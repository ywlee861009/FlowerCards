package com.flowercards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowercards.domain.engine.GamePhase
import com.flowercards.domain.engine.PlayerAction
import com.flowercards.domain.engine.PlayerId
import com.flowercards.domain.model.Card
import com.flowercards.feature.game.render.CARD_ASPECT
import com.flowercards.feature.game.render.CARD_BACK_ID
import com.flowercards.feature.game.render.CardBack
import com.flowercards.feature.game.render.CardFace
import com.flowercards.feature.game.render.OverlappingRow

private val BoardGreen = Color(0xFF14532D)
private val MintText = Color(0xFFA7F3D0)
private val GoMarkerColor = Color(0xFFF59E0B)

/** 획득 스트립 카드 겹침 비율(피가 많을 때 압축) */
private const val CAPTURED_OVERLAP = 0.5f

/**
 * 2-C 인게임 보드 (PLAN-phase2 §4·§6). 좌석 고정(P1 하단/P2 상단) + turn 기준 상호작용.
 * P1 턴엔 하단 P1 손패가 드래그 가능. P2 턴엔 [PassAndPlayOverlay]가 게이트→공개→조작을 담당.
 */
@Composable
fun GameBoard(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    onAction: (PlayerAction) -> Unit,
    onNewGame: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val floorCoords = rememberFloorCoordinates()
    var handBandRect by remember { mutableStateOf<Rect?>(null) }
    // 흔들기 무장 상태(§4.7): 켠 뒤 해당 월 카드를 내면 declareShake=true로 전달.
    var shakeArmed by remember { mutableStateOf(false) }

    val playCard: (Card, Card?) -> Unit = { card, choice ->
        val declareShake = shakeArmed && card.month in uiState.activeShakeableMonths
        onAction(PlayerAction.PlayCard(card, floorChoice = choice, declareShake = declareShake))
        shakeArmed = false
    }

    Box(modifier.fillMaxSize().background(BoardGreen)) {
        Column(Modifier.fillMaxSize()) {
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
            // 하단 손패 밴드: P1 턴엔 앞면·상호작용, P2 턴엔 뒷면으로 가림(오버레이가 조작).
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
                        enabled = uiState.canPlay && uiState.turn == PlayerId.P1,
                        floorCoords = floorCoords,
                        floorCards = uiState.floor,
                        onPlay = playCard,
                        modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
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
                onNewGame = onNewGame,
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
            )
        }
    }
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
private fun HandBackRow(count: Int, backImage: ImageBitmap?, modifier: Modifier) {
    OverlappingRow(
        itemCount = count,
        overlap = 0.55f,
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        CardBack(backImage, Modifier)
    }
}

// -------------------------------------------------------------- 바닥 + 더미

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorBand(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    floorCoords: FloorCoordinates,
    modifier: Modifier,
) {
    // 바닥에서 사라진 카드의 좌표는 정리
    LaunchedEffect(uiState.floor) {
        val ids = uiState.floor.mapTo(HashSet()) { it.id }
        floorCoords.cardRects.keys.retainAll(ids)
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                uiState.floor.forEach { card ->
                    key(card.id) {
                        FlipInCard(
                            cardId = card.id,
                            modifier = Modifier
                                .height(cardH)
                                .width(cardW)
                                .onGloballyPositioned { floorCoords.cardRects[card.id] = it.boundsInRoot() },
                        ) {
                            CardFace(card, cardImages[card.id], Modifier.fillMaxSize())
                        }
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
