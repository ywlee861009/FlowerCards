package com.flowercards.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowercards.domain.engine.GamePhase
import com.flowercards.feature.game.render.CARD_ASPECT
import com.flowercards.feature.game.render.CARD_BACK_ID
import com.flowercards.feature.game.render.CardBack
import com.flowercards.feature.game.render.CardFace
import com.flowercards.feature.game.render.OverlappingRow

private val BoardGreen = Color(0xFF14532D)
private val BandBorder = Color(0x1AFFFFFF)
private val MintText = Color(0xFFA7F3D0)

// 카테고리 색 (획득 스트립 카드 부재 시 카테고리 구분용 칩)
private val GwangColor = Color(0xFFF59E0B)
private val YeolColor = Color(0xFF3B82F6)
private val TtiColor = Color(0xFFEF4444)
private val PiColor = Color(0xFF9CA3AF)

/**
 * 2-B 정적 보드 (PLAN-phase2 §4). 8밴드 Column + weight. 좌석 고정: 하단 = P1.
 * read-only 렌더 + 2-A 검증용 액션바(디버그 버튼). 실제 터치/드래그는 2-C.
 */
@Composable
fun GameBoard(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    onPlayFirstCard: () -> Unit,
    onNewGame: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BoardGreen),
    ) {
        HudBar(uiState, Modifier.fillMaxWidth().weight(0.06f))
        CapturedStrip(
            seat = "P2",
            summary = uiState.oppCaptured,
            score = uiState.oppScore,
            showGoMarker = false,
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
            modifier = Modifier.fillMaxWidth().weight(0.24f),
        )
        CapturedStrip(
            seat = "P1",
            summary = uiState.myCaptured,
            score = uiState.myScore,
            showGoMarker = uiState.canGo,
            modifier = Modifier.fillMaxWidth().weight(0.10f),
        )
        MyHandRow(
            uiState = uiState,
            cardImages = cardImages,
            modifier = Modifier.fillMaxWidth().weight(0.20f),
        )
        ActionBar(
            uiState = uiState,
            onPlayFirstCard = onPlayFirstCard,
            onNewGame = onNewGame,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth().weight(0.07f),
        )
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

// -------------------------------------------------------------- 획득 스트립

@Composable
private fun CapturedStrip(
    seat: String,
    summary: CapturedSummary,
    score: Int,
    showGoMarker: Boolean,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        val chipH = maxHeight * 0.66f
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (showGoMarker) "$seat ▲" else seat,
                color = if (showGoMarker) GwangColor else MintText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            CategoryStack("광", summary.gwang, GwangColor, chipH)
            CategoryStack("열", summary.yeol, YeolColor, chipH)
            CategoryStack("띠", summary.tti, TtiColor, chipH)
            CategoryStack("피", summary.pi, PiColor, chipH)
            Text(
                text = "${score}점",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 카테고리별 겹침 스택(색 칩) + 개수 배지. UiState는 개수만 가지므로 카드 이미지 대신 색 칩으로 표현. */
@Composable
private fun CategoryStack(label: String, count: Int, color: Color, chipH: Dp) {
    if (count <= 0) return
    val chipW = chipH * CARD_ASPECT
    val step = chipW * 0.42f
    val visible = count.coerceAtMost(6)
    val shape = RoundedCornerShape(3.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .height(chipH)
                .width(chipW + step * (visible - 1).coerceAtLeast(0)),
        ) {
            repeat(visible) { i ->
                Box(
                    modifier = Modifier
                        .offset(x = step * i)
                        .size(width = chipW, height = chipH)
                        .clip(shape)
                        .background(color)
                        .border(1.dp, Color(0x33000000), shape),
                )
            }
        }
        Text("$label$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// -------------------------------------------------------------- 손패

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

@Composable
private fun MyHandRow(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    modifier: Modifier,
) {
    val arcPx = with(LocalDensity.current) { 10.dp.toPx() }
    val hand = uiState.myHand
    OverlappingRow(
        itemCount = hand.size,
        overlap = 0.42f,
        arcHeightPx = arcPx,
        modifier = modifier.padding(vertical = 6.dp),
    ) { index ->
        CardFace(hand[index], cardImages[hand[index].id], Modifier)
    }
}

// -------------------------------------------------------------- 바닥 + 더미

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorBand(
    uiState: GameUiState,
    cardImages: Map<String, ImageBitmap?>,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val cardH = maxHeight * 0.46f // 2줄 그리드 기준
            val cardW = cardH * CARD_ASPECT
            FlowRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                uiState.floor.forEach { card ->
                    CardFace(card, cardImages[card.id], Modifier.height(cardH).width(cardW))
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
            Text(
                text = "×$count",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// -------------------------------------------------------------- 액션바 (2-A 검증용)

@Composable
private fun ActionBar(
    uiState: GameUiState,
    onPlayFirstCard: () -> Unit,
    onNewGame: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlayFirstCard,
            enabled = uiState.phase == GamePhase.AWAITING_PLAY,
            modifier = Modifier.weight(1.4f),
        ) {
            Text("턴(${uiState.turn.name}) 첫장", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onNewGame, modifier = Modifier.weight(1f)) {
            Text("새 게임", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
            Text("설정", fontSize = 12.sp)
        }
    }
}
