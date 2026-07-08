package com.flowercards.feature.game

import androidx.lifecycle.ViewModel
import com.flowercards.domain.deal.Dealer
import com.flowercards.domain.engine.GameEvent
import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameState
import com.flowercards.domain.engine.PlayerAction
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.engine.TurnEngine
import com.flowercards.domain.rule.RuleSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.random.Random

/**
 * 인게임 상태 계층 (PLAN-phase2 §3). 도메인(불변 상태 + 순수 전이) 위의 얇은 배선.
 *
 * - 내부에 현재 [GameState]를 보유하고, [uiState]로 뷰 투영을 노출한다.
 * - [onAction]은 [TurnEngine.apply]를 호출해 상태를 갱신하고 1회성 [GameEvent]를 방출한다.
 * - 딜에 쓴 시드를 [seed]에 보관한다(테스트/리플레이용, §1-7).
 *
 * 모든 생성자 파라미터가 기본값을 가지므로 Kotlin이 파라미터 없는 생성자를 함께 만든다 →
 * Compose의 `viewModel()`이 리플렉션으로 인스턴스화할 수 있다.
 */
class GameViewModel(
    private val ruleSet: RuleSet = RuleSet(),
    private val seedSource: () -> Long = { Random.nextLong() },
) : ViewModel() {

    /** 마지막 딜에 사용한 시드 — 리플레이/디버깅용 */
    var seed: Long = 0L
        private set

    private lateinit var current: GameState

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * 1회성 이벤트 채널 (상태와 분리). Phase 4에서 SFX/햅틱이 구독한다.
     * [Channel.BUFFERED]로 구독자가 없어도 방출이 유실되지 않게 버퍼링한다.
     */
    private val _events = Channel<GameEvent>(Channel.BUFFERED)
    val events: Flow<GameEvent> = _events.receiveAsFlow()

    init {
        newGame()
    }

    /** 새 딜. 시드를 지정하면 재현 가능한 판을 만든다(테스트/리플레이). */
    fun newGame(seed: Long = seedSource()) {
        this.seed = seed
        val outcome = Dealer.deal(ruleSet, Random(seed))
        current = outcome.state
        publish()
        outcome.events.forEach { _events.trySend(it) } // 총통 즉시 종료 등 딜 이벤트
    }

    /**
     * 플레이어 행동 적용. 유효하지 않은 단계에서의 호출은 무시한다
     * (UI가 phase로 1차 가드하지만, 방어적으로 재확인).
     */
    fun onAction(action: PlayerAction) {
        if (current.phase == GamePhase.FINISHED) return
        val result = TurnEngine.apply(current, action)
        current = result.state
        publish()
        result.events.forEach { _events.trySend(it) }
    }

    /**
     * 2-A 디버그 전용 입력 경로. 표시 관점은 P1 고정([uiState])이지만, hotseat 교대를 눈으로
     * 검증하려면 turn==P2일 때도 진행돼야 한다 → **현재 턴 플레이어**의 손패 첫 장을 낸다.
     * 정식 손패 터치 입력·P2 패스앤플레이 UX는 2-C에서 대체한다.
     */
    fun playFirstCardOfCurrentTurn() {
        if (current.phase != GamePhase.AWAITING_PLAY) return
        val first = current.currentPlayer.hand.firstOrNull() ?: return
        onAction(PlayerAction.PlayCard(first))
    }

    private fun publish() {
        // 좌석 고정: 표시 관점은 항상 하단 = P1
        _uiState.value = current.toUiState(me = PlayerId.P1)
    }
}
