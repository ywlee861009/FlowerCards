package com.flowercards.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [computeHandSlots]는 손패 겹침/히트박스 배치를 담당하는 결정적 순수 기하 함수다
 * (`internal`이라 같은 모듈 단위 테스트에서 직접 호출한다).
 */
class GameInteractionGeometryTest {

    @Test
    fun `count 0 또는 음수, width-height 0이면 빈 리스트`() {
        assertTrue(computeHandSlots(width = 800, height = 200, count = 0, overlap = 0.4f, aspect = 0.6f, arcHeightPx = 10f).isEmpty())
        assertTrue(computeHandSlots(width = 800, height = 200, count = -1, overlap = 0.4f, aspect = 0.6f, arcHeightPx = 10f).isEmpty())
        assertTrue(computeHandSlots(width = 0, height = 200, count = 3, overlap = 0.4f, aspect = 0.6f, arcHeightPx = 10f).isEmpty())
        assertTrue(computeHandSlots(width = 800, height = 0, count = 3, overlap = 0.4f, aspect = 0.6f, arcHeightPx = 10f).isEmpty())
    }

    @Test
    fun `카드 1장이면 중앙 정렬, arc lift 없음(top=arcHeightPx)`() {
        val arcHeightPx = 10f
        val slots = computeHandSlots(width = 800, height = 200, count = 1, overlap = 0.42f, aspect = 0.6f, arcHeightPx = arcHeightPx)
        assertEquals(1, slots.size)
        val slot = slots[0]
        // 단일 카드는 t=0 → lift = arcHeightPx*(1-0) = arcHeightPx → top = arcHeightPx - arcHeightPx = 0
        assertEquals(0f, slot.top, 0.001f)
        // 가로 중앙 정렬
        assertEquals(800f / 2f, slot.centerX, 0.5f)
    }

    @Test
    fun `여러 장이면 폭이 충분할 때 overlap 비율대로 겹치고 좌우 대칭이다`() {
        val width = 1000
        val height = 300
        val count = 4
        val overlap = 0.42f
        val aspect = 0.6f
        val arcHeightPx = 10f
        val slots = computeHandSlots(width, height, count, overlap, aspect, arcHeightPx)
        assertEquals(count, slots.size)

        val cardHeight = height - arcHeightPx
        val cardWidth = cardHeight * aspect
        val expectedStep = cardWidth * (1f - overlap)

        for (i in 1 until slots.size) {
            assertEquals(expectedStep, slots[i].left - slots[i - 1].left, 0.01f)
        }

        // 중앙 기준 좌우 대칭: 첫 슬롯과 마지막 슬롯의 중심이 전체 폭 중앙에 대해 대칭
        val center = width / 2f
        assertEquals(center - slots[0].centerX, slots[count - 1].centerX - center, 0.5f)

        // 가장 가운데(양쪽 중앙에 가장 가까운) 카드가 가장 높이 들린다(top이 가장 작다) — 아치형 배치
        val minTop = slots.minOf { it.top }
        val centerIndex = (count - 1) / 2
        // count=4는 짝수라 정확히 중앙 카드가 없으므로, 최솟값이 중앙 2장 중 하나여야 한다.
        assertTrue(slots[centerIndex].top == minTop || slots[centerIndex + 1].top == minTop)
    }

    @Test
    fun `폭이 좁아 총 너비가 width를 넘으면 step을 줄여 폭 안에 맞춘다(경계)`() {
        val width = 200 // 카드 폭에 비해 매우 좁게 설정
        val height = 300
        val count = 6
        val overlap = 0.1f // overlap이 작아 정상 step이면 폭을 초과하도록 유도
        val aspect = 0.6f
        val arcHeightPx = 10f

        val slots = computeHandSlots(width, height, count, overlap, aspect, arcHeightPx)
        assertEquals(count, slots.size)

        val cardHeight = height - arcHeightPx
        val cardWidth = cardHeight * aspect
        val naiveStep = cardWidth * (1f - overlap)
        val naiveTotal = cardWidth + naiveStep * (count - 1)
        assertTrue("이 테스트 전제(폭 초과)가 성립해야 경계 분기를 검증할 수 있다", naiveTotal > width)

        // 폭 제한 분기 적용 시 마지막 슬롯의 우측 끝이 width를 벗어나지 않아야 한다(startX>=0 가정 하에).
        val last = slots.last()
        assertTrue(
            "총 폭이 width를 넘지 않아야 한다: last.left=${last.left} last.width=${last.width} width=$width",
            last.left + last.width <= width + 0.01f,
        )
        // 좁은 폭 강제 압축이므로 step은 naiveStep보다 작아야 한다.
        val actualStep = slots[1].left - slots[0].left
        assertTrue(actualStep < naiveStep)
    }

    @Test
    fun `startX는 음수가 될 수 없다(coerceAtLeast 0)`() {
        val slots = computeHandSlots(width = 50, height = 300, count = 5, overlap = 0.0f, aspect = 0.6f, arcHeightPx = 10f)
        assertTrue(slots.isNotEmpty())
        assertTrue(slots[0].left >= 0f)
    }
}
