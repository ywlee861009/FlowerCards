package com.flowercards.domain.score

import com.flowercards.domain.Fixtures.cards
import com.flowercards.domain.Fixtures.piCards
import com.flowercards.domain.rule.GoBonusMode
import com.flowercards.domain.rule.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** scoring.md — AC: 광/임계값/조합/박/고/배수/예시 4종 */
class ScoreCalculatorTest {

    // ------------------------------------------------------------- §1.1 광

    @Test
    fun `광 - 2장 이하는 0점`() {
        assertEquals(0, ScoreCalculator.baseScore(cards("m01_gwang", "m03_gwang")).gwang)
    }

    @Test
    fun `광 - 비광 없는 3광은 3점`() {
        assertEquals(3, ScoreCalculator.baseScore(cards("m01_gwang", "m03_gwang", "m08_gwang")).gwang)
    }

    @Test
    fun `광 - 비광 낀 3광은 2점`() {
        assertEquals(2, ScoreCalculator.baseScore(cards("m01_gwang", "m03_gwang", "m12_gwang")).gwang)
    }

    @Test
    fun `광 - 4광은 4점, 5광은 15점`() {
        assertEquals(4, ScoreCalculator.baseScore(cards("m01_gwang", "m03_gwang", "m08_gwang", "m11_gwang")).gwang)
        assertEquals(
            15,
            ScoreCalculator.baseScore(
                cards("m01_gwang", "m03_gwang", "m08_gwang", "m11_gwang", "m12_gwang"),
            ).gwang,
        )
    }

    // ------------------------------------------------- §1.2~1.4 임계값 (N장부터 1점)

    @Test
    fun `열끗 - 4장 0점, 5장 1점, 6장 2점`() {
        assertEquals(0, ScoreCalculator.baseScore(cards("m04_yeol", "m05_yeol", "m06_yeol", "m07_yeol")).yeol)
        assertEquals(1, ScoreCalculator.baseScore(cards("m04_yeol", "m05_yeol", "m06_yeol", "m07_yeol", "m09_yeol")).yeol)
        assertEquals(
            2,
            ScoreCalculator.baseScore(cards("m04_yeol", "m05_yeol", "m06_yeol", "m07_yeol", "m09_yeol", "m12_yeol")).yeol,
        )
    }

    @Test
    fun `띠 - 4장 0점, 5장 1점`() {
        assertEquals(0, ScoreCalculator.baseScore(cards("m01_tti", "m02_tti", "m04_tti", "m06_tti")).tti)
        assertEquals(1, ScoreCalculator.baseScore(cards("m01_tti", "m02_tti", "m04_tti", "m06_tti", "m12_tti")).tti)
    }

    @Test
    fun `피 - 9장 0점, 10장 1점, 11장 2점`() {
        assertEquals(0, ScoreCalculator.baseScore(piCards(9)).pi)
        assertEquals(1, ScoreCalculator.baseScore(piCards(10)).pi)
        assertEquals(2, ScoreCalculator.baseScore(piCards(11)).pi)
    }

    // ------------------------------------------------------------- §2 조합

    @Test
    fun `고도리 - 2·8·10월 열끗 3장이면 5점`() {
        val breakdown = ScoreCalculator.baseScore(cards("m02_yeol", "m08_yeol", "m10_yeol"))
        assertEquals(5, breakdown.godori)
        assertEquals(0, breakdown.yeol, "열끗 3장은 장수 점수 0")
    }

    @Test
    fun `홍단·청단·초단 - 각 3점이며 별도 합산된다`() {
        assertEquals(3, ScoreCalculator.baseScore(cards("m01_tti", "m02_tti", "m03_tti")).hongDan)
        assertEquals(3, ScoreCalculator.baseScore(cards("m06_tti", "m09_tti", "m10_tti")).cheongDan)
        assertEquals(3, ScoreCalculator.baseScore(cards("m04_tti", "m05_tti", "m07_tti")).choDan)
        // 홍단 + 초단 동시 보유 → 3+3, 띠 6장 → +2
        val both = ScoreCalculator.baseScore(
            cards("m01_tti", "m02_tti", "m03_tti", "m04_tti", "m05_tti", "m07_tti"),
        )
        assertEquals(3, both.hongDan)
        assertEquals(3, both.choDan)
        assertEquals(2, both.tti)
        assertEquals(8, both.total)
    }

    // ------------------------------------------------------------- §3 고 가산/배수

    @Test
    fun `고 방식(기본안) - 3점 기준 1고 4, 2고 5, 3고 10, 4고 20`() {
        val mode = GoBonusMode.PLUS_ONE_THEN_DOUBLE
        assertEquals(3, ScoreCalculator.applyGoBonus(3, 0, mode))
        assertEquals(4, ScoreCalculator.applyGoBonus(3, 1, mode))
        assertEquals(5, ScoreCalculator.applyGoBonus(3, 2, mode))
        assertEquals(10, ScoreCalculator.applyGoBonus(3, 3, mode))
        assertEquals(20, ScoreCalculator.applyGoBonus(3, 4, mode))
    }

    // ------------------------------------------------------------- §2 박 발동

    @Test
    fun `피박 - 상대 피 5장 이하일 때만 2배`() {
        val winner = cards("m01_gwang", "m03_gwang", "m08_gwang") // 3점
        val final5 = ScoreCalculator.finalScore(winner, piCards(5) + cards("m11_gwang"), 0, 0, 0, 0)
        assertTrue(final5.piBak)
        assertEquals(6, final5.total)
        val final6 = ScoreCalculator.finalScore(winner, piCards(6) + cards("m11_gwang"), 0, 0, 0, 0)
        assertFalse(final6.piBak)
        assertEquals(3, final6.total)
    }

    @Test
    fun `광박 - 광 3점 이상으로 이기고 상대 광이 0장일 때만 2배`() {
        val threeGwang = cards("m01_gwang", "m03_gwang", "m08_gwang") // 광 3점
        val biGwang3 = cards("m01_gwang", "m03_gwang", "m12_gwang") // 비광 3광 = 2점
        val loserNoGwang = piCards(6)
        val loserWithGwang = piCards(6) + cards("m11_gwang")

        assertTrue(ScoreCalculator.finalScore(threeGwang, loserNoGwang, 0, 0, 0, 0).gwangBak)
        assertFalse(ScoreCalculator.finalScore(threeGwang, loserWithGwang, 0, 0, 0, 0).gwangBak)
        assertFalse(
            ScoreCalculator.finalScore(biGwang3, loserNoGwang, 0, 0, 0, 0).gwangBak,
            "비광 3광(2점)은 광박 조건 미달",
        )
    }

    @Test
    fun `멍박 - 기본 룰(2인)에서는 미적용, 활성화 시 적용`() {
        val winner = cards("m01_gwang", "m03_gwang", "m08_gwang")
        val loserNoYeol = piCards(6) + cards("m11_gwang")
        assertFalse(ScoreCalculator.finalScore(winner, loserNoYeol, 0, 0, 0, 0).meongBak)
        val enabled = RuleSet(meongBakEnabled = true)
        val withMeong = ScoreCalculator.finalScore(winner, loserNoYeol, 0, 0, 0, 0, ruleSet = enabled)
        assertTrue(withMeong.meongBak)
        assertEquals(6, withMeong.total)
    }

    @Test
    fun `배수는 곱연산으로 누적된다 - 피박+흔들기 = 4배`() {
        val winner = cards("m01_gwang", "m03_gwang", "m08_gwang") // 3점
        val final = ScoreCalculator.finalScore(
            winnerCaptured = winner,
            loserCaptured = piCards(4) + cards("m11_gwang"), // 피박
            winnerGoCount = 0,
            loserGoCount = 0,
            shakeCount = 1,
            bombCount = 0,
        )
        assertEquals(12, final.total, "3 × 피박2 × 흔들기2")
    }

    @Test
    fun `고박 - 패자가 고를 불렀으면 플래그가 기록된다`() {
        val winner = cards("m01_gwang", "m03_gwang", "m08_gwang")
        val loser = piCards(6) + cards("m11_gwang")
        assertTrue(ScoreCalculator.finalScore(winner, loser, 0, loserGoCount = 2, shakeCount = 0, bombCount = 0).goBak)
        assertFalse(ScoreCalculator.finalScore(winner, loser, 0, loserGoCount = 0, shakeCount = 0, bombCount = 0).goBak)
    }

    // ------------------------------------------------------- §5 예시 계산 4종 재현

    @Test
    fun `예시1 - 광3(비광X) 피11장 = 5점`() {
        val winner = cards("m01_gwang", "m03_gwang", "m08_gwang") + piCards(11)
        val loser = cards("m11_gwang", "m11_pi1", "m11_pi2", "m11_pi3", "m12_pi1", "m07_pi1", "m07_pi2")
        val final = ScoreCalculator.finalScore(winner, loser, 0, 0, 0, 0)
        assertEquals(5, final.base.total, "광3 + 피2")
        assertEquals(5, final.total)
    }

    @Test
    fun `예시2 - 고도리 피12장 상대피4장 = 피박 16점`() {
        val winner = cards("m02_yeol", "m08_yeol", "m10_yeol") + piCards(12)
        val loser = cards("m11_gwang", "m11_pi1", "m11_pi2", "m11_pi3", "m12_pi1")
        val final = ScoreCalculator.finalScore(winner, loser, 0, 0, 0, 0)
        assertEquals(8, final.base.total, "고도리5 + 피3")
        assertTrue(final.piBak)
        assertEquals(16, final.total)
    }

    @Test
    fun `예시3 - 광3 흔들기 상대광0 = 광박+흔들기 12점`() {
        val winner = cards("m01_gwang", "m03_gwang", "m08_gwang")
        val loser = piCards(6)
        val final = ScoreCalculator.finalScore(winner, loser, 0, 0, shakeCount = 1, bombCount = 0)
        assertEquals(3, final.base.total)
        assertTrue(final.gwangBak)
        assertEquals(12, final.total, "3 × 광박2 × 흔들기2")
    }

    @Test
    fun `예시4 - 홍단3 피10장 2고 후 스톱 = 6점`() {
        val winner = cards("m01_tti", "m02_tti", "m03_tti") + piCards(10)
        val loser = cards("m11_gwang", "m11_pi1", "m11_pi2", "m11_pi3", "m12_pi1", "m07_pi1", "m07_pi2")
        val final = ScoreCalculator.finalScore(winner, loser, winnerGoCount = 2, loserGoCount = 0, shakeCount = 0, bombCount = 0)
        assertEquals(4, final.base.total, "홍단3 + 피1")
        assertEquals(6, final.afterGo, "2고 = +2")
        assertEquals(6, final.total)
    }
}
