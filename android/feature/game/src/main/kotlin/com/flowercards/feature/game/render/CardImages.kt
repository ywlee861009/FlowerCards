package com.flowercards.feature.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.flowercards.domain.model.Deck
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlin.math.roundToInt

/** 카드 종횡비 (SVG viewBox 103.2×168.2, §4-3). width / height */
const val CARD_ASPECT: Float = 103.2f / 168.2f

/** 뒷면 에셋 id (assets/cards/card_back.svg) */
const val CARD_BACK_ID: String = "card_back"

/**
 * 프리로드 디코딩 해상도(px, 폭 기준). 화면 표시 최대 폭보다 크게 잡아 확대 시 뭉개짐 방지.
 * 49종 × 220×359×4B ≈ 15.5MB 상주 — 매 프레임 벡터 디코딩 회피가 목적 (§5).
 */
private const val CARD_DECODE_WIDTH_PX: Int = 220

/**
 * 49종(앞면 48 + 뒷면 1) SVG를 [ImageBitmap] 메모리 맵으로 프리로드한다 (PLAN-phase2 §5).
 *
 * 로딩 중에는 null을 반환하고, 완료되면 `Card.id → ImageBitmap?` 맵을 반환한다.
 * 개별 로드 실패 시 해당 id의 값은 null → 렌더 레이어가 폴백(단색 Rect + 월 텍스트)을 그린다.
 * 단일 소유자: 이 composable을 board 최상위에서 한 번만 호출한다.
 */
@Composable
fun rememberCardImages(): Map<String, ImageBitmap?>? {
    val context = LocalContext.current
    val ids = remember { Deck.standard().map { it.id } + CARD_BACK_ID }
    return produceState<Map<String, ImageBitmap?>?>(initialValue = null, context, ids) {
        value = loadCardImages(context, ids)
    }.value
}

private suspend fun loadCardImages(
    context: Context,
    ids: List<String>,
): Map<String, ImageBitmap?> = coroutineScope {
    val loader = ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .build()
    val width = CARD_DECODE_WIDTH_PX
    val height = (width / CARD_ASPECT).roundToInt()

    ids.map { id ->
        async {
            val request = ImageRequest.Builder(context)
                .data("file:///android_asset/cards/$id.svg")
                .size(Size(width, height))
                .allowHardware(false) // asImageBitmap을 위해 소프트웨어 비트맵으로
                .build()
            val result = runCatching { loader.execute(request) }.getOrNull()
            id to (result as? SuccessResult)?.drawable?.toImageBitmapOrNull()
        }
    }.awaitAll().toMap()
}

private fun Drawable.toImageBitmapOrNull(): ImageBitmap? = runCatching {
    if (this is BitmapDrawable && bitmap != null) {
        bitmap.asImageBitmap()
    } else {
        val w = intrinsicWidth.coerceAtLeast(1)
        val h = intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, w, h)
        draw(Canvas(bmp))
        bmp.asImageBitmap()
    }
}.getOrNull()
