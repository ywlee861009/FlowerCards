package com.flowercards.feature.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.model.Card
import com.flowercards.domain.model.Month
import com.flowercards.feature.game.render.CARD_ASPECT
import com.flowercards.feature.game.render.CardFace
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// 손패 겹침/선택 잠정 수치(§9 리스크 #5: game-feel 확정 전까지 임시값)
private const val HAND_OVERLAP = 0.42f
private val HAND_ARC_DP = 10.dp
private val HAND_LIFT_DP = 26.dp
private const val HAND_LIFT_SCALE = 1.15f
private val HAND_HIGHLIGHT_LIFT_DP = 14.dp
private val HAND_WIGGLE_AMP_DP = 5.dp
private const val SHAKE_WIGGLE_MS = 480
private val TAP_SLOP_DP = 12.dp
// 더미 뒤집기/딜 등장 flip 연출(뒷→앞 절반 회전 리빌). 정식 2면 flip·사운드 동기화는 Phase 4.
private const val FLIP_START_DEG = 90f
private const val FLIP_MS = 200

/** 바닥 좌표 레지스트리(root 좌표계). 드래그 드롭 판정·매칭 대상·뻑 스탬프 위치에 사용. */
class FloorCoordinates {
    var region: Rect? by mutableStateOf(null)
    val cardRects = mutableStateMapOf<String, Rect>()
    /** 월 그룹 중심(뻑 스탬프·따조 버스트 등 월 단위 오버레이 앵커) */
    val groupCenters = mutableStateMapOf<Month, androidx.compose.ui.geometry.Offset>()
}

@Composable
fun rememberFloorCoordinates(): FloorCoordinates = remember { FloorCoordinates() }

private data class HandSlot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** 손패 겹침 슬롯 기하(Layout 배치 & 히트박스 판정 공용). 결정적 순수 함수. */
private fun computeHandSlots(
    width: Int,
    height: Int,
    count: Int,
    overlap: Float,
    aspect: Float,
    arcHeightPx: Float,
): List<HandSlot> {
    if (count <= 0 || width <= 0 || height <= 0) return emptyList()
    val cardHeight = (height - arcHeightPx).coerceAtLeast(1f)
    val cardWidth = cardHeight * aspect
    var step = cardWidth * (1f - overlap)
    var total = cardWidth + step * (count - 1)
    if (count > 1 && total > width) {
        step = (width - cardWidth) / (count - 1)
        total = cardWidth + step * (count - 1)
    }
    val startX = ((width - total) / 2f).coerceAtLeast(0f)
    val center = (count - 1) / 2f
    return (0 until count).map { i ->
        val t = if (center == 0f) 0f else (i - center) / center // -1..1
        val lift = (1f - t * t) * arcHeightPx // 중앙이 가장 높게
        HandSlot(left = startX + i * step, top = arcHeightPx - lift, width = cardWidth, height = cardHeight)
    }
}

/**
 * 상호작용 손패 (PLAN-phase2 §6).
 * - 터치다운: 히트박스 확장(x 최근접 슬롯) → 해당 카드 솟음+확대.
 * - 드래그: 카드가 손가락 추종(offset은 graphicsLayer=Draw에서만 읽어 recomposition 회피).
 * - 릴리스: 바닥 영역이면 스냅→[onPlay]. 짧은 탭이면 자동 대상으로 확정. 그 외(아래로 등)는 취소(스프링백).
 * - 매칭 후보 2장: 드롭 지점에 가장 가까운 바닥 카드를 [Card] floorChoice로 전달.
 */
@Composable
fun InteractiveHand(
    hand: List<Card>,
    cardImages: Map<String, ImageBitmap?>,
    enabled: Boolean,
    floorCoords: FloorCoordinates,
    floorCards: List<Card>,
    onPlay: (card: Card, floorChoice: Card?) -> Unit,
    modifier: Modifier = Modifier,
    highlightMonths: Set<Month> = emptySet(),
) {
    val density = LocalDensity.current
    val arcPx = with(density) { HAND_ARC_DP.toPx() }
    val liftPx = with(density) { HAND_LIFT_DP.toPx() }
    val tapSlopPx = with(density) { TAP_SLOP_DP.toPx() }
    val highlightLiftPx = with(density) { HAND_HIGHLIGHT_LIFT_DP.toPx() }
    val wiggleAmpPx = with(density) { HAND_WIGGLE_AMP_DP.toPx() }
    val scope = rememberCoroutineScope()

    val selected = remember { mutableStateOf<Int?>(null) }
    val drag = remember { mutableStateOf(Offset.Zero) }
    val handOrigin = remember { mutableStateOf(Offset.Zero) }

    // 흔들기 무장 시(§4.7): 해당 월 카드가 들리고, 무장 순간 좌우 흔들림 1회.
    val wiggle = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(highlightMonths) {
        if (highlightMonths.isNotEmpty()) {
            wiggle.snapTo(1f)
            wiggle.animateTo(0f, animationSpec = tween(SHAKE_WIGGLE_MS))
        } else {
            wiggle.snapTo(0f)
        }
    }

    val gestureModifier = if (enabled) {
        Modifier.pointerInput(hand, floorCards) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slots = computeHandSlots(size.width, size.height, hand.size, HAND_OVERLAP, CARD_ASPECT, arcPx)
                if (slots.isEmpty()) return@awaitEachGesture
                val idx = slots.indices.minByOrNull { abs(slots[it].centerX - down.position.x) }
                    ?: return@awaitEachGesture
                selected.value = idx
                drag.value = Offset.Zero
                down.consume()

                var totalDrag = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    totalDrag += delta
                    drag.value = drag.value + delta
                    change.consume()
                }

                val card = hand.getOrNull(idx)
                if (card == null) {
                    selected.value = null; drag.value = Offset.Zero
                    return@awaitEachGesture
                }
                val slot = slots[idx]
                val cardCenterRoot = handOrigin.value +
                    Offset(slot.centerX, slot.centerY) + drag.value + Offset(0f, -liftPx)
                val region = floorCoords.region
                when {
                    region != null && region.contains(cardCenterRoot) -> {
                        val choice = chooseFloorTarget(card, floorCards, floorCoords, cardCenterRoot)
                        selected.value = null; drag.value = Offset.Zero
                        onPlay(card, choice)
                    }
                    totalDrag.getDistance() < tapSlopPx -> {
                        selected.value = null; drag.value = Offset.Zero
                        onPlay(card, null) // 탭 = 확정(자동 대상)
                    }
                    else -> {
                        val from = drag.value
                        scope.launch {
                            animate(1f, 0f, animationSpec = spring()) { v, _ -> drag.value = from * v }
                            selected.value = null
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Layout(
        modifier = modifier
            .onGloballyPositioned { handOrigin.value = it.boundsInRoot().topLeft }
            .then(gestureModifier),
        content = {
            hand.forEachIndexed { i, card ->
                val highlighted = card.month in highlightMonths
                Box(
                    Modifier
                        .zIndex(if (selected.value == i) 1f else if (highlighted) 0.5f else 0f)
                        .graphicsLayer {
                            when {
                                selected.value == i -> {
                                    translationX = drag.value.x
                                    translationY = drag.value.y - liftPx
                                    scaleX = HAND_LIFT_SCALE
                                    scaleY = HAND_LIFT_SCALE
                                }
                                highlighted -> {
                                    val w = wiggle.value
                                    val osc = sin(w * PI * 4).toFloat() * w
                                    translationY = -highlightLiftPx
                                    translationX = osc * wiggleAmpPx
                                    rotationZ = osc * 6f
                                }
                            }
                        },
                ) {
                    CardFace(card, cardImages[card.id], Modifier.fillMaxSize())
                }
            }
        },
    ) { measurables, constraints ->
        val slots = computeHandSlots(
            constraints.maxWidth, constraints.maxHeight, measurables.size,
            HAND_OVERLAP, CARD_ASPECT, arcPx,
        )
        val placeables = measurables.mapIndexed { i, m ->
            val s = slots[i]
            m.measure(Constraints.fixed(s.width.roundToInt().coerceAtLeast(1), s.height.roundToInt().coerceAtLeast(1)))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                p.place(slots[i].left.roundToInt(), slots[i].top.roundToInt())
            }
        }
    }
}

private fun chooseFloorTarget(
    card: Card,
    floorCards: List<Card>,
    coords: FloorCoordinates,
    dropRoot: Offset,
): Card? {
    val matches = floorCards.filter { it.month == card.month }
    // 후보 2장일 때만 명시 선택 필요 (그 외는 도메인 autoPick, TurnEngine §4.1)
    if (matches.size != 2) return null
    return matches.minByOrNull { m ->
        coords.cardRects[m.id]?.center?.let { (it - dropRoot).getDistance() } ?: Float.MAX_VALUE
    }
}

/**
 * P2(hotseat) 패스앤플레이 오버레이 — 하단 손패 밴드 위에만 뜬다.
 * 좌석 고정 보드는 P2 손패를 뒷면으로 가리고, 이 오버레이가 게이트→공개→조작을 담당한다.
 * **Phase 3 격리**: P2=AI가 되면 이 컴포저블 호출만 제거하면 된다(보드 본체 불변).
 *
 * @param bandRectInRoot 하단 손패 밴드의 root 좌표 사각형(오버레이 위치·크기)
 */
@Composable
fun PassAndPlayOverlay(
    activePlayer: PlayerId,
    activeHand: List<Card>,
    cardImages: Map<String, ImageBitmap?>,
    floorCoords: FloorCoordinates,
    floorCards: List<Card>,
    canPlay: Boolean,
    bandRectInRoot: Rect,
    onPlay: (card: Card, floorChoice: Card?) -> Unit,
    modifier: Modifier = Modifier,
    highlightMonths: Set<Month> = emptySet(),
) {
    val density = LocalDensity.current
    // turn/플레이어가 바뀌면 다시 게이트로. 같은 P2 턴 내(연속 턴/고 대기)에는 공개 유지.
    var revealed by remember(activePlayer) { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(bandRectInRoot.left.roundToInt(), bandRectInRoot.top.roundToInt()) }
                .size(
                    width = with(density) { bandRectInRoot.width.toDp() },
                    height = with(density) { bandRectInRoot.height.toDp() },
                ),
        ) {
            if (!revealed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF01F2937)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${activePlayer.name} 차례", color = Color.White, fontWeight = FontWeight.Bold)
                        Button(onClick = { revealed = true }) { Text("손패 보기") }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF14532D)),
                ) {
                    Text(
                        text = "${activePlayer.name} 조작 중",
                        color = Color(0xFFA7F3D0),
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    )
                    InteractiveHand(
                        hand = activeHand,
                        cardImages = cardImages,
                        enabled = canPlay,
                        floorCoords = floorCoords,
                        floorCards = floorCards,
                        onPlay = onPlay,
                        modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
                        highlightMonths = highlightMonths,
                    )
                }
            }
        }
    }
}

/**
 * 카드 등장 flip 리빌(뒷→앞 절반 회전). 바닥에 새로 놓이는 카드(딜·뒤집기)에 적용.
 * id를 key로 최초 컴포지션에만 재생 → 이미 놓인 카드는 애니메이션하지 않는다.
 */
@Composable
fun FlipInCard(cardId: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val rotation = remember(cardId) { Animatable(FLIP_START_DEG) }
    androidx.compose.runtime.LaunchedEffect(cardId) {
        rotation.animateTo(0f, animationSpec = tween(FLIP_MS))
    }
    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotation.value
            cameraDistance = 12f * density
        },
    ) {
        content()
    }
}
