package com.flowercards.domain.engine

import com.flowercards.domain.Fixtures.card
import com.flowercards.domain.Fixtures.cards
import com.flowercards.domain.Fixtures.piCards
import com.flowercards.domain.Fixtures.state
import com.flowercards.domain.deal.Dealer
import com.flowercards.domain.rule.RuleSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** rules §5(고/스톱)·§6(판 종료) — AC: "3점 이상에서만 고/스톱 선택지가 뜬다", "판 종료 3조건" */
class GoStopAndEndTest {

    /** 광 2장 보유 상태에서 3광을 완성해 3점 도달 */
    private fun stateAboutToReachThree() = state(
        p1Hand = cards("m08_pi1"),
        p1Captured = cards("m01_gwang", "m03_gwang"),
        p2Hand = cards("m05_pi1"),
        p2Captured = cards("m11_gwang") + piCards(6),
        floor = cards("m08_gwang", "m12_pi1"),
        pile = cards("m09_pi1", "m10_pi1"),
    )

    @Test
    fun `3점 도달 시 고-스톱 선택 단계로 전환된다`() {
        val (next, events) = TurnEngine.apply(
            stateAboutToReachThree(),
            PlayerAction.PlayCard(card("m08_pi1")),
        )
        assertEquals(GamePhase.AWAITING_GO_STOP, next.phase)
        assertTrue(events.any { it is GameEvent.GoStopChoice })
        assertEquals(PlayerId.P1, next.turn)
    }

    @Test
    fun `3점 미만이면 고-스톱 없이 턴이 넘어간다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi2"),
            pile = cards("m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertEquals(GamePhase.AWAITING_PLAY, next.phase)
        assertFalse(events.any { it is GameEvent.GoStopChoice })
        assertEquals(PlayerId.P2, next.turn)
    }

    @Test
    fun `고 선언 - 고 카운트가 오르고 턴이 상대에게 넘어간다`() {
        val (reached, _) = TurnEngine.apply(
            stateAboutToReachThree(),
            PlayerAction.PlayCard(card("m08_pi1")),
        )
        val (next, events) = TurnEngine.apply(reached, PlayerAction.DeclareGo)
        assertTrue(events.any { it is GameEvent.GoDeclared })
        assertEquals(1, next.player(PlayerId.P1).goCount)
        assertEquals(GamePhase.AWAITING_PLAY, next.phase)
        assertEquals(PlayerId.P2, next.turn)
    }

    @Test
    fun `스톱 선언 - 판이 종료되고 승자와 점수가 확정된다`() {
        val (reached, _) = TurnEngine.apply(
            stateAboutToReachThree(),
            PlayerAction.PlayCard(card("m08_pi1")),
        )
        val (next, events) = TurnEngine.apply(reached, PlayerAction.DeclareStop)
        assertEquals(GamePhase.FINISHED, next.phase)
        assertTrue(events.any { it is GameEvent.Stopped })
        val result = next.result
        assertTrue(result is GameResult.Win)
        assertEquals(PlayerId.P1, result.winner)
        assertEquals(3, result.score.total, "3광=3점, 박/배수 없음")
        assertFalse(result.score.goBak)
    }

    @Test
    fun `고박 - 고를 부른 상대에게 이기면 고박으로 기록된다`() {
        val base = stateAboutToReachThree()
        val withOpponentGo = base.copy(
            players = base.players + (
                PlayerId.P2 to base.player(PlayerId.P2).copy(goCount = 1)
                ),
        )
        val (reached, _) = TurnEngine.apply(
            withOpponentGo,
            PlayerAction.PlayCard(card("m08_pi1")),
        )
        val (next, _) = TurnEngine.apply(reached, PlayerAction.DeclareStop)
        val result = next.result as GameResult.Win
        assertTrue(result.score.goBak, "패자가 고를 불렀으면 고박 (rules §5)")
    }

    @Test
    fun `나가리 - 양쪽 손패가 소진되고 승자가 없으면 무승부다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = emptyList(),
            floor = cards("m03_pi1"),
            pile = cards("m04_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertEquals(GamePhase.FINISHED, next.phase)
        assertEquals(GameResult.Nagari, next.result)
        assertTrue(events.any { it is GameEvent.Nagari })
    }

    @Test
    fun `빈 손 턴 - FlipOnly로 더미만 뒤집는다`() {
        val s = state(
            p1Hand = emptyList(),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi2"),
            pile = cards("m01_pi1", "m09_pi1"),
        )
        val (next, _) = TurnEngine.apply(s, PlayerAction.FlipOnly)
        assertEquals(cards("m01_pi1", "m01_pi2").toSet(), next.player(PlayerId.P1).captured.toSet())
        assertEquals(PlayerId.P2, next.turn)
    }

    @Test
    fun `스모크 - 딜부터 종료까지 무작위 플레이로 카드 총합 48이 유지된다`() {
        repeat(50) { seed ->
            val random = Random(seed)
            var (state, _) = Dealer.deal(RuleSet(), random)
            var steps = 0
            while (state.phase != GamePhase.FINISHED) {
                check(steps++ < 200) { "seed=$seed 판이 끝나지 않는다" }
                val action = when (state.phase) {
                    GamePhase.AWAITING_GO_STOP ->
                        if (random.nextBoolean()) PlayerAction.DeclareGo else PlayerAction.DeclareStop
                    else -> {
                        val hand = state.currentPlayer.hand
                        if (hand.isEmpty()) {
                            PlayerAction.FlipOnly
                        } else {
                            PlayerAction.PlayCard(hand.random(random))
                        }
                    }
                }
                val (next, _) = TurnEngine.apply(state, action)
                assertEquals(48, next.totalCards(), "seed=$seed step=$steps")
                state = next
            }
        }
    }
}
