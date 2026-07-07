package com.flowercards.domain

import com.flowercards.domain.engine.GameState
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.engine.PlayerState
import com.flowercards.domain.model.Card
import com.flowercards.domain.model.Deck
import com.flowercards.domain.rule.RuleSet

/** 테스트 공용 픽스처. 카드는 덱의 id로 참조한다 (예: "m01_gwang"). */
object Fixtures {
    private val deck = Deck.standard()

    fun card(id: String): Card = deck.first { it.id == id }

    fun cards(vararg ids: String): List<Card> = ids.map(::card)

    /** 서로 다른 월의 일반 피 [count]장 — 점수 구성용 */
    fun piCards(count: Int): List<Card> {
        val pis = deck.filter { it.kind == com.flowercards.domain.model.CardKind.PI }
        require(count <= pis.size)
        return pis.take(count)
    }

    /** 부분 상태 빌더. 카드 총수는 48장이 아니어도 되며 엔진은 전이 전후 보존만 검사한다. */
    fun state(
        turn: PlayerId = PlayerId.P1,
        p1Hand: List<Card> = emptyList(),
        p1Captured: List<Card> = emptyList(),
        p2Hand: List<Card> = emptyList(),
        p2Captured: List<Card> = emptyList(),
        floor: List<Card> = emptyList(),
        pile: List<Card> = emptyList(),
        ppeokMonths: Map<com.flowercards.domain.model.Month, PlayerId> = emptyMap(),
        ruleSet: RuleSet = RuleSet(),
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
    )
}
