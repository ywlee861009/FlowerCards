package com.flowercards.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** rules §1 구성표 검증 — AC: "48장 덱이 구성표와 정확히 일치한다" */
class DeckTest {
    private val deck = Deck.standard()

    @Test
    fun `덱은 정확히 48장이다`() {
        assertEquals(48, deck.size)
        assertEquals(48, deck.map { it.id }.toSet().size, "id는 유일해야 한다")
    }

    @Test
    fun `모든 월은 4장씩이다`() {
        val byMonth = deck.groupingBy { it.month }.eachCount()
        assertEquals(12, byMonth.size)
        assertTrue(byMonth.values.all { it == 4 })
    }

    @Test
    fun `종류별 합계 - 광5 열끗9 띠10 피24`() {
        val byKind = deck.groupingBy { it.kind }.eachCount()
        assertEquals(5, byKind[CardKind.GWANG])
        assertEquals(9, byKind[CardKind.YEOL])
        assertEquals(10, byKind[CardKind.TTI])
        assertEquals(24, byKind[CardKind.PI])
    }

    @Test
    fun `광은 1·3·8·11·12월이다`() {
        val gwangMonths = deck.filter { it.kind == CardKind.GWANG }.map { it.month.number }.sorted()
        assertEquals(listOf(1, 3, 8, 11, 12), gwangMonths)
    }

    @Test
    fun `비광은 12월 광 하나뿐이다`() {
        val biGwang = deck.filter { it.isBiGwang }
        assertEquals(1, biGwang.size)
        assertEquals(Month.DECEMBER, biGwang[0].month)
    }

    @Test
    fun `고도리는 2·8·10월 열끗 3장이다`() {
        val godori = deck.filter { it.isGodori }
        assertEquals(3, godori.size)
        assertEquals(listOf(2, 8, 10), godori.map { it.month.number }.sorted())
        assertTrue(godori.all { it.kind == CardKind.YEOL })
    }

    @Test
    fun `띠 색 구성 - 홍단 1·2·3월, 초단 4·5·7월, 청단 6·9·10월, 비띠 12월`() {
        fun months(color: TtiColor) =
            deck.filter { it.ttiColor == color }.map { it.month.number }.sorted()
        assertEquals(listOf(1, 2, 3), months(TtiColor.HONG))
        assertEquals(listOf(4, 5, 7), months(TtiColor.CHO))
        assertEquals(listOf(6, 9, 10), months(TtiColor.CHEONG))
        assertEquals(listOf(12), months(TtiColor.BI))
    }

    @Test
    fun `피 분포 - 11월 3장, 12월 1장, 나머지 월 2장`() {
        val piByMonth = deck.filter { it.kind == CardKind.PI }
            .groupingBy { it.month.number }.eachCount()
        assertEquals(3, piByMonth[11])
        assertEquals(1, piByMonth[12])
        (1..10).forEach { assertEquals(2, piByMonth[it], "${it}월 피") }
    }
}
