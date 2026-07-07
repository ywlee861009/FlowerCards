package com.flowercards.domain.rule

/** 고 가산/배수 방식 (scoring §3, 확정: PLUS_ONE_THEN_DOUBLE) */
enum class GoBonusMode {
    /** 1·2고 각 +1점, 3고부터 매 고마다 ×2 (기본 확정안) */
    PLUS_ONE_THEN_DOUBLE,

    /** 매 고마다 +1점 */
    PLUS_ONE_EACH,

    /** 매 고마다 ×2 */
    DOUBLE_EACH,
}

/**
 * 확정 룰 13건(rules.md·scoring.md 하단 확정 표)을 파라미터화한 설정.
 * 기본값 = 2026-07-07 확정값. 지역 룰 변형은 이 객체 교체로 대응한다.
 */
data class RuleSet(
    /** #1 쌍피 처리 — 확정: 없음 */
    val useSsangPi: Boolean = false,
    /** #3 총통(손패 같은 월 4장) — 확정: 적용(즉시 승) */
    val chongtongEnabled: Boolean = true,
    /** 총통 승리 점수 */
    val chongtongScore: Int = 10,
    /** #4 딜 시 바닥 같은 월 4장 — 확정: 리딜 */
    val redealOnFloorFourOfMonth: Boolean = true,
    /** #5 자뻑 피 뺏기 장수 — 확정: 1장 */
    val jaPpeokPiSteal: Int = 1,
    /** #6 폭탄 추가 턴 — 확정: 있음 */
    val bombExtraTurn: Boolean = true,
    /** #7 나가리 곱(다음 판 ×2) — 확정: 없음 */
    val nagariCarryMultiplier: Boolean = false,
    /** #8·scoring#4 고 방식 — 확정: 1·2고 +1점, 3고↑ ×2 */
    val goBonusMode: GoBonusMode = GoBonusMode.PLUS_ONE_THEN_DOUBLE,
    /** scoring#2 멍박(상대 열끗 0장 ×2) 2인 적용 — 확정: 미적용 */
    val meongBakEnabled: Boolean = false,
    /** 고/스톱 선택이 가능해지는 최소 점수 (rules §5) */
    val stopThreshold: Int = 3,
    /** 따닥 피 뺏기 장수 (rules §4.4) */
    val ttadakPiSteal: Int = 1,
    /** 따조(바닥 3장 먹기) 피 뺏기 장수 (rules §4.4) */
    val ttajoPiSteal: Int = 1,
    /** 쪽 피 뺏기 장수 (rules §4.5) */
    val jjokPiSteal: Int = 1,
    /** 싹쓸이 피 뺏기 장수 (rules §4.6) */
    val sweepPiSteal: Int = 1,
    /** 폭탄 피 뺏기 장수 (rules §4.7) */
    val bombPiSteal: Int = 1,
    /** 피박 발동 기준: 상대 피가 이 장수 이하 (scoring §2) */
    val piBakThreshold: Int = 5,
)
