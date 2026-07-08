package com.flowercards.feature.game

import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameResult
import com.flowercards.domain.engine.GameState
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.engine.PlayerState
import com.flowercards.domain.model.Card
import com.flowercards.domain.model.Month
import com.flowercards.domain.model.Deck
import com.flowercards.domain.rule.RuleSet

/**
 * feature:game 테스트 전용 픽스처. :domain 테스트 소스셋의 Fixtures는 이 모듈에서 접근 불가하므로
 * (testFixtures 아티팩트 미노출) 동일한 패턴을 로컬로 재구성한다.
 */
object TestFixtures {
    private val deck = Deck.standard()

    /** 덱 표준 id로 카드 조회 (예: "m01_gwang", "m02_tti", "m11_pi1"). */
    fun card(id: String): Card = deck.first { it.id == id }

    fun cards(vararg ids: String): List<Card> = ids.map(::card)

    /** GameState 부분 빌더. 카드 총수 48장 불변식은 강제하지 않는다(뷰 투영 단위 테스트 목적). */
    fun state(
        turn: PlayerId = PlayerId.P1,
        p1Hand: List<Card> = emptyList(),
        p1Captured: List<Card> = emptyList(),
        p2Hand: List<Card> = emptyList(),
        p2Captured: List<Card> = emptyList(),
        floor: List<Card> = emptyList(),
        pile: List<Card> = emptyList(),
        ppeokMonths: Map<Month, PlayerId> = emptyMap(),
        ruleSet: RuleSet = RuleSet(),
        phase: GamePhase = GamePhase.AWAITING_PLAY,
        result: GameResult? = null,
    ): GameState = GameState(
        ruleSet = ruleSet,
        turn = turn,
        players = mapOf(
            PlayerId.P1 to PlayerState(hand = p1Hand, captured = p1Captured),
            PlayerId.P2 to PlayerState(hand = p2Hand, captured = p2Captured),
        ),
        floor = floor,
        pile = pile,
        ppeokMonths = ppeokMonths,
        phase = phase,
        result = result,
    )
}
