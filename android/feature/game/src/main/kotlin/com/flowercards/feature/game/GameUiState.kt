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
 * 획득패 카테고리별 장수 집계 (rules §1: 광·열끗·띠·피). 배지 표기용.
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
 * 획득패를 카테고리별 카드 리스트로 보관 (2-C: 획득 스트립을 실제 카드 이미지 겹침으로 렌더, game-loop §2).
 */
data class CapturedCards(
    val gwang: List<Card> = emptyList(),
    val yeol: List<Card> = emptyList(),
    val tti: List<Card> = emptyList(),
    val pi: List<Card> = emptyList(),
)

/**
 * 바닥을 월 단위로 묶은 뷰 (2-D: 뻑 스탬프를 올바른 월 더미 위에 앉히기 위함).
 * @param isPpeok 이 월이 뻑으로 묶였는지 (GameState.ppeokMonths)
 */
data class FloorGroup(
    val month: Month,
    val cards: List<Card>,
    val isPpeok: Boolean,
)

/**
 * 도메인 [GameState]의 뷰 투영 모델 (PLAN-phase2 §3, 2-C 갱신).
 *
 * **좌석 고정**: 하단 = P1, 상단 = P2. `my*`(P1)·`opp*`(P2)는 좌석 기준이다.
 * **상호작용**: hotseat이므로 실제 조작 대상은 현재 턴 플레이어 → `active*`로 별도 투영한다.
 * (Phase 3에서 P2=AI가 되면 P2 턴의 패스앤플레이 계층만 제거하면 된다.)
 */
data class GameUiState(
    // ---- 좌석 고정 (P1 하단 / P2 상단) ----
    val myHand: List<Card> = emptyList(),
    val oppHandCount: Int = 0,
    val floor: List<Card> = emptyList(),
    val pileCount: Int = 0,
    val myCaptured: CapturedSummary = CapturedSummary(),
    val oppCaptured: CapturedSummary = CapturedSummary(),
    val myCapturedCards: CapturedCards = CapturedCards(),
    val oppCapturedCards: CapturedCards = CapturedCards(),
    val myScore: Int = 0,
    val oppScore: Int = 0,
    // ---- 바닥(월/뻑 그룹) ----
    val floorGroups: List<FloorGroup> = emptyList(),
    // ---- 진행 상태 ----
    val turn: PlayerId = PlayerId.P1,
    val phase: GamePhase = GamePhase.AWAITING_PLAY,
    val result: GameResult? = null,
    // ---- turn 기준 (상호작용/액션바/고 판정) ----
    val activePlayer: PlayerId = PlayerId.P1,
    val activeHand: List<Card> = emptyList(),
    /** 현재 턴 플레이어 기본 점수 (고/스톱 모달 표기) */
    val activeScore: Int = 0,
    /** 현재 턴 플레이어 누적 고 횟수 */
    val activeGoCount: Int = 0,
    /** 현재 턴 플레이어가 스톱 임계(기본 3점) 이상인지 */
    val activeCanGo: Boolean = false,
    /** 현재 턴 플레이어 흔들기 가능 월: 손패 같은 월 3장 이상 (rules §4.7) */
    val activeShakeableMonths: Set<Month> = emptySet(),
    /** 현재 턴 플레이어 폭탄 가능 월: 손패 같은 월 3장 + 바닥 정확히 1장 (rules §4.7) */
    val activeBombableMonths: Set<Month> = emptySet(),
) {
    /** 카드 낼 수 있는 상태 */
    val canPlay: Boolean
        get() = phase == GamePhase.AWAITING_PLAY && activeHand.isNotEmpty()
}

/**
 * [GameState] → [GameUiState] 순수 투영. 좌석 기준(me=P1) + turn 기준(active) 동시 계산.
 *
 * @param me 좌석 관점 플레이어(하단). ViewModel은 [PlayerId.P1]을 넘긴다.
 */
fun GameState.toUiState(me: PlayerId): GameUiState {
    val myState = player(me)
    val oppState = player(me.opponent)
    val active = player(turn)

    val handMonthCounts: Map<Month, Int> = active.hand.groupingBy { it.month }.eachCount()
    val floorMonthCounts: Map<Month, Int> = floor.groupingBy { it.month }.eachCount()
    // 흔들기: 손패 같은 월 3장 이상 (TurnEngine: hand.count { month } >= 3)
    val shakeable = handMonthCounts.filterValues { it >= 3 }.keys
    // 폭탄: 손패 같은 월 3장 + 바닥 정확히 1장 (TurnEngine: bombCards.size == 3 && floorMatch.size == 1)
    val bombable = handMonthCounts
        .filter { (month, count) -> count == 3 && floorMonthCounts[month] == 1 }
        .keys

    val myScore = ScoreCalculator.baseScore(myState.captured, ruleSet).total
    val oppScore = ScoreCalculator.baseScore(oppState.captured, ruleSet).total
    val activeScore = ScoreCalculator.baseScore(active.captured, ruleSet).total

    val groups = floor
        .groupBy { it.month }
        .map { (month, cards) -> FloorGroup(month, cards, isPpeok = ppeokMonths.containsKey(month)) }

    return GameUiState(
        myHand = myState.hand,
        oppHandCount = oppState.hand.size,
        floor = floor,
        pileCount = pile.size,
        myCaptured = myState.captured.toCapturedSummary(),
        oppCaptured = oppState.captured.toCapturedSummary(),
        myCapturedCards = myState.captured.toCapturedCards(),
        oppCapturedCards = oppState.captured.toCapturedCards(),
        myScore = myScore,
        oppScore = oppScore,
        floorGroups = groups,
        turn = turn,
        phase = phase,
        result = result,
        activePlayer = turn,
        activeHand = active.hand,
        activeScore = activeScore,
        activeGoCount = active.goCount,
        activeCanGo = activeScore >= ruleSet.stopThreshold,
        activeShakeableMonths = shakeable,
        activeBombableMonths = bombable,
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

private fun List<Card>.toCapturedCards(): CapturedCards = CapturedCards(
    gwang = filter { it.kind == CardKind.GWANG },
    yeol = filter { it.kind == CardKind.YEOL },
    tti = filter { it.kind == CardKind.TTI },
    pi = filter { it.kind == CardKind.PI },
)
