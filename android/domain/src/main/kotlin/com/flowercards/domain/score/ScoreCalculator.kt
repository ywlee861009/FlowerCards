package com.flowercards.domain.score

import com.flowercards.domain.model.Card
import com.flowercards.domain.model.CardKind
import com.flowercards.domain.model.TtiColor
import com.flowercards.domain.rule.GoBonusMode
import com.flowercards.domain.rule.RuleSet

/**
 * 획득 카드의 기본 점수 내역 (scoring §1).
 */
data class ScoreBreakdown(
    val gwang: Int,
    val yeol: Int,
    val godori: Int,
    val tti: Int,
    val hongDan: Int,
    val cheongDan: Int,
    val choDan: Int,
    val pi: Int,
) {
    val total: Int
        get() = gwang + yeol + godori + tti + hongDan + cheongDan + choDan + pi
}

/**
 * 승자 최종 점수 (scoring §4 계산 순서).
 * 고박은 확정 룰대로 "진 사람이 상대 점수만큼 물어줌"(정산 이벤트)이므로
 * 점수 배수가 아니라 플래그로만 기록한다. 판돈 환산은 MVP 제외.
 */
data class FinalScore(
    val base: ScoreBreakdown,
    val goCount: Int,
    /** 고 가산/배수 적용 후 점수 (scoring §3) */
    val afterGo: Int,
    val piBak: Boolean,
    val gwangBak: Boolean,
    val meongBak: Boolean,
    val goBak: Boolean,
    /** 흔들기·폭탄 ×2 곱연산 누적 배수 (scoring §2) */
    val shakeBombMultiplier: Int,
    val total: Int,
)

object ScoreCalculator {

    /** 피 장수(쌍피 채택 시 쌍피 2장 계산 — 현재 확정 룰은 쌍피 없음) */
    fun piCount(captured: List<Card>, ruleSet: RuleSet = RuleSet()): Int =
        captured.count { it.kind == CardKind.PI }

    /** 기본 점수 합산 (scoring §1) */
    fun baseScore(captured: List<Card>, ruleSet: RuleSet = RuleSet()): ScoreBreakdown {
        val gwangCards = captured.filter { it.kind == CardKind.GWANG }
        val yeolCards = captured.filter { it.kind == CardKind.YEOL }
        val ttiCards = captured.filter { it.kind == CardKind.TTI }

        return ScoreBreakdown(
            gwang = gwangScore(gwangCards),
            yeol = thresholdScore(yeolCards.size, from = 5),
            godori = if (yeolCards.count { it.isGodori } == 3) 5 else 0,
            tti = thresholdScore(ttiCards.size, from = 5),
            hongDan = danScore(ttiCards, TtiColor.HONG),
            cheongDan = danScore(ttiCards, TtiColor.CHEONG),
            choDan = danScore(ttiCards, TtiColor.CHO),
            pi = thresholdScore(piCount(captured, ruleSet), from = 10),
        )
    }

    /** 광 점수: 3광(비광X)=3 / 3광(비광O)=2 / 4광=4 / 5광=15 (scoring §1.1) */
    fun gwangScore(gwangCards: List<Card>): Int = when (gwangCards.size) {
        in 0..2 -> 0
        3 -> if (gwangCards.any { it.isBiGwang }) 2 else 3
        4 -> 4
        else -> 15
    }

    /** N장부터 1점, 이후 1장당 +1점 */
    private fun thresholdScore(count: Int, from: Int): Int =
        if (count >= from) count - from + 1 else 0

    private fun danScore(ttiCards: List<Card>, color: TtiColor): Int =
        if (ttiCards.count { it.ttiColor == color } == 3) 3 else 0

    /** 고 가산/배수 (scoring §3): 기본안 = 1·2고 각 +1, 3고부터 매 고 ×2 */
    fun applyGoBonus(score: Int, goCount: Int, mode: GoBonusMode): Int = when (mode) {
        GoBonusMode.PLUS_ONE_THEN_DOUBLE -> {
            var result = score + minOf(goCount, 2)
            repeat(maxOf(goCount - 2, 0)) { result *= 2 }
            result
        }
        GoBonusMode.PLUS_ONE_EACH -> score + goCount
        GoBonusMode.DOUBLE_EACH -> {
            var result = score
            repeat(goCount) { result *= 2 }
            result
        }
    }

    /**
     * 최종 점수 (scoring §4 순서: 기본 → 고 → 박 → 흔들기/폭탄 → 고박 기록).
     *
     * @param shakeCount 이번 판의 흔들기 선언 횟수(양쪽 합)
     * @param bombCount 이번 판의 폭탄 횟수(양쪽 합) — 흔들기·폭탄은 "판 점수 ×2"이므로 판 전체에 적용
     * @param loserGoCount 패자가 선언한 고 횟수 — 1 이상이면 고박
     */
    fun finalScore(
        winnerCaptured: List<Card>,
        loserCaptured: List<Card>,
        winnerGoCount: Int,
        loserGoCount: Int,
        shakeCount: Int,
        bombCount: Int,
        ruleSet: RuleSet = RuleSet(),
    ): FinalScore {
        val base = baseScore(winnerCaptured, ruleSet)
        val afterGo = applyGoBonus(base.total, winnerGoCount, ruleSet.goBonusMode)

        val loserPi = piCount(loserCaptured, ruleSet)
        val piBak = loserPi <= ruleSet.piBakThreshold
        val gwangBak = base.gwang >= 3 &&
            loserCaptured.none { it.kind == CardKind.GWANG }
        val meongBak = ruleSet.meongBakEnabled &&
            loserCaptured.none { it.kind == CardKind.YEOL }

        var multiplier = 1
        if (piBak) multiplier *= 2
        if (gwangBak) multiplier *= 2
        if (meongBak) multiplier *= 2
        val shakeBombMultiplier = 1 shl (shakeCount + bombCount)

        return FinalScore(
            base = base,
            goCount = winnerGoCount,
            afterGo = afterGo,
            piBak = piBak,
            gwangBak = gwangBak,
            meongBak = meongBak,
            goBak = loserGoCount > 0,
            shakeBombMultiplier = shakeBombMultiplier,
            total = afterGo * multiplier * shakeBombMultiplier,
        )
    }
}
