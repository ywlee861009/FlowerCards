package com.flowercards.domain.engine

import com.flowercards.domain.model.Card
import com.flowercards.domain.model.Month
import com.flowercards.domain.rule.RuleSet
import com.flowercards.domain.score.FinalScore

/** 2인 맞고 플레이어 식별자. 누가 사람/AI인지는 앱 레이어가 매핑한다. */
enum class PlayerId {
    P1, P2;

    val opponent: PlayerId
        get() = if (this == P1) P2 else P1
}

/** 플레이어 1명의 판 내 상태 */
data class PlayerState(
    val hand: List<Card> = emptyList(),
    val captured: List<Card> = emptyList(),
    /** 선언한 고 횟수 */
    val goCount: Int = 0,
    /** 마지막 고 선언 시점의 점수 — 고/스톱 재프롬프트 기준 (rules §5) */
    val lastGoScore: Int = 0,
    /** 흔들기 선언 횟수 (rules §4.7, 판 점수 ×2씩) */
    val shakeCount: Int = 0,
    /** 폭탄 횟수 (rules §4.7, 판 점수 ×2씩) */
    val bombCount: Int = 0,
)

/** 진행 단계 */
enum class GamePhase {
    /** 현재 턴 플레이어의 카드 내기 대기 */
    AWAITING_PLAY,

    /** 3점 이상 도달 — 고/스톱 선택 대기 (rules §5) */
    AWAITING_GO_STOP,

    /** 판 종료 (rules §6) */
    FINISHED,
}

/** 판 결과 (rules §6: 스톱 승 / 총통 즉시 승 / 나가리) */
sealed interface GameResult {
    data class Win(val winner: PlayerId, val score: FinalScore) : GameResult

    data class ChongtongWin(val winner: PlayerId, val month: Month, val score: Int) : GameResult

    data object Nagari : GameResult
}

/**
 * 판 전체 상태. 불변 — 전이는 [TurnEngine]의 순수 함수로만 일어난다.
 *
 * 불변식: 손패 + 바닥 + 더미 + 양쪽 획득 = 48장 (rules §1)
 */
data class GameState(
    val ruleSet: RuleSet,
    val turn: PlayerId,
    val players: Map<PlayerId, PlayerState>,
    val floor: List<Card>,
    val pile: List<Card>,
    /** 뻑으로 바닥에 묶인 월 → 뻑을 유발한 플레이어 (자뻑 판정용, rules §4.2~4.3) */
    val ppeokMonths: Map<Month, PlayerId> = emptyMap(),
    val phase: GamePhase = GamePhase.AWAITING_PLAY,
    val result: GameResult? = null,
    /** 폭탄 추가 턴 보류(확정 룰 #6) — 고/스톱 선택을 거쳐도 유지되도록 상태로 저장 */
    val pendingExtraTurn: Boolean = false,
) {
    val currentPlayer: PlayerState
        get() = players.getValue(turn)

    fun player(id: PlayerId): PlayerState = players.getValue(id)

    /** 판에 존재하는 총 카드 수 — 항상 48이어야 한다 */
    fun totalCards(): Int =
        players.values.sumOf { it.hand.size + it.captured.size } + floor.size + pile.size

    internal fun updatePlayer(id: PlayerId, transform: (PlayerState) -> PlayerState): GameState =
        copy(players = players + (id to transform(players.getValue(id))))
}
