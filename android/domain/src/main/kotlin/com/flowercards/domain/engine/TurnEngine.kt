package com.flowercards.domain.engine

import com.flowercards.domain.model.Card
import com.flowercards.domain.model.CardKind
import com.flowercards.domain.score.ScoreCalculator

/** 플레이어(사람/AI)가 엔진에 보내는 행동 */
sealed interface PlayerAction {
    /**
     * 손패 1장 내기 (rules §4 턴 순서 1~7을 이 한 번의 전이로 처리).
     *
     * @param floorChoice 바닥에 같은 월이 2장일 때 가져갈 카드 선택 (rules §4.1). 생략 시 자동 선택
     * @param declareShake 흔들기 선언 — 같은 월 3장 보유 시에만 유효 (rules §4.7)
     */
    data class PlayCard(
        val card: Card,
        val floorChoice: Card? = null,
        val declareShake: Boolean = false,
    ) : PlayerAction

    /** 폭탄: 손패 같은 월 3장 + 바닥 1장 → 4장 획득 (rules §4.7) */
    data class PlayBomb(val month: com.flowercards.domain.model.Month) : PlayerAction

    /** 손패가 빈 턴(폭탄으로 인한 비대칭) — 더미만 뒤집는다 */
    data object FlipOnly : PlayerAction

    /** 고 (rules §5) */
    data object DeclareGo : PlayerAction

    /** 스톱 — 판 종료 (rules §5) */
    data object DeclareStop : PlayerAction
}

/** 상태 전이 결과: 새 상태 + 이번 전이에서 발생한 이벤트 순서열 */
data class TurnResult(val state: GameState, val events: List<GameEvent>)

/**
 * 2인 맞고 턴 엔진. 불변 [GameState]에 대한 순수 함수 전이만 제공한다.
 * 렌더링·사운드는 [GameEvent] 스트림을 소비한다.
 */
object TurnEngine {

    fun apply(state: GameState, action: PlayerAction): TurnResult {
        check(state.phase != GamePhase.FINISHED) { "이미 종료된 판이다" }
        val result = when (action) {
            is PlayerAction.PlayCard -> playCard(state, action)
            is PlayerAction.PlayBomb -> playBomb(state, action)
            PlayerAction.FlipOnly -> flipOnly(state)
            PlayerAction.DeclareGo -> declareGo(state)
            PlayerAction.DeclareStop -> declareStop(state)
        }
        // 카드 보존 불변식 (rules §1): 딜이 48장을 보장하므로 전이는 총수를 바꿀 수 없다
        check(result.state.totalCards() == state.totalCards()) {
            "카드 총합 불변식 위반: ${state.totalCards()} -> ${result.state.totalCards()}"
        }
        return result
    }

    // ---------------------------------------------------------------- 카드 내기

    private fun playCard(state: GameState, action: PlayerAction.PlayCard): TurnResult {
        check(state.phase == GamePhase.AWAITING_PLAY) { "카드를 낼 수 있는 단계가 아니다" }
        val me = state.turn
        val hand = state.currentPlayer.hand
        require(action.card in hand) { "손패에 없는 카드: ${action.card.id}" }

        val events = mutableListOf<GameEvent>()
        val floorWasEmpty = state.floor.isEmpty()
        var working = state

        // 흔들기 선언 (rules §4.7): 같은 월 3장 보유 시 선언, 판 점수 ×2
        if (action.declareShake) {
            require(hand.count { it.month == action.card.month } >= 3) {
                "흔들기는 같은 월 3장 보유 시에만 가능"
            }
            working = working.updatePlayer(me) { it.copy(shakeCount = it.shakeCount + 1) }
            events += GameEvent.Shake(me, action.card.month)
        }

        working = working.updatePlayer(me) { it.copy(hand = it.hand - action.card) }
        events += GameEvent.CardPlayed(me, action.card)

        // 턴 순서 2: 낸 카드 매칭 (rules §4.1)
        var floor = working.floor
        val handMatches = floor.filter { it.month == action.card.month }
        var playedCardOnFloor = false
        val pendingHand: List<Card> = when (handMatches.size) {
            0 -> {
                floor = floor + action.card
                playedCardOnFloor = true
                emptyList()
            }
            1 -> {
                floor = floor - handMatches[0]
                listOf(action.card, handMatches[0])
            }
            2 -> {
                val pick = action.floorChoice
                    ?.also { require(it in handMatches) { "floorChoice가 매칭 후보가 아니다" } }
                    ?: autoPick(handMatches)
                floor = floor - pick
                listOf(action.card, pick)
            }
            else -> { // 3장: 따조 또는 뻑 4장 완성 (rules §4.4, §4.2)
                floor = floor - handMatches.toSet()
                listOf(action.card) + handMatches
            }
        }

        return resolveFlipAndCapture(
            state = working.copy(floor = floor),
            events = events,
            playedCard = action.card,
            playedCardOnFloor = playedCardOnFloor,
            pendingHand = pendingHand,
            pendingHandIsBomb = false,
            floorWasEmptyAtTurnStart = floorWasEmpty,
            extraTurn = false,
        )
    }

    // ---------------------------------------------------------------- 폭탄

    private fun playBomb(state: GameState, action: PlayerAction.PlayBomb): TurnResult {
        check(state.phase == GamePhase.AWAITING_PLAY) { "카드를 낼 수 있는 단계가 아니다" }
        val me = state.turn
        val bombCards = state.currentPlayer.hand.filter { it.month == action.month }
        val floorMatch = state.floor.filter { it.month == action.month }
        require(bombCards.size == 3 && floorMatch.size == 1) {
            "폭탄은 손패 같은 월 3장 + 바닥 1장일 때만 가능 (rules §4.7)"
        }

        val events = mutableListOf<GameEvent>()
        var working = state.updatePlayer(me) {
            it.copy(hand = it.hand - bombCards.toSet(), bombCount = it.bombCount + 1)
        }
        working = working.copy(floor = working.floor - floorMatch.toSet())
        events += GameEvent.Bomb(me, action.month)
        working = steal(working, me, state.ruleSet.bombPiSteal, events)

        return resolveFlipAndCapture(
            state = working,
            events = events,
            playedCard = null,
            playedCardOnFloor = false,
            pendingHand = bombCards + floorMatch,
            pendingHandIsBomb = true,
            floorWasEmptyAtTurnStart = false, // 폭탄은 바닥 매칭 1장이 전제
            extraTurn = state.ruleSet.bombExtraTurn,
        )
    }

    // ---------------------------------------------------------------- 빈 손 턴

    private fun flipOnly(state: GameState): TurnResult {
        check(state.phase == GamePhase.AWAITING_PLAY) { "카드를 낼 수 있는 단계가 아니다" }
        require(state.currentPlayer.hand.isEmpty()) { "손패가 남아 있으면 FlipOnly 불가" }
        require(state.pile.isNotEmpty()) { "더미가 비어 FlipOnly 불가" }
        return resolveFlipAndCapture(
            state = state,
            events = mutableListOf(),
            playedCard = null,
            playedCardOnFloor = false,
            pendingHand = emptyList(),
            pendingHandIsBomb = false,
            floorWasEmptyAtTurnStart = state.floor.isEmpty(),
            extraTurn = false,
        )
    }

    // ---------------------------------------------------- 턴 순서 3~7 공통 처리

    /**
     * 더미 뒤집기(3) → 뒤집은 카드 매칭(4) → 획득(5) → 특수 판정(6) → 점수/고스톱(7).
     * rules §4의 턴 순서와 1:1.
     */
    private fun resolveFlipAndCapture(
        state: GameState,
        events: MutableList<GameEvent>,
        playedCard: Card?,
        playedCardOnFloor: Boolean,
        pendingHand: List<Card>,
        pendingHandIsBomb: Boolean,
        floorWasEmptyAtTurnStart: Boolean,
        extraTurn: Boolean,
    ): TurnResult {
        val me = state.turn
        val ruleSet = state.ruleSet
        var working = state
        var floor = state.floor
        var handPending = pendingHand
        var jjok = false
        var ppeokHappened = false

        // 턴 순서 3~4: 더미 뒤집기와 매칭
        var flipPending: List<Card> = emptyList()
        val flipCard = working.pile.firstOrNull()
        if (flipCard != null) {
            working = working.copy(pile = working.pile.drop(1))
            events += GameEvent.CardFlipped(flipCard)

            val flipMatches = floor.filter { it.month == flipCard.month }
            when {
                // 뻑 (rules §4.2): 낸 카드+매칭 카드+뒤집은 카드가 같은 월 3장으로 바닥에 묶임
                flipMatches.isEmpty() && handPending.size == 2 &&
                    playedCard != null && flipCard.month == playedCard.month -> {
                    floor = floor + handPending + flipCard
                    handPending = emptyList()
                    working = working.copy(
                        ppeokMonths = working.ppeokMonths + (playedCard.month to me),
                    )
                    ppeokHappened = true
                    events += GameEvent.Ppeok(me, playedCard.month)
                }

                // 쪽 (rules §4.5): 못 먹고 남긴 낸 카드를 뒤집은 카드가 잡음
                playedCardOnFloor && playedCard != null &&
                    flipMatches.size == 1 && flipMatches[0] == playedCard -> {
                    floor = floor - playedCard
                    flipPending = listOf(flipCard, playedCard)
                    jjok = true
                }

                flipMatches.isEmpty() -> floor = floor + flipCard

                flipMatches.size <= 2 -> {
                    val pick = if (flipMatches.size == 1) flipMatches[0] else autoPick(flipMatches)
                    floor = floor - pick
                    flipPending = listOf(flipCard, pick)
                }

                else -> { // 3장: 따조/뻑 4장 완성
                    floor = floor - flipMatches.toSet()
                    flipPending = listOf(flipCard) + flipMatches
                }
            }
        }
        working = working.copy(floor = floor)

        // 턴 순서 5: 획득
        val capturedNow = handPending + flipPending
        if (capturedNow.isNotEmpty()) {
            working = working.updatePlayer(me) { it.copy(captured = it.captured + capturedNow) }
            events += GameEvent.Captured(me, capturedNow)
        }

        // 턴 순서 6: 특수 상황 판정
        if (jjok) {
            events += GameEvent.Jjok(me)
            working = steal(working, me, ruleSet.jjokPiSteal, events)
        }
        for (quad in listOf(handPending, flipPending).filter { it.size == 4 }) {
            val month = quad[0].month
            val ppeokOwner = working.ppeokMonths[month]
            val fromHandPlay = quad === handPending && playedCard != null
            if (ppeokOwner != null) {
                working = working.copy(ppeokMonths = working.ppeokMonths - month)
                // 자뻑 (rules §4.3): "4번째 카드를 본인이 내서" — 손패 플레이 완성만 보너스.
                // 더미 뒤집기로 우연히 완성되면 보너스 없는 일반 획득이다.
                if (ppeokOwner == me && fromHandPlay) {
                    events += GameEvent.JaPpeok(me, month)
                    working = steal(working, me, ruleSet.jaPpeokPiSteal, events)
                }
            } else if (!(pendingHandIsBomb && quad === pendingHand)) { // 폭탄 quad는 따조가 아님
                events += GameEvent.Ttajo(me, month) // 따조 (rules §4.4)
                working = steal(working, me, ruleSet.ttajoPiSteal, events)
            }
        }
        // 따닥 (rules §4.4): 낸 카드와 뒤집은 카드로 서로 다른 월을 한 턴에 2번 먹음
        if (handPending.size == 2 && flipPending.size == 2 && playedCard != null &&
            handPending[0].month != flipPending[0].month
        ) {
            events += GameEvent.Ttadak(me)
            working = steal(working, me, ruleSet.ttadakPiSteal, events)
        }
        // 싹쓸이 (rules §4.6): 바닥을 전부 쓸어 0장.
        // 턴 시작 시 바닥이 이미 비어 있었다면(내 카드+쪽만으로 0장) "쓸었다"고 보지 않는다.
        if (working.floor.isEmpty() && !floorWasEmptyAtTurnStart &&
            capturedNow.isNotEmpty() && !ppeokHappened
        ) {
            events += GameEvent.Sweep(me)
            working = steal(working, me, ruleSet.sweepPiSteal, events)
        }

        // 턴 순서 7: 점수 판정 → 고/스톱 or 다음 턴 (rules §5)
        return judgeScoreAndAdvance(state, working, events, extraTurn)
    }

    private fun judgeScoreAndAdvance(
        before: GameState,
        after: GameState,
        events: MutableList<GameEvent>,
        extraTurn: Boolean,
    ): TurnResult {
        val me = before.turn
        val scoreBefore = ScoreCalculator.baseScore(before.player(me).captured, before.ruleSet).total
        val scoreAfter = ScoreCalculator.baseScore(after.player(me).captured, after.ruleSet).total
        val myState = after.player(me)

        if (scoreAfter >= after.ruleSet.stopThreshold &&
            scoreAfter > scoreBefore &&
            scoreAfter > myState.lastGoScore
        ) {
            events += GameEvent.GoStopChoice(me, scoreAfter)
            // 폭탄 추가 턴은 고 선언 후에도 살아 있어야 하므로 상태에 보류한다 (확정 룰 #6)
            return TurnResult(
                after.copy(phase = GamePhase.AWAITING_GO_STOP, pendingExtraTurn = extraTurn),
                events,
            )
        }
        return advanceOrEnd(after, events, extraTurn)
    }

    /**
     * 다음 턴으로 넘기거나, 양쪽 손패 소진 시 나가리 종료 (rules §4.8, §6).
     * 폭탄으로 손패가 더미보다 먼저 소진돼도 판은 손패 기준으로 끝난다 —
     * 남은 더미는 뒤집지 않는다(실물 맞고 관행).
     */
    private fun advanceOrEnd(
        state: GameState,
        events: MutableList<GameEvent>,
        extraTurn: Boolean,
    ): TurnResult {
        if (state.players.values.all { it.hand.isEmpty() }) {
            events += GameEvent.Nagari
            val result = GameResult.Nagari
            events += GameEvent.GameEnded(result)
            return TurnResult(state.copy(phase = GamePhase.FINISHED, result = result), events)
        }
        fun canAct(id: PlayerId) = state.player(id).hand.isNotEmpty() || state.pile.isNotEmpty()
        val next = when {
            extraTurn && canAct(state.turn) -> state.turn // 폭탄 추가 턴 (확정 룰 #6)
            !canAct(state.turn.opponent) -> state.turn
            else -> state.turn.opponent
        }
        return TurnResult(
            state.copy(turn = next, phase = GamePhase.AWAITING_PLAY, pendingExtraTurn = false),
            events,
        )
    }

    // ---------------------------------------------------------------- 고 / 스톱

    private fun declareGo(state: GameState): TurnResult {
        check(state.phase == GamePhase.AWAITING_GO_STOP) { "고/스톱 선택 단계가 아니다" }
        val me = state.turn
        val score = ScoreCalculator.baseScore(state.currentPlayer.captured, state.ruleSet).total
        val working = state.updatePlayer(me) {
            it.copy(goCount = it.goCount + 1, lastGoScore = score)
        }
        val events = mutableListOf<GameEvent>(
            GameEvent.GoDeclared(me, working.player(me).goCount),
        )
        // 폭탄 턴에 3점 도달 → 고 선언 시 보류해 둔 추가 턴을 소비한다 (확정 룰 #6)
        return advanceOrEnd(working, events, extraTurn = state.pendingExtraTurn)
    }

    private fun declareStop(state: GameState): TurnResult {
        check(state.phase == GamePhase.AWAITING_GO_STOP) { "고/스톱 선택 단계가 아니다" }
        val me = state.turn
        val opponent = me.opponent
        val finalScore = ScoreCalculator.finalScore(
            winnerCaptured = state.player(me).captured,
            loserCaptured = state.player(opponent).captured,
            winnerGoCount = state.player(me).goCount,
            loserGoCount = state.player(opponent).goCount,
            shakeCount = state.players.values.sumOf { it.shakeCount },
            bombCount = state.players.values.sumOf { it.bombCount },
            ruleSet = state.ruleSet,
        )
        val result = GameResult.Win(me, finalScore)
        val events = listOf(
            GameEvent.Stopped(me),
            GameEvent.GameEnded(result),
        )
        return TurnResult(state.copy(phase = GamePhase.FINISHED, result = result), events)
    }

    // ---------------------------------------------------------------- 유틸

    /** 바닥 매칭 후보 2장 중 자동 선택: 광 > 열끗 > 띠 > 피. Phase 2에서 UI 선택으로 대체 */
    private fun autoPick(candidates: List<Card>): Card =
        candidates.minBy { it.kind.ordinal }

    /** 상대 피 뺏기: 피 1장(일반 피)을 상대 획득에서 내 획득으로 이동 */
    private fun steal(
        state: GameState,
        to: PlayerId,
        count: Int,
        events: MutableList<GameEvent>,
    ): GameState {
        var working = state
        val from = to.opponent
        repeat(count) {
            val pi = working.player(from).captured.firstOrNull { it.kind == CardKind.PI }
                ?: return working
            working = working
                .updatePlayer(from) { it.copy(captured = it.captured - pi) }
                .updatePlayer(to) { it.copy(captured = it.captured + pi) }
            events += GameEvent.PiStolen(from, to, pi)
        }
        return working
    }
}
