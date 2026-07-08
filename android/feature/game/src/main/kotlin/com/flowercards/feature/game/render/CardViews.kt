package com.flowercards.feature.game.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.flowercards.domain.model.Card
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(6.dp)
private val CardBorder = Color(0x33000000)

/** 카드 앞면. 이미지 없으면 폴백(단색 Rect + 월/종류 텍스트) — §5 폴백 규칙. */
@Composable
fun CardFace(card: Card, image: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(Color.White)
            .border(1.dp, CardBorder, CardShape),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = card.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            FallbackFace(card)
        }
    }
}

/** 카드 뒷면 (card_back.svg). 이미지 없으면 단색 폴백. */
@Composable
fun CardBack(image: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "카드 뒷면",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF7F1D1D)))
        }
    }
}

@Composable
private fun FallbackFace(card: Card) {
    val kindLabel = when (card.kind) {
        com.flowercards.domain.model.CardKind.GWANG -> "광"
        com.flowercards.domain.model.CardKind.YEOL -> "열"
        com.flowercards.domain.model.CardKind.TTI -> "띠"
        com.flowercards.domain.model.CardKind.PI -> "피"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${card.month.number}월\n$kindLabel",
            color = Color(0xFF111827),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentSize(),
        )
    }
}

/**
 * 겹침(overlap) 한 줄 배치. 손패(미세 아크)·획득 스택·상대 손패에 공용으로 쓴다.
 * 매 프레임 값이 없는 정적 배치 — Layout 한 번 측정으로 끝난다.
 *
 * @param overlap 카드 폭 중 가려지는 비율 (0=간격 최대, 1=완전 겹침)
 * @param arcHeightPx 손패 미세 아크 높이(px). 중앙이 가장 높게 들린다. 0이면 평평.
 */
@Composable
fun OverlappingRow(
    itemCount: Int,
    overlap: Float,
    modifier: Modifier = Modifier,
    aspect: Float = CARD_ASPECT,
    arcHeightPx: Float = 0f,
    item: @Composable (index: Int) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = { repeat(itemCount) { item(it) } },
    ) { measurables, constraints ->
        val fullHeight = constraints.maxHeight
        val cardHeight = (fullHeight - arcHeightPx).roundToInt().coerceAtLeast(1)
        val cardWidth = (cardHeight * aspect).roundToInt().coerceAtLeast(1)
        val childConstraints = Constraints.fixed(cardWidth, cardHeight)
        val placeables = measurables.map { it.measure(childConstraints) }
        val n = placeables.size

        var step = (cardWidth * (1f - overlap)).roundToInt().coerceAtLeast(1)
        var total = if (n == 0) 0 else cardWidth + step * (n - 1)
        // 폭 초과 시 간격을 압축해 밴드 안에 욱여넣는다
        if (n > 1 && total > constraints.maxWidth) {
            step = ((constraints.maxWidth - cardWidth) / (n - 1)).coerceAtLeast(1)
            total = cardWidth + step * (n - 1)
        }

        layout(constraints.maxWidth, fullHeight) {
            var x = ((constraints.maxWidth - total) / 2).coerceAtLeast(0)
            val center = (n - 1) / 2f
            placeables.forEachIndexed { i, placeable ->
                val t = if (center == 0f) 0f else (i - center) / center // -1..1
                val lift = ((1f - t * t) * arcHeightPx).roundToInt()
                placeable.placeRelative(x, arcHeightPx.roundToInt() - lift)
                x += step
            }
        }
    }
}
