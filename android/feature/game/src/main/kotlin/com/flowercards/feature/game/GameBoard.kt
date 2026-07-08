package com.flowercards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowercards.domain.engine.GameEvent
import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.PlayerAction
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.model.Card
import com.flowercards.domain.model.CardKind
import com.flowercards.feature.game.render.CARD_ASPECT
import com.flowercards.feature.game.render.CARD_BACK_ID
import com.flowercards.feature.game.render.CardBack
import com.flowercards.feature.game.render.CardFace
import com.flowercards.feature.game.render.OverlappingRow
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.sin

private val BoardGreen = Color(0xFF14532D)
private val MintText = Color(0xFFA7F3D0)
private val GoMarkerColor = Color(0xFFF59E0B)
private val PpeokRed = Color(0xE6B91C1C)

/** 획득 스트립 카드 겹침 비율(피가 많을 때 압축) */
private const val CAPTURED_OVERLAP = 0.5f
/** 바닥 월 그룹 내부 겹침 */
private const val FLOOR_GROUP_OVERLAP = 0.28f

/**
 * 2-D 인게임 보드 (PLAN-phase2 §4·§6·§7). 좌석 고정(P1 하단/P2 상단) + turn 기준 상호작용.
 * 이벤트→시각 반응은 [BoardEffectsLayer](최상위 오버레이)가 담당한다.
 */
@Composable
fun GameBoard(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    events: Flow<GameEvent>,
    onAction: (PlayerAction) -> Unit,
    onNewGame: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val floorCoords = rememberFloorCoordinates()
    val effects = rememberGameEffects()
    var handBandRect by remember { mutableStateOf<Rect?>(null) }
    // 흔들기 무장 상태(§4.7): 켠 뒤 해당 월 카드를 내면 declareShake=true로 전달.
    var shakeArmed by remember { mutableStateOf(false) }
    // 매칭 후보 2장(종류 다름) 선택 대기
    var pendingMatch by remember { mutableStateOf<PendingMatch?>(null) }
    val shakeAmpPx = with(LocalDensity.current) { 6.dp.toPx() }

    val playCard: (Card, Card?) -> Unit = { card, choice ->
        val declareShake = shakeArmed && card.month in uiState.activeShakeableMonths
        val matches = uiState.floor.filter { it.month == card.month }
        // 같은 월 2장 & 종류(kind)가 달라 획득/점수가 갈릴 때만 사용자 선택 팝업 (그 외는 자동)
        if (matches.size == 2 && matches.map { it.kind }.toSet().size > 1) {
            pendingMatch = PendingMatch(card, matches, declareShake)
        } else {
            onAction(PlayerAction.PlayCard(card, floorChoice = choice, declareShake = declareShake))
        }
        shakeArmed = false
    }
    val highlightMonths = if (shakeArmed) uiState.activeShakeableMonths else emptySet()

    // 새 판 진입 시 보드 로컬 상태(무장/매칭 팝업)를 초기화한다 — 잔여 무장이 새 판에 새지 않게.
    val startNewGame: () -> Unit = {
        shakeArmed = false
        pendingMatch = null
        onNewGame()
    }

    Box(modifier.fillMaxSize().background(BoardGreen)) {
        // 화면 shake(뻑/폭탄)는 보드 본체에만 적용 — 오버레이 플래시는 별개.
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = effects.shake.value
                    if (s > 0f) {
                        translationX = sin(s * PI * 8).toFloat() * shakeAmpPx * s
                        translationY = sin(s * PI * 6).toFloat() * shakeAmpPx * 0.5f * s
                    }
                },
        ) {
            HudBar(uiState, Modifier.fillMaxWidth().weight(0.06f))
            CapturedStrip(
                seat = PlayerId.P2,
                captured = uiState.oppCapturedCards,
                score = uiState.oppScore,
                goMarker = uiState.activePlayer == PlayerId.P2 && uiState.activeCanGo,
                cardImages = cardImages,
                modifier = Modifier.fillMaxWidth().weight(0.09f),
            )
            HandBackRow(
                count = uiState.oppHandCount,
                backImage = cardImages[CARD_BACK_ID],
                dim = uiState.turn == PlayerId.P2, // P2 조작 중엔 상단 마커 흐리게(초점=하단 오버레이)
                modifier = Modifier.fillMaxWidth().weight(0.08f),
            )
            FloorBand(
                uiState = uiState,
                cardImages = cardImages,
                floorCoords = floorCoords,
                modifier = Modifier.fillMaxWidth().weight(0.24f),
            )
            CapturedStrip(
                seat = PlayerId.P1,
                captured = uiState.myCapturedCards,
                score = uiState.myScore,
                goMarker = uiState.activePlayer == PlayerId.P1 && uiState.activeCanGo,
                cardImages = cardImages,
                modifier = Modifier.fillMaxWidth().weight(0.10f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.20f)
                    .onGloballyPositioned { handBandRect = it.boundsInRoot() },
            ) {
                if (uiState.turn == PlayerId.P1) {
                    InteractiveHand(
                        hand = uiState.myHand,
                        cardImages = cardImages,
                        enabled = uiState.canPlay,
                        floorCoords = floorCoords,
                        floorCards = uiState.floor,
                        onPlay = playCard,
                        modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
                        highlightMonths = highlightMonths,
                    )
                } else {
                    HandBackRow(
                        count = uiState.myHand.size,
                        backImage = cardImages[CARD_BACK_ID],
                        modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
                    )
                }
            }
            ActionBar(
                uiState = uiState,
                shakeArmed = shakeArmed,
                onToggleShake = { shakeArmed = !shakeArmed },
                onBomb = { month -> onAction(PlayerAction.PlayBomb(month)) },
                onPlayFirstCard = { uiState.activeHand.firstOrNull()?.let { onAction(PlayerAction.PlayCard(it)) } },
                onNewGame = startNewGame,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxWidth().weight(0.07f),
            )
        }

        // P2(hotseat) 조작 오버레이 — 하단 손패 밴드 위에만. Phase 3에서 이 블록만 제거.
        val rect = handBandRect
        if (uiState.turn == PlayerId.P2 && uiState.result == null && rect != null) {
            PassAndPlayOverlay(
                activePlayer = uiState.activePlayer,
                activeHand = uiState.activeHand,
                cardImages = cardImages,
                floorCoords = floorCoords,
                floorCards = uiState.floor,
                canPlay = uiState.canPlay,
                bandRectInRoot = rect,
                onPlay = playCard,
                highlightMonths = highlightMonths,
            )
        }

        // 이벤트→시각 반응(플래시/스탬프 트리거/토스트/버스트) + 고·스톱 모달 + 결과 화면.
        BoardEffectsLayer(
            events = events,
            uiState = uiState,
            floorCoords = floorCoords,
            effects = effects,
            onAction = onAction,
            onNewGame = startNewGame,
        )

        // 매칭 후보 선택 팝업(종류 다른 2장) — 선택 전까지 카드는 아직 손패에 있다(취소 안전).
        pendingMatch?.let { pm ->
            MatchChoicePopup(
                card = pm.card,
                candidates = pm.candidates,
                cardImages = cardImages,
                onPick = { picked ->
                    onAction(PlayerAction.PlayCard(pm.card, floorChoice = picked, declareShake = pm.declareShake))
                    pendingMatch = null
                },
                onCancel = { pendingMatch = null },
            )
        }
    }
}

/** 종류가 다른 바닥 2장 중 어느 것을 먹을지 사용자가 고르는 대기 상태. */
private data class PendingMatch(
    val card: Card,
    val candidates: List<Card>,
    val declareShake: Boolean,
)

@Composable
private fun MatchChoicePopup(
    card: Card,
    candidates: List<Card>,
    cardImages: Map<String, ImageBitmap?>,
    onPick: (Card) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1F2937), RoundedCornerShape(16.dp))
                .border(1.dp, GoMarkerColor, RoundedCornerShape(16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // 패널 내부 탭이 취소로 새지 않게
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "가져갈 ${card.month.koreanName}(${card.month.number}월) 선택",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                candidates.forEach { candidate ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onPick(candidate) },
                    ) {
                        CardFace(candidate, cardImages[candidate.id], Modifier.size(width = 58.dp, height = 94.dp))
                        Text(kindLabel(candidate.kind), color = MintText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: CardKind): String = when (kind) {
    CardKind.GWANG -> "광"
    CardKind.YEOL -> "열끗"
    CardKind.TTI -> "띠"
    CardKind.PI -> "피"
}

@Composable
private fun HudBar(uiState: GameUiState, modifier: Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudText("턴  ${uiState.turn.name}")
        HudText("단계  ${uiState.phase.name}")
        HudText("더미  ${uiState.pileCount}")
        uiState.result?.let { HudText("종료") }
    }
}

@Composable
private fun HudText(text: String) {
    Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

// -------------------------------------------------------------- 획득 스트립 (실제 카드 겹침)

@Composable
private fun CapturedStrip(
    seat: PlayerId,
    captured: CapturedCards,
    score: Int,
    goMarker: Boolean,
    cardImages: Map<String, ImageBitmap?>,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
        val cardH = maxHeight * 0.92f
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (goMarker) "${seat.name} ▲" else seat.name,
                color = if (goMarker) GoMarkerColor else MintText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            CategoryGroup(captured.gwang, cardImages, cardH)
            CategoryGroup(captured.yeol, cardImages, cardH)
            CategoryGroup(captured.tti, cardImages, cardH)
            CategoryGroup(captured.pi, cardImages, cardH)
            Text("${score}점", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryGroup(cards: List<Card>, cardImages: Map<String, ImageBitmap?>, cardH: Dp) {
    if (cards.isEmpty()) return
    val cardW = cardH * CARD_ASPECT
    val step = cardW * (1f - CAPTURED_OVERLAP)
    val groupWidth = cardW + step * (cards.size - 1)
    Box(Modifier.width(groupWidth).height(cardH)) {
        OverlappingRow(
            itemCount = cards.size,
            overlap = CAPTURED_OVERLAP,
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            CardFace(cards[i], cardImages[cards[i].id], Modifier)
        }
    }
}

// -------------------------------------------------------------- 손패(뒷면)

@Composable
private fun HandBackRow(count: Int, backImage: ImageBitmap?, modifier: Modifier, dim: Boolean = false) {
    OverlappingRow(
        itemCount = count,
        overlap = 0.55f,
        modifier = modifier.padding(vertical = 4.dp).alpha(if (dim) 0.35f else 1f),
    ) {
        CardBack(backImage, Modifier)
    }
}

// -------------------------------------------------------------- 바닥 (월/뻑 그룹) + 더미

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorBand(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    floorCoords: FloorCoordinates,
    modifier: Modifier,
) {
    LaunchedEffect(uiState.floor) {
        val ids = uiState.floor.mapTo(HashSet()) { it.id }
        floorCoords.cardRects.keys.retainAll(ids)
        val months = uiState.floorGroups.mapTo(HashSet()) { it.month }
        floorCoords.groupCenters.keys.retainAll(months)
    }
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { floorCoords.region = it.boundsInRoot() },
        ) {
            val cardH = maxHeight * 0.46f // 2줄 그리드 기준
            val cardW = cardH * CARD_ASPECT
            FlowRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                uiState.floorGroups.forEach { group ->
                    key(group.month) {
                        FloorGroupCell(group, cardImages, cardW, cardH, floorCoords)
                    }
                }
            }
        }
        PileStack(
            count = uiState.pileCount,
            backImage = cardImages[CARD_BACK_ID],
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

@Composable
private fun FloorGroupCell(
    group: FloorGroup,
    cardImages: Map<String, ImageBitmap?>,
    cardW: Dp,
    cardH: Dp,
    floorCoords: FloorCoordinates,
) {
    val n = group.cards.size
    val step = cardW * FLOOR_GROUP_OVERLAP
    val cellW = cardW + step * (n - 1).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .width(cellW)
            .height(cardH)
            .onGloballyPositioned { floorCoords.groupCenters[group.month] = it.boundsInRoot().center },
    ) {
        group.cards.forEachIndexed { i, card ->
            key(card.id) {
                FlipInCard(
                    cardId = card.id,
                    modifier = Modifier
                        .offset(x = step * i)
                        .size(width = cardW, height = cardH)
                        .onGloballyPositioned { floorCoords.cardRects[card.id] = it.boundsInRoot() },
                ) {
                    CardFace(card, cardImages[card.id], Modifier.fillMaxSize())
                }
            }
        }
        if (group.isPpeok) {
            PpeokStamp(Modifier.align(Alignment.Center))
        }
    }
}

/** 뻑 지속 스탬프(§4-3: 판 상태 영향 → 바닥 위 지속 표시). */
@Composable
private fun PpeokStamp(modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = -12f }
            .background(PpeokRed, shape)
            .border(1.dp, Color.White, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("뻑", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PileStack(count: Int, backImage: ImageBitmap?, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val cardH = maxHeight * 0.62f
        val cardW = cardH * CARD_ASPECT
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(width = cardW + 6.dp, height = cardH)) {
                val visible = count.coerceIn(0, 4)
                repeat(visible) { i ->
                    CardBack(
                        backImage,
                        Modifier
                            .offset(x = (i * 2).dp, y = (i * 2).dp)
                            .size(width = cardW, height = cardH),
                    )
                }
            }
            Text("×$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------- 액션바

@Composable
private fun ActionBar(
    uiState: GameUiState,
    shakeArmed: Boolean,
    onToggleShake: () -> Unit,
    onBomb: (com.flowercards.domain.model.Month) -> Unit,
    onPlayFirstCard: () -> Unit,
    onNewGame: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val awaitingPlay = uiState.phase == GamePhase.AWAITING_PLAY
    val bombMonth = uiState.activeBombableMonths.firstOrNull()
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onToggleShake,
            enabled = awaitingPlay && uiState.activeShakeableMonths.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            Text(if (shakeArmed) "흔들기✓" else "흔들기", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = { bombMonth?.let(onBomb) },
            enabled = awaitingPlay && bombMonth != null,
            modifier = Modifier.weight(1f),
        ) {
            Text("폭탄", fontSize = 11.sp)
        }
        Button(
            onClick = onPlayFirstCard,
            enabled = awaitingPlay,
            modifier = Modifier.weight(1f),
        ) {
            Text("첫장", fontSize = 11.sp)
        }
        OutlinedButton(onClick = onNewGame, modifier = Modifier.weight(1f)) {
            Text("새판", fontSize = 11.sp)
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
            Text("설정", fontSize = 11.sp)
        }
    }
}
