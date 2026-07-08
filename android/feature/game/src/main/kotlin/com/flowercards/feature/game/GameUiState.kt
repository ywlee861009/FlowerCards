package com.flowercards.feature.game

import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameResult
import com.flowercards.domain.engine.GameState
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.model.Card
import com.flowercards.domain.model.CardKind
import com.flowercards.domain.model.Month
import com.flowercards.domain.score.ScoreCalculator

/**
 * 획득패 카테고리별 장수 집계 (rules §1: 광·열끗·띠·피).
 * 점수가 아니라 "몇 장 모았나"의 뷰용 집계다 — 스트립 배지에 그대로 쓴다.
 */
data class CapturedSummary(
    val gwang: Int = 0,
    val yeol: Int = 0,
    val tti: Int = 0,
    val pi: Int = 0,
) {
    val total: Int get() = gwang + yeol + tti + pi
}

/**
 * 도메인 [GameState]의 뷰 투영 모델 (PLAN-phase2 §3).
 *
 * 도메인을 Compose에 그대로 넘기지 않고 투영한다 → 획득패 카테고리 집계·고 가능 여부·
 * 흔들기/폭탄 가능 월 계산 같은 뷰 로직을 여기서 흡수한다.
 *
 * **관점(perspective)**: 좌석 고정 — 하단 = [PlayerId.P1] (상단 = P2, Phase 3에서 AI 투입).
 * [myHand]/[myCaptured]/[myScore]는 항상 P1의 것이고, [oppHandCount] 등은 P2의 것이다.
 * [turn]은 지금 누구 차례인지를 표시용으로 노출한다(hotseat 교대 가시화).
 */
data class GameUiState(
    val myHand: List<Card> = emptyList(),
    val oppHandCount: Int = 0,
    val floor: List<Card> = emptyList(),
    val pileCount: Int = 0,
    val myCaptured: CapturedSummary = CapturedSummary(),
    val oppCaptured: CapturedSummary = CapturedSummary(),
    /** 지금 조작 주체 (hotseat: 이 플레이어가 곧 myHand의 주인) */
    val turn: PlayerId = PlayerId.P1,
    val phase: GamePhase = GamePhase.AWAITING_PLAY,
    val myScore: Int = 0,
    val oppScore: Int = 0,
    /** 현재 턴 플레이어가 스톱 임계(기본 3점) 이상인지 — 고/스톱 대상 여부 */
    val canGo: Boolean = false,
    /** 흔들기 가능 월: 손패 같은 월 3장 이상 보유 (rules §4.7) */
    val shakeableMonths: Set<Month> = emptySet(),
    /** 폭탄 가능 월: 손패 같은 월 3장 + 바닥 정확히 1장 (rules §4.7) */
    val bombableMonths: Set<Month> = emptySet(),
    val result: GameResult? = null,
)

/**
 * [GameState] → [GameUiState] 순수 투영.
 *
 * @param me 관점 플레이어. 좌석 고정이므로 ViewModel은 항상 [PlayerId.P1]을 넘긴다.
 */
fun GameState.toUiState(me: PlayerId): GameUiState {
    val myState = player(me)
    val oppState = player(me.opponent)

    val handMonthCounts: Map<Month, Int> = myState.hand.groupingBy { it.month }.eachCount()
    val floorMonthCounts: Map<Month, Int> = floor.groupingBy { it.month }.eachCount()

    // 흔들기: 손패 같은 월 3장 이상 (TurnEngine: hand.count { month } >= 3)
    val shakeable = handMonthCounts.filterValues { it >= 3 }.keys
    // 폭탄: 손패 같은 월 3장 + 바닥 정확히 1장 (TurnEngine: bombCards.size == 3 && floorMatch.size == 1)
    val bombable = handMonthCounts
        .filter { (month, count) -> count == 3 && floorMonthCounts[month] == 1 }
        .keys

    val myScore = ScoreCalculator.baseScore(myState.captured, ruleSet).total
    val oppScore = ScoreCalculator.baseScore(oppState.captured, ruleSet).total

    return GameUiState(
        myHand = myState.hand,
        oppHandCount = oppState.hand.size,
        floor = floor,
        pileCount = pile.size,
        myCaptured = myState.captured.toCapturedSummary(),
        oppCaptured = oppState.captured.toCapturedSummary(),
        turn = turn,
        phase = phase,
        myScore = myScore,
        oppScore = oppScore,
        canGo = myScore >= ruleSet.stopThreshold,
        shakeableMonths = shakeable,
        bombableMonths = bombable,
        result = result,
    )
}

private fun List<Card>.toCapturedSummary(): CapturedSummary {
    var gwang = 0
    var yeol = 0
    var tti = 0
    var pi = 0
    for (card in this) {
        when (card.kind) {
            CardKind.GWANG -> gwang++
            CardKind.YEOL -> yeol++
            CardKind.TTI -> tti++
            CardKind.PI -> pi++
        }
    }
    return CapturedSummary(gwang = gwang, yeol = yeol, tti = tti, pi = pi)
}
