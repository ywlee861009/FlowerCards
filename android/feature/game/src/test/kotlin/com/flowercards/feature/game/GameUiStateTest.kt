package com.flowercards.feature.game

import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.model.Card
import com.flowercards.domain.model.CardKind
import com.flowercards.domain.model.Month
import com.flowercards.domain.rule.RuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameState.toUiState 순수 투영 로직 검증.
 * - 좌석 고정(my, opp 계열)은 me 파라미터 기준, turn과 무관.
 * - active 계열은 turn(현재 턴 플레이어) 기준, me와 무관.
 */
class GameUiStateTest {

    // ---------------------------------------------------------------- 좌석 고정 vs turn 투영

    @Test
    fun `my-opp 투영은 turn과 무관하게 me 좌석 고정`() {
        val p1Hand = TestFixtures.cards("m01_gwang")
        val p2Hand = TestFixtures.cards("m02_yeol", "m03_gwang")
        val floor = TestFixtures.cards("m04_yeol")
        val pile = TestFixtures.cards("m05_yeol", "m06_yeol")

        val stateTurnP2 = TestFixtures.state(
            turn = PlayerId.P2,
            p1Hand = p1Hand,
            p2Hand = p2Hand,
            floor = floor,
            pile = pile,
        )
        val uiTurnP2 = stateTurnP2.toUiState(PlayerId.P1)
        assertEquals(p1Hand, uiTurnP2.myHand)
        assertEquals(p2Hand.size, uiTurnP2.oppHandCount)
        assertEquals(floor, uiTurnP2.floor)
        assertEquals(pile.size, uiTurnP2.pileCount)

        val stateTurnP1 = stateTurnP2.copy(turn = PlayerId.P1)
        val uiTurnP1 = stateTurnP1.toUiState(PlayerId.P1)
        // turn이 바뀌어도 좌석 기준 my/opp 투영은 동일해야 한다.
        assertEquals(p1Hand, uiTurnP1.myHand)
        assertEquals(p2Hand.size, uiTurnP1.oppHandCount)
    }

    @Test
    fun `active 투영은 turn 플레이어 기준, me와 무관`() {
        val p1Hand = TestFixtures.cards("m01_gwang")
        val p2Hand = TestFixtures.cards("m02_yeol", "m03_gwang")
        val base = TestFixtures.state(p1Hand = p1Hand, p2Hand = p2Hand)

        val uiWhenP1Turn = base.copy(turn = PlayerId.P1).toUiState(PlayerId.P1)
        assertEquals(PlayerId.P1, uiWhenP1Turn.activePlayer)
        assertEquals(p1Hand, uiWhenP1Turn.activeHand)

        val uiWhenP2Turn = base.copy(turn = PlayerId.P2).toUiState(PlayerId.P1)
        assertEquals(PlayerId.P2, uiWhenP2Turn.activePlayer)
        assertEquals(p2Hand, uiWhenP2Turn.activeHand)
    }

    @Test
    fun `me=P2로 투영해도 my-opp가 올바르게 스왑된다`() {
        // 계약상 ViewModel은 항상 P1을 넘기지만, 함수 자체의 일반성(회귀 방지)을 검증한다.
        val p1Hand = TestFixtures.cards("m01_gwang")
        val p2Hand = TestFixtures.cards("m02_yeol")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = p1Hand, p2Hand = p2Hand)

        val ui = state.toUiState(PlayerId.P2)
        assertEquals(p2Hand, ui.myHand)
        assertEquals(p1Hand.size, ui.oppHandCount)
        // active는 turn(P1) 기준이므로 me=P2와 무관하게 P1 손패다.
        assertEquals(PlayerId.P1, ui.activePlayer)
        assertEquals(p1Hand, ui.activeHand)
    }

    // ---------------------------------------------------------------- 흔들기/폭탄 가능 월

    @Test
    fun `흔들기 가능 월은 손패 같은 월 3장 이상`() {
        val hand = TestFixtures.cards("m01_gwang", "m01_tti", "m01_pi1", "m02_yeol")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = hand)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(setOf(Month.JANUARY), ui.activeShakeableMonths)
    }

    @Test
    fun `같은 월 2장은 흔들기 대상이 아니다`() {
        val hand = TestFixtures.cards("m01_gwang", "m01_tti", "m02_yeol")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = hand)

        val ui = state.toUiState(PlayerId.P1)
        assertTrue(ui.activeShakeableMonths.isEmpty())
    }

    @Test
    fun `폭탄 가능 월은 손패 3장 + 바닥 정확히 1장`() {
        val hand = TestFixtures.cards("m11_gwang", "m11_pi1", "m11_pi2")
        val floor = TestFixtures.cards("m11_pi3")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = hand, floor = floor)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(setOf(Month.NOVEMBER), ui.activeBombableMonths)
        // 폭탄 가능 월은 흔들기 조건도 동시에 만족한다(같은 월 3장 이상).
        assertEquals(setOf(Month.NOVEMBER), ui.activeShakeableMonths)
    }

    @Test
    fun `바닥에 해당 월이 없으면 손패 3장이어도 폭탄이 아니다`() {
        val hand = TestFixtures.cards("m11_gwang", "m11_pi1", "m11_pi2")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = hand, floor = emptyList())

        val ui = state.toUiState(PlayerId.P1)
        assertTrue(ui.activeBombableMonths.isEmpty())
    }

    @Test
    fun `바닥에 해당 월이 2장이면 폭탄이 아니다(따닥 상황과 구분)`() {
        // 표준 덱은 한 월 최대 4장이라 손패3+바닥2(=5장)는 실제 대국에서 나올 수 없다.
        // 폭탄 판정식 자체(count==3 && floorCount==1)의 경계값을 검증하기 위해
        // 합성 카드로 floorCount=2 상황만 인위적으로 구성한다.
        val hand = TestFixtures.cards("m11_gwang", "m11_pi1", "m11_pi2")
        val syntheticExtra = Card(id = "m11_pi_synthetic", month = Month.NOVEMBER, kind = CardKind.PI)
        val floor = TestFixtures.cards("m11_pi3") + syntheticExtra
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = hand, floor = floor)

        val ui = state.toUiState(PlayerId.P1)
        assertTrue(
            "바닥 2장(floorMonthCounts==2)에서는 폭탄 조건(==1)을 만족하지 않아야 한다",
            ui.activeBombableMonths.isEmpty(),
        )
    }

    // ---------------------------------------------------------------- 고 가능 여부(activeCanGo)

    @Test
    fun `activeScore가 stopThreshold 이상이면 canGo`() {
        // 홍단 3장(1,2,3월 띠) = 3점, 기본 stopThreshold=3
        val captured = TestFixtures.cards("m01_tti", "m02_tti", "m03_tti")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Captured = captured)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(3, ui.activeScore)
        assertTrue(ui.activeCanGo)
    }

    @Test
    fun `activeScore가 stopThreshold 미만이면 canGo 아님(경계값 -1)`() {
        // 3광(비광 포함) = 2점 < stopThreshold(3)
        val captured = TestFixtures.cards("m01_gwang", "m03_gwang", "m12_gwang")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Captured = captured)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(2, ui.activeScore)
        assertFalse(ui.activeCanGo)
    }

    @Test
    fun `stopThreshold를 커스텀 RuleSet으로 바꾸면 경계가 따라간다`() {
        val captured = TestFixtures.cards("m01_tti", "m02_tti", "m03_tti") // 3점
        val ruleSet = RuleSet(stopThreshold = 4)
        val state = TestFixtures.state(turn = PlayerId.P1, p1Captured = captured, ruleSet = ruleSet)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(3, ui.activeScore)
        assertFalse(ui.activeCanGo) // 3 < 4
    }

    // ---------------------------------------------------------------- 획득패 카테고리 분류

    @Test
    fun `myCapturedCards와 myCaptured 요약이 카테고리별로 정확히 분류된다`() {
        val captured = TestFixtures.cards("m01_gwang", "m02_yeol", "m01_tti", "m01_pi1")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Captured = captured)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(TestFixtures.cards("m01_gwang"), ui.myCapturedCards.gwang)
        assertEquals(TestFixtures.cards("m02_yeol"), ui.myCapturedCards.yeol)
        assertEquals(TestFixtures.cards("m01_tti"), ui.myCapturedCards.tti)
        assertEquals(TestFixtures.cards("m01_pi1"), ui.myCapturedCards.pi)

        assertEquals(1, ui.myCaptured.gwang)
        assertEquals(1, ui.myCaptured.yeol)
        assertEquals(1, ui.myCaptured.tti)
        assertEquals(1, ui.myCaptured.pi)
        assertEquals(4, ui.myCaptured.total)
    }

    @Test
    fun `oppCapturedCards도 좌석 기준(opp=me의 반대)으로 분류된다`() {
        val p2Captured = TestFixtures.cards("m03_gwang", "m08_yeol")
        val state = TestFixtures.state(turn = PlayerId.P1, p2Captured = p2Captured)

        val ui = state.toUiState(PlayerId.P1)
        assertEquals(TestFixtures.cards("m03_gwang"), ui.oppCapturedCards.gwang)
        assertEquals(TestFixtures.cards("m08_yeol"), ui.oppCapturedCards.yeol)
        assertEquals(2, ui.oppCaptured.total)
    }

    // ---------------------------------------------------------------- floorGroups(월/뻑 그룹)

    @Test
    fun `floorGroups는 월 단위로 묶고 ppeokMonths에 있는 월만 isPpeok=true`() {
        val floor = TestFixtures.cards("m01_gwang", "m01_tti", "m02_yeol")
        val state = TestFixtures.state(
            turn = PlayerId.P1,
            floor = floor,
            ppeokMonths = mapOf(Month.JANUARY to PlayerId.P1),
        )

        val ui = state.toUiState(PlayerId.P1)
        val byMonth = ui.floorGroups.associateBy { it.month }

        assertEquals(2, byMonth.getValue(Month.JANUARY).cards.size)
        assertTrue(byMonth.getValue(Month.JANUARY).isPpeok)
        assertEquals(1, byMonth.getValue(Month.FEBRUARY).cards.size)
        assertFalse(byMonth.getValue(Month.FEBRUARY).isPpeok)
    }

    @Test
    fun `floorGroups는 바닥이 비어있으면 빈 리스트`() {
        val state = TestFixtures.state(turn = PlayerId.P1, floor = emptyList())
        val ui = state.toUiState(PlayerId.P1)
        assertTrue(ui.floorGroups.isEmpty())
    }

    // ---------------------------------------------------------------- canPlay

    @Test
    fun `canPlay는 AWAITING_PLAY이고 activeHand가 있을 때만 true`() {
        val hand = TestFixtures.cards("m01_gwang")
        val state = TestFixtures.state(turn = PlayerId.P1, p1Hand = hand, phase = GamePhase.AWAITING_PLAY)
        assertTrue(state.toUiState(PlayerId.P1).canPlay)

        val finished = state.copy(phase = GamePhase.FINISHED)
        assertFalse(finished.toUiState(PlayerId.P1).canPlay)

        val emptyHandState = TestFixtures.state(turn = PlayerId.P1, p1Hand = emptyList())
        assertFalse(emptyHandState.toUiState(PlayerId.P1).canPlay)
    }
}
