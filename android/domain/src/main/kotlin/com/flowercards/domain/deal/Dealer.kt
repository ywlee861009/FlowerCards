package com.flowercards.domain.deal

import com.flowercards.domain.engine.GameEvent
import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameResult
import com.flowercards.domain.engine.GameState
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.engine.PlayerState
import com.flowercards.domain.model.Deck
import com.flowercards.domain.rule.RuleSet
import kotlin.random.Random

/**
 * 초기 배치 (rules §3): 손패 10 / 손패 10 / 바닥 8 / 더미 20.
 * - 선(先): 랜덤 (확정 룰 #2)
 * - 바닥에 같은 월 4장 → 리딜 (확정 룰 #4)
 * - 총통(손패 같은 월 4장) → 즉시 종료 (확정 룰 #3)
 */
object Dealer {

    data class DealOutcome(val state: GameState, val events: List<GameEvent>)

    fun deal(ruleSet: RuleSet = RuleSet(), random: Random = Random.Default): DealOutcome {
        val firstPlayer = if (random.nextBoolean()) PlayerId.P1 else PlayerId.P2

        var shuffled = Deck.standard().shuffled(random)
        if (ruleSet.redealOnFloorFourOfMonth) {
            while (hasFourOfMonth(floorOf(shuffled))) {
                shuffled = Deck.standard().shuffled(random)
            }
        }

        // subList 뷰가 원본 배열을 물고 있지 않도록 방어 복사한다
        val firstHand = shuffled.subList(0, 10).toList()
        val secondHand = shuffled.subList(10, 20).toList()
        val floor = floorOf(shuffled).toList()
        val pile = shuffled.subList(28, 48).toList()

        val state = GameState(
            ruleSet = ruleSet,
            turn = firstPlayer,
            players = mapOf(
                firstPlayer to PlayerState(hand = firstHand),
                firstPlayer.opponent to PlayerState(hand = secondHand),
            ),
            floor = floor,
            pile = pile,
        )
        check(state.totalCards() == 48) { "딜 결과가 48장이 아니다: ${state.totalCards()}" }

        if (ruleSet.chongtongEnabled) {
            for (playerId in listOf(firstPlayer, firstPlayer.opponent)) {
                val chongtongMonth = state.player(playerId).hand
                    .groupingBy { it.month }.eachCount()
                    .entries.firstOrNull { it.value == 4 }?.key
                if (chongtongMonth != null) {
                    val result = GameResult.ChongtongWin(playerId, chongtongMonth, ruleSet.chongtongScore)
                    return DealOutcome(
                        state = state.copy(phase = GamePhase.FINISHED, result = result),
                        events = listOf(
                            GameEvent.Chongtong(playerId, chongtongMonth),
                            GameEvent.GameEnded(result),
                        ),
                    )
                }
            }
        }

        return DealOutcome(state, emptyList())
    }

    private fun floorOf(shuffled: List<com.flowercards.domain.model.Card>) = shuffled.subList(20, 28)

    private fun hasFourOfMonth(cards: List<com.flowercards.domain.model.Card>): Boolean =
        cards.groupingBy { it.month }.eachCount().any { it.value == 4 }
}
