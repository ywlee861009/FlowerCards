package com.flowercards.domain.engine

import com.flowercards.domain.Fixtures.card
import com.flowercards.domain.Fixtures.cards
import com.flowercards.domain.Fixtures.state
import com.flowercards.domain.model.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** rules §4.2~§4.7 — AC: "뻑/자뻑/따닥/따조/쪽/싹쓸이/폭탄이 정의된 조건에서만 발생한다" */
class SpecialSituationTest {

    // ------------------------------------------------------------- 뻑 (rules §4.2)

    @Test
    fun `뻑 - 낸 카드+바닥+뒤집은 카드가 같은 월 3장이면 아무도 못 먹는다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi2", "m03_pi1"),
            pile = cards("m01_tti", "m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertTrue(events.any { it is GameEvent.Ppeok })
        assertTrue(next.player(PlayerId.P1).captured.isEmpty(), "뻑이면 획득 실패")
        assertTrue(cards("m01_pi1", "m01_pi2", "m01_tti").all { it in next.floor }, "3장이 바닥에 묶인다")
        assertEquals(PlayerId.P1, next.ppeokMonths[Month.JANUARY], "뻑 유발자 기록")
    }

    @Test
    fun `자뻑 - 뻑 유발자가 4번째 카드로 4장을 먹으면 피를 뺏는다`() {
        val s = state(
            p1Hand = cards("m01_gwang"),
            p2Hand = cards("m05_pi1"),
            p2Captured = cards("m10_pi1", "m10_pi2"),
            floor = cards("m01_pi1", "m01_pi2", "m01_tti"),
            pile = cards("m09_pi1"),
            ppeokMonths = mapOf(Month.JANUARY to PlayerId.P1),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_gwang")))
        assertTrue(events.any { it is GameEvent.JaPpeok })
        assertFalse(events.any { it is GameEvent.Ttajo }, "자뻑은 따조와 별개")
        assertTrue(events.any { it is GameEvent.PiStolen }, "자뻑 피 뺏기 1장 (확정 룰 #5)")
        assertTrue(next.ppeokMonths.isEmpty(), "뻑 해소")
    }

    @Test
    fun `뻑 더미를 상대가 먹으면 보너스 없이 4장만 가져간다`() {
        val s = state(
            turn = PlayerId.P2,
            p1Hand = cards("m05_pi1"),
            p1Captured = cards("m10_pi1"),
            p2Hand = cards("m01_gwang"),
            floor = cards("m01_pi1", "m01_pi2", "m01_tti"),
            pile = cards("m09_pi1"),
            ppeokMonths = mapOf(Month.JANUARY to PlayerId.P1),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_gwang")))
        assertEquals(4, next.player(PlayerId.P2).captured.size)
        assertFalse(events.any { it is GameEvent.JaPpeok })
        assertFalse(events.any { it is GameEvent.Ttajo })
        assertFalse(events.any { it is GameEvent.PiStolen }, "남의 뻑 먹기는 피 뺏기 없음 (rules §4.2)")
    }

    // ------------------------------------------------------------- 쪽 (rules §4.5)

    @Test
    fun `쪽 - 낸 카드를 뒤집은 카드가 잡으면 2장 획득하고 피를 뺏는다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            p2Captured = cards("m10_pi1"),
            floor = cards("m03_pi1"),
            pile = cards("m01_pi2", "m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertTrue(events.any { it is GameEvent.Jjok })
        val captured = next.player(PlayerId.P1).captured
        assertTrue(cards("m01_pi1", "m01_pi2").all { it in captured })
        assertTrue(card("m10_pi1") in captured, "쪽 피 뺏기")
    }

    // ------------------------------------------------------------- 따닥 (rules §4.4)

    @Test
    fun `따닥 - 낸 카드와 뒤집은 카드로 다른 월을 각각 먹으면 피를 뺏는다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            p2Captured = cards("m10_pi1"),
            floor = cards("m01_pi2", "m02_pi1", "m12_pi1"),
            pile = cards("m02_pi2", "m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertTrue(events.any { it is GameEvent.Ttadak })
        assertEquals(5, next.player(PlayerId.P1).captured.size, "2+2 획득 + 뺏은 피 1")
    }

    @Test
    fun `한 쪽만 먹으면 따닥이 아니다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi2", "m12_pi1"),
            pile = cards("m09_pi1"),
        )
        val (_, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertFalse(events.any { it is GameEvent.Ttadak })
    }

    // ------------------------------------------------------------- 싹쓸이 (rules §4.6)

    @Test
    fun `싹쓸이 - 바닥을 전부 쓸면 피를 뺏는다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            p2Captured = cards("m10_pi1"),
            floor = cards("m01_pi2", "m02_pi1"),
            pile = cards("m02_pi2", "m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertTrue(events.any { it is GameEvent.Sweep })
        assertTrue(next.floor.isEmpty())
    }

    @Test
    fun `뒤집은 카드가 바닥에 남으면 싹쓸이가 아니다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi2"),
            pile = cards("m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertFalse(events.any { it is GameEvent.Sweep })
        assertEquals(cards("m09_pi1"), next.floor)
    }

    // ------------------------------------------------------------- 흔들기 / 폭탄 (rules §4.7)

    @Test
    fun `흔들기 - 같은 월 3장 보유 시 선언하면 배수 카운트가 오른다`() {
        val s = state(
            p1Hand = cards("m01_pi1", "m01_pi2", "m01_gwang", "m05_pi1"),
            p2Hand = cards("m06_pi1"),
            floor = cards("m03_pi1"),
            pile = cards("m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(
            s,
            PlayerAction.PlayCard(card("m01_pi1"), declareShake = true),
        )
        assertTrue(events.any { it is GameEvent.Shake })
        assertEquals(1, next.player(PlayerId.P1).shakeCount)
    }

    @Test
    fun `흔들기 - 같은 월 3장이 없으면 선언할 수 없다`() {
        val s = state(
            p1Hand = cards("m01_pi1", "m01_pi2", "m05_pi1"),
            p2Hand = cards("m06_pi1"),
            floor = cards("m03_pi1"),
            pile = cards("m09_pi1"),
        )
        assertFailsWith<IllegalArgumentException> {
            TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1"), declareShake = true))
        }
    }

    @Test
    fun `폭탄 - 손패 3장+바닥 1장으로 4장을 먹고 피를 뺏고 추가 턴을 얻는다`() {
        val s = state(
            p1Hand = cards("m01_pi1", "m01_pi2", "m01_tti", "m05_pi1"),
            p2Hand = cards("m06_pi1"),
            p2Captured = cards("m10_pi1"),
            floor = cards("m01_gwang", "m03_pi1"),
            pile = cards("m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayBomb(Month.JANUARY))
        assertTrue(events.any { it is GameEvent.Bomb })
        val captured = next.player(PlayerId.P1).captured
        assertTrue(cards("m01_pi1", "m01_pi2", "m01_tti", "m01_gwang").all { it in captured })
        assertTrue(events.any { it is GameEvent.PiStolen }, "폭탄 피 뺏기")
        assertFalse(events.any { it is GameEvent.Ttajo }, "폭탄은 따조가 아니다")
        assertEquals(1, next.player(PlayerId.P1).bombCount)
        assertEquals(PlayerId.P1, next.turn, "폭탄 추가 턴 (확정 룰 #6)")
    }

    @Test
    fun `폭탄 - 조건 미충족이면 불가`() {
        val s = state(
            p1Hand = cards("m01_pi1", "m01_pi2", "m05_pi1"),
            p2Hand = cards("m06_pi1"),
            floor = cards("m01_gwang"),
            pile = cards("m09_pi1"),
        )
        assertFailsWith<IllegalArgumentException> {
            TurnEngine.apply(s, PlayerAction.PlayBomb(Month.JANUARY))
        }
    }
}
