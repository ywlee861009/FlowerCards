package com.flowercards.domain.engine

import com.flowercards.domain.Fixtures.card
import com.flowercards.domain.Fixtures.cards
import com.flowercards.domain.Fixtures.state
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** rules §4.1 매칭 — AC: "같은 월 매칭이 정확하다 (0/1/2/3장 바닥 상황 각각 처리)" */
class MatchingTest {

    @Test
    fun `바닥 0장 - 낸 카드는 바닥에 남는다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m03_pi1"),
            pile = cards("m09_pi1"),
        )
        val (next, _) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        assertTrue(card("m01_pi1") in next.floor)
        assertTrue(next.player(PlayerId.P1).captured.isEmpty())
    }

    @Test
    fun `바닥 1장 - 두 장을 획득한다`() {
        val s = state(
            p1Hand = cards("m01_pi1"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi2", "m03_pi1"),
            pile = cards("m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_pi1")))
        val captured = next.player(PlayerId.P1).captured
        assertEquals(cards("m01_pi1", "m01_pi2").toSet(), captured.toSet())
        assertTrue(events.any { it is GameEvent.Captured })
    }

    @Test
    fun `바닥 2장 - floorChoice로 선택한 카드를 가져오고 나머지는 남는다`() {
        val s = state(
            p1Hand = cards("m01_gwang"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi1", "m01_tti", "m03_pi1"),
            pile = cards("m09_pi1"),
        )
        val (next, _) = TurnEngine.apply(
            s,
            PlayerAction.PlayCard(card("m01_gwang"), floorChoice = card("m01_pi1")),
        )
        val captured = next.player(PlayerId.P1).captured
        assertEquals(cards("m01_gwang", "m01_pi1").toSet(), captured.toSet())
        assertTrue(card("m01_tti") in next.floor, "선택하지 않은 카드는 바닥에 남는다")
    }

    @Test
    fun `바닥 2장 - 선택이 없으면 종류 우선순위(광-열끗-띠-피)로 자동 선택한다`() {
        val s = state(
            p1Hand = cards("m01_gwang"),
            p2Hand = cards("m05_pi1"),
            floor = cards("m01_pi1", "m01_tti"),
            pile = cards("m09_pi1"),
        )
        val (next, _) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_gwang")))
        assertTrue(card("m01_tti") in next.player(PlayerId.P1).captured, "띠가 피보다 우선")
    }

    @Test
    fun `바닥 3장(따조) - 4번째 카드로 4장을 모두 획득하고 피를 뺏는다`() {
        val s = state(
            p1Hand = cards("m01_gwang"),
            p2Hand = cards("m05_pi1"),
            p2Captured = cards("m10_pi1"),
            floor = cards("m01_pi1", "m01_pi2", "m01_tti", "m03_pi1"),
            pile = cards("m09_pi1"),
        )
        val (next, events) = TurnEngine.apply(s, PlayerAction.PlayCard(card("m01_gwang")))
        val captured = next.player(PlayerId.P1).captured
        assertTrue(cards("m01_gwang", "m01_pi1", "m01_pi2", "m01_tti").all { it in captured })
        assertTrue(events.any { it is GameEvent.Ttajo }, "따조 이벤트 (rules §4.4)")
        assertTrue(card("m10_pi1") in captured, "따조 피 뺏기")
        assertTrue(events.any { it is GameEvent.PiStolen })
    }
}
