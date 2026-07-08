package com.flowercards.feature.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowercards.domain.engine.GameEvent
import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.GameResult
import com.flowercards.domain.engine.PlayerAction
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.model.Card
import com.flowercards.feature.game.render.CARD_ASPECT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 잠정 지속시간(ms) — game-feel 확정(Phase 4) 전 임시값
private const val FLASH_MS = 340
private const val GO_GLOW_MS = 900
private const val TOAST_MS = 950
private const val BURST_MS = 800
private const val BANNER_MS = 950
private const val PI_FLIGHT_MS = 620
private const val RESULT_FADE_MS = 500

// 카드 플레이 연출 타이밍(ms) — 사용자 확정값(Phase 4 game-feel 확정 전 상수화).
private const val PLAY_SLIDE_MS = 180   // ① 릴리스 지점 → 매칭 바닥패로 미끄러짐
private const val PLAY_HOLD_MS = 180    // ② '탁' 붙은 뒤 짧은 정지
private const val PLAY_FLY_MS = 280     // ③ 획득 시 낸 카드+먹힌 패가 획득 더미로 비행
private const val PLAY_HOLD_END = PLAY_SLIDE_MS + PLAY_HOLD_MS          // 360
private const val PLAY_FLY_END = PLAY_HOLD_END + PLAY_FLY_MS            // 640
// 바닥 카드 높이 추정(FloorBand cardH = maxHeight * 0.46f). 매칭 rect가 없을 때만 폴백.
private const val FLOOR_CARD_H_RATIO = 0.46f

private val RedFlash = Color(0xFFEF4444)
private val OrangeFlash = Color(0xFFF97316)
private val GoldGlow = Color(0xFFFBBF24)

/** 1회성 시각 효과 (이벤트에서 생성 → 시간 경과 후 자동 소멸) */
sealed interface TransientEffect {
    val id: Long
}

private data class ToastFx(override val id: Long, val text: String, val color: Color, val anchor: Offset?) : TransientEffect
private data class BurstFx(override val id: Long, val text: String, val anchor: Offset) : TransientEffect
private data class BannerFx(override val id: Long, val text: String) : TransientEffect
private data class PiFlightFx(override val id: Long, val from: Offset, val to: Offset) : TransientEffect

/**
 * 실제 카드 페이스가 나는 비행 연출(카드 플레이). [keyframes]는 (시각ms, root위치) 목록으로,
 * 현재 시각을 감싸는 두 키프레임 사이를 보간해 배치한다. PiFlightFx 패턴 확장(그 쪽은 피 마커).
 * board와 같은 패키지에서 enqueue하므로 internal.
 */
internal data class CardFlightFx(
    override val id: Long,
    val image: ImageBitmap?,
    val keyframes: List<Pair<Int, Offset>>,
    val totalMs: Int,
    val widthPx: Float,
    val heightPx: Float,
) : TransientEffect

/** 오버레이 효과 상태(단일 소유자: board 최상위 remember). */
class GameEffectsState {
    val transients = mutableStateListOf<TransientEffect>()
    val flashAlpha = Animatable(0f)
    var flashColor by mutableStateOf(RedFlash)
    val shake = Animatable(0f)
    val goGlow = Animatable(0f)
    var layoutSize by mutableStateOf(IntSize.Zero)
    private var seq = 0L

    fun nextId(): Long = seq++

    fun flash(scope: CoroutineScope, color: Color, peak: Float) {
        flashColor = color
        scope.launch {
            flashAlpha.snapTo(peak)
            flashAlpha.animateTo(0f, tween(FLASH_MS))
        }
    }

    fun triggerShake(scope: CoroutineScope, intensity: Float) {
        scope.launch {
            shake.snapTo(intensity)
            shake.animateTo(0f, tween(380))
        }
    }

    fun triggerGoGlow(scope: CoroutineScope) {
        scope.launch {
            goGlow.snapTo(1f)
            goGlow.animateTo(0f, tween(GO_GLOW_MS))
        }
    }
}

@Composable
fun rememberGameEffects(): GameEffectsState = remember { GameEffectsState() }

/**
 * 손패 플레이 연출을 enqueue한다. **반드시 onAction 호출 "직전"에** 부른다
 * (onAction이 상태를 갱신하면 floorCoords가 프루닝되어 좌표가 사라지므로 순서가 중요).
 *
 * 연출: 낸 카드가 [fromCenter](릴리스 지점)에서 매칭 바닥패 위치(mid)로 미끄러져 붙고(①),
 * 짧게 멈춘 뒤(②), 획득이 발생한 경우 낸 카드 + 먹힌 바닥패들이 획득 더미로 날아간다(③).
 * 매칭이 없으면 ①만.
 *
 * 좌표가 하나라도 없으면(레이아웃 전 등) 안전하게 연출을 건너뛴다(크래시/오배치 방지).
 *
 * 범위: **손패 플레이 경로 전용**. 자동 뒤집기(FlipOnly) 캡처 연출은 이번 범위 밖.
 * 획득 캡처셋은 "같은 월" 그룹 기준 근사 — 따닥/보너스 정밀 도메인 캡처셋은 반영하지 않는다.
 */
internal fun enqueueCardFlight(
    effects: GameEffectsState,
    floorCoords: FloorCoordinates,
    uiState: GameUiState,
    card: Card,
    choice: Card?,
    fromCenter: Offset,
    cardImages: Map<String, ImageBitmap?>,
) {
    // 매칭 대상: 명시 선택(choice) 우선, 없으면 같은 월 단일 카드.
    val floorSameMonth = uiState.floor.filter { it.month == card.month }
    val matchCard = choice ?: floorSameMonth.singleOrNull()

    // 착지 지점 mid: 매칭 카드 rect 중심 → 월 그룹 중심 → 바닥 영역 중심. 없으면 skip.
    val mid = matchCard?.let { floorCoords.cardRects[it.id]?.center }
        ?: floorCoords.groupCenters[card.month]
        ?: floorCoords.region?.center
        ?: return

    // 착지 카드 크기(px): 매칭 rect size → 바닥 카드 크기 추정.
    val matchRect = matchCard?.let { floorCoords.cardRects[it.id] }
    val landW = matchRect?.size?.width
        ?: floorCoords.region?.let { it.height * FLOOR_CARD_H_RATIO * CARD_ASPECT }
        ?: return
    val landH = matchRect?.size?.height ?: (landW / CARD_ASPECT)

    // 획득 여부: 같은 월 바닥패가 하나라도 있으면 획득으로 간주(월 그룹 근사).
    val captured = floorSameMonth.isNotEmpty()
    val stripCenter = floorCoords.capturedStripCenters[uiState.activePlayer]
    val willFly = captured && stripCenter != null

    // 낸 카드: [릴리스→mid(미끄러짐)] → [mid 홀드] → (획득 시) [mid→더미(비행)]
    val playedKeyframes = buildList {
        add(0 to fromCenter)
        add(PLAY_SLIDE_MS to mid)
        add(PLAY_HOLD_END to mid)
        if (willFly) add(PLAY_FLY_END to stripCenter!!)
    }
    effects.transients.add(
        CardFlightFx(
            id = effects.nextId(),
            image = cardImages[card.id],
            keyframes = playedKeyframes,
            totalMs = if (willFly) PLAY_FLY_END else PLAY_HOLD_END,
            widthPx = landW,
            heightPx = landH,
        ),
    )

    // 먹힌 바닥패들: 붙기+홀드 동안 제자리 → 이후 더미로 비행.
    if (willFly) {
        floorSameMonth.forEach { floorCard ->
            val rect = floorCoords.cardRects[floorCard.id]
            val pos = rect?.center ?: mid
            effects.transients.add(
                CardFlightFx(
                    id = effects.nextId(),
                    image = cardImages[floorCard.id],
                    keyframes = listOf(0 to pos, PLAY_HOLD_END to pos, PLAY_FLY_END to stripCenter!!),
                    totalMs = PLAY_FLY_END,
                    widthPx = rect?.size?.width ?: landW,
                    heightPx = rect?.size?.height ?: landH,
                ),
            )
        }
    }
}

/**
 * 이벤트→시각 반응 오버레이 (PLAN-phase2 §7). `events`(1회성)만 구독해 비파괴로 얹는다.
 * 지속 정보(뻑 묶임 등)는 [GameUiState]/[FloorCoordinates]에서 그린다(상태 vs 이벤트 분리).
 */
@Composable
fun BoardEffectsLayer(
    events: Flow<GameEvent>,
    uiState: GameUiState,
    floorCoords: FloorCoordinates,
    effects: GameEffectsState,
    onAction: (PlayerAction) -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(events) {
        events.collect { event ->
            val size = effects.layoutSize
            val center = Offset(size.width / 2f, size.height * 0.5f)
            fun monthAnchor(month: com.flowercards.domain.model.Month) =
                floorCoords.groupCenters[month] ?: center
            fun stripCenter(player: PlayerId) =
                Offset(size.width / 2f, if (player == PlayerId.P1) size.height * 0.66f else size.height * 0.16f)

            when (event) {
                is GameEvent.Ppeok -> {
                    effects.flash(scope, RedFlash, 0.45f)
                    effects.triggerShake(scope, 1f)
                    effects.transients.add(ToastFx(effects.nextId(), "뻑", RedFlash, monthAnchor(event.month)))
                }
                is GameEvent.JaPpeok -> {
                    effects.flash(scope, OrangeFlash, 0.65f)
                    effects.triggerShake(scope, 1.4f)
                    effects.transients.add(ToastFx(effects.nextId(), "자뻑!", OrangeFlash, monthAnchor(event.month)))
                }
                is GameEvent.Jjok ->
                    effects.transients.add(ToastFx(effects.nextId(), "쪽!", Color.White, center))
                is GameEvent.Ttadak ->
                    effects.transients.add(BurstFx(effects.nextId(), "따닥!", center))
                is GameEvent.Ttajo ->
                    effects.transients.add(BurstFx(effects.nextId(), "따조!", monthAnchor(event.month)))
                is GameEvent.Sweep ->
                    effects.transients.add(BannerFx(effects.nextId(), "싹쓸이"))
                is GameEvent.PiStolen ->
                    effects.transients.add(PiFlightFx(effects.nextId(), stripCenter(event.from), stripCenter(event.to)))
                is GameEvent.Shake ->
                    effects.transients.add(ToastFx(effects.nextId(), "흔들기!", GoldGlow, null))
                is GameEvent.Bomb -> {
                    effects.triggerShake(scope, 1.2f)
                    effects.transients.add(ToastFx(effects.nextId(), "폭탄!", GoldGlow, monthAnchor(event.month)))
                }
                is GameEvent.GoDeclared -> effects.triggerGoGlow(scope)
                is GameEvent.Chongtong ->
                    effects.transients.add(ToastFx(effects.nextId(), "총통!", GoldGlow, center))
                GameEvent.Nagari ->
                    effects.transients.add(BannerFx(effects.nextId(), "나가리"))
                // 상태로 처리되는 이벤트는 트랜지언트 없음
                is GameEvent.CardPlayed, is GameEvent.CardFlipped, is GameEvent.Captured,
                is GameEvent.GoStopChoice, is GameEvent.Stopped, is GameEvent.GameEnded -> Unit
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { effects.layoutSize = it.size },
    ) {
        // 붉은/주황 플래시 (뻑/자뻑)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = effects.flashAlpha.value }
                .background(effects.flashColor),
        )
        // 고 선언 골드 글로우 (가장자리)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = effects.goGlow.value }
                .border(10.dp, GoldGlow),
        )
        // 1회성 트랜지언트
        for (fx in effects.transients) {
            androidx.compose.runtime.key(fx.id) {
                Box(Modifier.fillMaxSize()) { // fresh BoxScope
                    TransientView(fx) { effects.transients.remove(fx) }
                }
            }
        }
        // 고/스톱 모달
        if (uiState.phase == GamePhase.AWAITING_GO_STOP && uiState.result == null) {
            GoStopModal(
                uiState = uiState,
                onGo = { onAction(PlayerAction.DeclareGo) },
                onStop = { onAction(PlayerAction.DeclareStop) },
            )
        }
        // 판 종료 3조건 수렴 → 결과 화면 (2-E)
        uiState.result?.let { ResultScreen(it, onNewGame) }
    }
}

// -------------------------------------------------------------- 트랜지언트 렌더

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TransientView(fx: TransientEffect, onDone: () -> Unit) {
    when (fx) {
        is ToastFx -> ToastView(fx.text, fx.color, fx.anchor, onDone)
        is BurstFx -> BurstView(fx.text, fx.anchor, onDone)
        is BannerFx -> BannerView(fx.text, onDone)
        is PiFlightFx -> PiFlightView(fx.from, fx.to, onDone)
        is CardFlightFx -> CardFlightView(fx, onDone)
    }
}

/**
 * 실제 카드 페이스 비행 뷰. clock(0→totalMs)을 선형 tween으로 돌리고, 현재 시각을
 * 감싸는 두 키프레임 사이를 보간(모션 구간은 ease-out 감속)해 [Modifier.offset]으로 배치한다.
 * 카드 크기는 착지(바닥) 크기로 고정 — 스펙의 손패→바닥 크기 모핑은 미구현(아래 보고 참조).
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CardFlightView(fx: CardFlightFx, onDone: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // clock 자체는 선형 — 키프레임 시각(ms)을 그대로 보존. 감속은 구간 보간에서.
        clock.animateTo(fx.totalMs.toFloat(), tween(fx.totalMs, easing = LinearEasing))
        onDone()
    }
    val t = clock.value
    val pos = interpolateKeyframes(fx.keyframes, t)
    val halfW = fx.widthPx / 2f
    val halfH = fx.heightPx / 2f
    val density = LocalDensity.current
    val wDp = with(density) { fx.widthPx.toDp() }
    val hDp = with(density) { fx.heightPx.toDp() }
    // 마지막 15%에서만 살짝 페이드(1 → 0.55) — 실제 카드 위로 자연 소멸.
    val fadeStart = fx.totalMs * 0.85f
    val alphaVal = if (t < fadeStart) {
        1f
    } else {
        (1f - 0.45f * ((t - fadeStart) / (fx.totalMs - fadeStart))).coerceIn(0f, 1f)
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .offset { IntOffset((pos.x - halfW).roundToInt(), (pos.y - halfH).roundToInt()) }
            .graphicsLayer { alpha = alphaVal }
            .size(width = wDp, height = hDp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Color.White, shape),
    ) {
        if (fx.image != null) {
            Image(
                bitmap = fx.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** 키프레임(시각ms→root위치) 목록에서 [t] 시각의 위치를 구한다. 모션 구간은 ease-out 감속. */
private fun interpolateKeyframes(keyframes: List<Pair<Int, Offset>>, t: Float): Offset {
    if (keyframes.isEmpty()) return Offset.Zero
    if (t <= keyframes.first().first) return keyframes.first().second
    if (t >= keyframes.last().first) return keyframes.last().second
    for (i in 0 until keyframes.size - 1) {
        val (t0, p0) = keyframes[i]
        val (t1, p1) = keyframes[i + 1]
        if (t >= t0 && t <= t1) {
            val span = (t1 - t0).toFloat()
            if (span <= 0f) return p1
            val fLin = (t - t0) / span
            val f = 1f - (1f - fLin) * (1f - fLin) // ease-out(감속): '탁' 붙기/착지감
            return Offset(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f)
        }
    }
    return keyframes.last().second
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ToastView(text: String, color: Color, anchor: Offset?, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(TOAST_MS))
        onDone()
    }
    val p = progress.value
    val rise = with(LocalDensity.current) { (18.dp.toPx()) } * p
    Box(
        modifier = anchorModifier(anchor)
            .graphicsLayer {
                alpha = 1f - p
                translationY = -rise
            },
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BurstView(text: String, anchor: Offset, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(BURST_MS))
        onDone()
    }
    val p = progress.value
    val scale = 0.6f + p * 1.1f
    Box(
        modifier = anchorModifier(anchor)
            .graphicsLayer {
                alpha = 1f - p
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = GoldGlow, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(2.dp))
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BannerView(text: String, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(BANNER_MS))
        onDone()
    }
    val p = progress.value
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(40.dp)
            .graphicsLayer { alpha = if (p < 0.15f) p / 0.15f else 1f - (p - 0.15f) / 0.85f }
            .background(Color(0x66FBBF24)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PiFlightView(from: Offset, to: Offset, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(PI_FLIGHT_MS))
        onDone()
    }
    val p = progress.value
    val x = from.x + (to.x - from.x) * p
    val y = from.y + (to.y - from.y) * p
    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt() - 16, y.roundToInt() - 10) }
            .graphicsLayer { alpha = 1f - p * 0.3f }
            .size(width = 20.dp, height = 30.dp)
            .background(Color(0xFF9CA3AF), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0x66000000), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("피", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** anchor(root px)가 있으면 그 지점 부근에 배치, 없으면 중앙 상단. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.anchorModifier(anchor: Offset?): Modifier =
    if (anchor != null) {
        Modifier.offset { IntOffset(anchor.x.roundToInt() - 24, anchor.y.roundToInt() - 16) }
    } else {
        Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
    }

// -------------------------------------------------------------- 고/스톱 모달

@Composable
private fun androidx.compose.foundation.layout.BoxScope.GoStopModal(
    uiState: GameUiState,
    onGo: () -> Unit,
    onStop: () -> Unit,
) {
    // 바닥 dim + 하단 입력 차단(스크림)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    )
    val goBonusHint = when {
        uiState.activeGoCount < 2 -> "다음 고: +1점"
        else -> "다음 고: ×2"
    }
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.82f)
            .background(Color(0xFF1F2937), RoundedCornerShape(16.dp))
            .border(1.dp, GoldGlow, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${uiState.activePlayer.name} — ${uiState.activeScore}점", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("현재 고 ${uiState.activeGoCount}회 · $goBonusHint", color = Color(0xFFA7F3D0), fontSize = 13.sp)
        val cap = if (uiState.activePlayer == PlayerId.P1) uiState.myCaptured else uiState.oppCaptured
        Text(
            "획득  광 ${cap.gwang} · 열 ${cap.yeol} · 띠 ${cap.tti} · 피 ${cap.pi}",
            color = Color.White,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("스톱") }
            Button(
                onClick = onGo,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlow, contentColor = Color(0xFF1F2937)),
            ) { Text("고!", fontWeight = FontWeight.Black) }
        }
    }
}

// -------------------------------------------------------------- 판 종료 결과 화면 (2-E)

/**
 * 최소 결과 화면 (PLAN-phase2 §8): 승자/무승부 + 최종 점수 내역 + [다시하기].
 * 카타르시스 연출·타이틀·일시정지는 Phase 5 범위 — 여기서는 최소 정보 표시만.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ResultScreen(result: GameResult, onNewGame: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(RESULT_FADE_MS)) }

    // 결과 화면은 하단 조작을 막는다(스크림).
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    )
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.86f)
            .alpha(progress.value)
            .background(Color(0xFF1F2937), RoundedCornerShape(18.dp))
            .border(1.dp, GoldGlow, RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val title = when (result) {
            is GameResult.Win -> "${result.winner.name} 승"
            is GameResult.ChongtongWin -> "총통! ${result.winner.name} 승"
            GameResult.Nagari -> "나가리 (무승부)"
        }
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)

        when (result) {
            is GameResult.Win -> FinalScoreBreakdown(result.score)
            is GameResult.ChongtongWin ->
                Text("손패 같은 월 4장 · 즉시 ${result.score}점", color = Color(0xFFA7F3D0), fontSize = 14.sp)
            GameResult.Nagari ->
                Text("승자 없음 · 다음 판으로", color = Color(0xFFA7F3D0), fontSize = 14.sp)
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onNewGame,
            colors = ButtonDefaults.buttonColors(containerColor = GoldGlow, contentColor = Color(0xFF1F2937)),
        ) { Text("다시하기", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun FinalScoreBreakdown(fs: com.flowercards.domain.score.FinalScore) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val base = fs.base
        val parts = buildList {
            if (base.gwang > 0) add("광 ${base.gwang}")
            if (base.yeol > 0) add("열끗 ${base.yeol}")
            if (base.godori > 0) add("고도리 ${base.godori}")
            if (base.tti > 0) add("띠 ${base.tti}")
            if (base.hongDan > 0) add("홍단 ${base.hongDan}")
            if (base.cheongDan > 0) add("청단 ${base.cheongDan}")
            if (base.choDan > 0) add("초단 ${base.choDan}")
            if (base.pi > 0) add("피 ${base.pi}")
        }
        ScoreRow("기본 점수", "${base.total}점")
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" · "), color = Color(0xFF9CA3AF), fontSize = 11.sp)
        }
        if (fs.goCount > 0) ScoreRow("고 ${fs.goCount}회 적용", "${fs.afterGo}점")

        val baks = buildList {
            if (fs.piBak) add("피박")
            if (fs.gwangBak) add("광박")
            if (fs.meongBak) add("멍박")
        }
        if (baks.isNotEmpty()) ScoreRow(baks.joinToString("·") + " 배수", "×2씩")
        if (fs.shakeBombMultiplier > 1) ScoreRow("흔들기·폭탄", "×${fs.shakeBombMultiplier}")
        if (fs.goBak) ScoreRow("고박", "상대 고 후 패배")

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        ScoreRow("최종", "${fs.total}점", emphasize = true)
    }
}

@Composable
private fun ScoreRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = if (emphasize) GoldGlow else Color.White,
            fontSize = if (emphasize) 16.sp else 13.sp,
            fontWeight = if (emphasize) FontWeight.Black else FontWeight.Medium,
        )
        Text(
            value,
            color = if (emphasize) GoldGlow else Color.White,
            fontSize = if (emphasize) 16.sp else 13.sp,
            fontWeight = if (emphasize) FontWeight.Black else FontWeight.SemiBold,
        )
    }
}
