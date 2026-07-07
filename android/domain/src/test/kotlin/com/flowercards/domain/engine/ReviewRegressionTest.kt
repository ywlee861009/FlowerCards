package com.flowercards.domain.engine

import com.flowercards.domain.Fixtures.card
import com.flowercards.domain.Fixtures.cards
import com.flowercards.domain.Fixtures.piCards
import com.flowercards.domain.Fixtures.state
import com.flowercards.domain.model.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 코드 리뷰에서 발견된 결함/룰 해석의 회귀 테스트 */
class ReviewRegressionTest {

    @Test
    fun `폭탄으로 3점 도달 후 고를 선언해도 추가 턴이 유지된다`() {
        // P1: 광 2장 보유, 8월 폭탄으로 m08_gwang을 먹어 3광 완성 → 고/스톱 → 고
        val s = state(
            p1Hand = cards("m08_yeol", "m08_pi1", "m08_pi2", "m05_pi1"),
            p1Captured = cards("m01_gwang", "m03_gwang"),
            p2Hand = cards("m06_pi1"),
            p2Captured = cards("m11_gwang", "m11_pi1", "m11_pi2", "m11_pi3", "m12_pi1", "m07_pi1", "m07_pi2"),
            floor = cards("m08_gwang", "m03_pi1"),
            pile = cards("m09_pi1", "m10_pi1"),
        )
        val (afterBomb, events) = TurnEngine.apply(s, PlayerAction.PlayBomb(Month.AUGUST))
        assertEquals(GamePhase.AWAITING_GO_STOP, afterBomb.phase)
        assertTrue(events.any { it is GameEvent.GoStopChoice })
        assertTrue(afterBomb.pendingExtraTurn, "추가 턴이 상태에 보류된다")

        val (afterGo, _) = TurnEngine.apply(afterBomb, PlayerAction.DeclareGo)
        assertEquals(PlayerId.P1, afterGo.turn, "폭탄 추가 턴 (확정 룰 #6)")
        assertEquals(GamePhase.AWAITING_PLAY, afterGo.phase)
        assertFalse(afterGo.pendingExtraTurn, "소비 후 해제")
    }

    @Test
    fun `흔들기-폭탄 배수는 판 전체에 적용된다 - 패자가 흔들었어도 승자 점수 2배`() {
        // rules §4.7 "해당 판 점수 ×2" — 흔든 사람이 지면 두 배로 물어준다는 통상 룰
        val base = state(
            p1Hand = cards("m08_pi1"),
            p1Captured = cards("m01_gwang", "m03_gwang"),
            p2Hand = cards("m05_pi1"),
            p2Captured = cards("m11_gwang") + piCards(6),
            floor = cards("m08_gwang", "m12_pi1"),
            pile = cards("m09_pi1", "m10_pi1"),
        )
        val withLoserShake = base.copy(
            players = base.players + (
                PlayerId.P2 to base.player(PlayerId.P2).copy(shakeCount = 1)
                ),
        )
        val (reached, _) = TurnEngine.apply(withLoserShake, PlayerAction.PlayCard(card("m08_pi1")))
        val (finished, _) = TurnEngine.apply(reached, PlayerAction.DeclareStop)
        val result = finished.result as GameResult.Win
        assertEquals(2, result.score.shakeBombMultiplier)
        assertEquals(6, result.score.total, "3광 3점 × 패자 흔들기 2배")
    }

    @Test
    fun `자기 뻑이 더미 뒤집기로 완성되면 자뻑 보너스 없이 일반 획득이다`() {
        // rules §4.3 "4번째 카드를 본인이 내서" — flip 완성은 보너스 제외
        val s = state(
            p1Hand = cards("m05_pi1"),
            p2Hand = cards("m06_pi1"),
            p2Captured = cards("m10_pi1"),
            floor = cards("m01_pi1", "m01_pi2", "m01_tti", "m05_pi2", "m12_pi1"),
            pile = cards("m01_gwang", "m09_pi1"),
            ppeokMonths = mapOf(Month.JANUARY to PlayerId.P1),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m05_pi1")))
        val captured = next.player(PlayerId.P1).captured
        assertTrue(cards("m01_gwang", "m01_pi1", "m01_pi2", "m01_tti").all { it in captured })
        assertFalse(events.any { it is GameEvent.JaPpeok }, "flip 완성은 자뻑 아님")
        assertFalse(events.any { it is GameEvent.PiStolen })
        assertTrue(next.ppeokMonths.isEmpty(), "뻑은 해소된다")
    }
}
