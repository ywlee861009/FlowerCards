package com.flowercards.domain.deal

import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameResult
import com.flowercards.domain.rule.RuleSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** rules §3 초기 배치 — AC: "딜 후 손패10/손패10/바닥8/더미20이 항상 성립한다" */
class DealerTest {

    @Test
    fun `딜 배치는 항상 10-10-8-20이고 총 48장이다`() {
        repeat(500) { seed ->
            val (state, _) = Dealer.deal(random = Random(seed))
            if (state.phase == GamePhase.FINISHED) return@repeat // 총통 즉시 종료 판은 별도 검증
            val hands = state.players.values.map { it.hand.size }
            assertEquals(listOf(10, 10), hands)
            assertEquals(8, state.floor.size)
            assertEquals(20, state.pile.size)
            assertEquals(48, state.totalCards())
        }
    }

    @Test
    fun `리딜 규칙 - 바닥에 같은 월 4장이 절대 없다`() {
        repeat(2000) { seed ->
            val (state, _) = Dealer.deal(random = Random(seed))
            val floorByMonth = state.floor.groupingBy { it.month }.eachCount()
            assertTrue(floorByMonth.values.all { it < 4 }, "seed=$seed 바닥 4장 발생")
        }
    }

    @Test
    fun `총통 - 손패에 같은 월 4장이면 즉시 종료된다`() {
        // 확률적 상황이므로 시드를 탐색해 실제 총통 딜을 찾아 검증한다
        for (seed in 0..20000) {
            val (state, events) = Dealer.deal(random = Random(seed))
            val chongtongPlayer = state.players.entries.firstOrNull { (_, p) ->
                p.hand.groupingBy { it.month }.eachCount().any { it.value == 4 }
            }?.key
            if (chongtongPlayer != null) {
                assertEquals(GamePhase.FINISHED, state.phase, "seed=$seed")
                val result = state.result
                assertTrue(result is GameResult.ChongtongWin, "seed=$seed")
                assertEquals(chongtongPlayer, result.winner)
                assertEquals(RuleSet().chongtongScore, result.score)
                assertTrue(events.isNotEmpty())
                return
            }
        }
        fail("탐색 범위에서 총통 시드를 찾지 못했다 — 탐색 범위를 늘릴 것")
    }

    @Test
    fun `총통 미적용 룰이면 같은 월 4장 손패여도 판이 계속된다`() {
        val ruleSet = RuleSet(chongtongEnabled = false)
        for (seed in 0..20000) {
            val (state, _) = Dealer.deal(ruleSet, Random(seed))
            val hasFour = state.players.values.any { p ->
                p.hand.groupingBy { it.month }.eachCount().any { it.value == 4 }
            }
            if (hasFour) {
                assertEquals(GamePhase.AWAITING_PLAY, state.phase, "seed=$seed")
                return
            }
        }
        fail("탐색 범위에서 총통 시드를 찾지 못했다")
    }
}
