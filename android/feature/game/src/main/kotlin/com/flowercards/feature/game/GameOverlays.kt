package com.flowercards.feature.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    }
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
