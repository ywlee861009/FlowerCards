package com.flowercards.domain.engine

import com.flowercards.domain.model.Card
import com.flowercards.domain.model.Month

/**
 * 도메인이 방출하는 이벤트 스트림.
 * game-loop.md §5 "손맛/사운드 트리거 매핑" 표와 1:1 대응 — UI·사운드·햅틱 레이어는
 * 이 이벤트만 구독하면 된다 (rules §7 AC 마지막 항목).
 */
sealed interface GameEvent {
    /** 손패에서 카드를 바닥에 냄 — "탁" */
    data class CardPlayed(val player: PlayerId, val card: Card) : GameEvent

    /** 더미 뒤집기 — "촤르륵" */
    data class CardFlipped(val card: Card) : GameEvent

    /** 매칭된 카드들이 획득 더미로 이동 — "찰칵"+"촥" */
    data class Captured(val player: PlayerId, val cards: List<Card>) : GameEvent

    /** 뻑 — 둔탁음 + 화면 흔들림 (rules §4.2) */
    data class Ppeok(val player: PlayerId, val month: Month) : GameEvent

    /** 자뻑 — 강한 타격음 + 카메라 흔들림 (rules §4.3) */
    data class JaPpeok(val player: PlayerId, val month: Month) : GameEvent

    /** 따닥 — 콤보음 2단 (rules §4.4) */
    data class Ttadak(val player: PlayerId) : GameEvent

    /** 따조(바닥 3장 먹기) (rules §4.4) */
    data class Ttajo(val player: PlayerId, val month: Month) : GameEvent

    /** 쪽 — "쪽!" + 반짝임 (rules §4.5) */
    data class Jjok(val player: PlayerId) : GameEvent

    /** 싹쓸이 — 스윕 라이트 (rules §4.6) */
    data class Sweep(val player: PlayerId) : GameEvent

    /** 흔들기 선언 — 3장 흔들림 모션 (rules §4.7) */
    data class Shake(val player: PlayerId, val month: Month) : GameEvent

    /** 폭탄 — 슬램 + "쾅!" (rules §4.7) */
    data class Bomb(val player: PlayerId, val month: Month) : GameEvent

    /** 상대 피 뺏기 — 피가 내 스트립으로 비행 */
    data class PiStolen(val from: PlayerId, val to: PlayerId, val card: Card) : GameEvent

    /** 3점 이상 도달 — 고/스톱 오버레이 표시 (rules §5) */
    data class GoStopChoice(val player: PlayerId, val score: Int) : GameEvent

    /** "고!" 선언 — 보이스 + 골드 글로우 */
    data class GoDeclared(val player: PlayerId, val goCount: Int) : GameEvent

    /** "스톱" — 결과 화면 전환 */
    data class Stopped(val player: PlayerId) : GameEvent

    /** 총통 즉시 종료 (rules §3) */
    data class Chongtong(val player: PlayerId, val month: Month) : GameEvent

    /** 나가리 — 무승부 (rules §4.8) */
    data object Nagari : GameEvent

    /** 판 종료 — 결과 확정 */
    data class GameEnded(val result: GameResult) : GameEvent
}
