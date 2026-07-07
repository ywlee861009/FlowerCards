package com.flowercards.domain.model

import com.flowercards.domain.model.CardKind.GWANG
import com.flowercards.domain.model.CardKind.PI
import com.flowercards.domain.model.CardKind.TTI
import com.flowercards.domain.model.CardKind.YEOL

/**
 * 표준 화투 48장 팩토리. rules.md §1 구성표와 1:1 대응한다.
 * 쌍피 없음(확정 룰 #1) — 모든 피는 1장으로 계산한다.
 */
object Deck {
    fun standard(): List<Card> = buildList {
        // 1월 송학: 광1, 홍단1, 피2
        gwang(1)
        tti(1, TtiColor.HONG)
        pi(1, 2)
        // 2월 매조: 열끗1(새·고도리), 홍단1, 피2
        yeol(2, godori = true)
        tti(2, TtiColor.HONG)
        pi(2, 2)
        // 3월 벚꽃: 광1, 홍단1, 피2
        gwang(3)
        tti(3, TtiColor.HONG)
        pi(3, 2)
        // 4월 흑싸리: 열끗1(새), 초단1, 피2
        yeol(4)
        tti(4, TtiColor.CHO)
        pi(4, 2)
        // 5월 난초: 열끗1, 초단1, 피2
        yeol(5)
        tti(5, TtiColor.CHO)
        pi(5, 2)
        // 6월 모란: 열끗1(나비), 청단1, 피2
        yeol(6)
        tti(6, TtiColor.CHEONG)
        pi(6, 2)
        // 7월 홍싸리: 열끗1(멧돼지), 초단1, 피2
        yeol(7)
        tti(7, TtiColor.CHO)
        pi(7, 2)
        // 8월 공산: 광1, 열끗1(기러기·고도리), 피2
        gwang(8)
        yeol(8, godori = true)
        pi(8, 2)
        // 9월 국진: 열끗1(술잔), 청단1, 피2 — 쌍피 없음 확정으로 국진은 열끗 고정
        yeol(9)
        tti(9, TtiColor.CHEONG)
        pi(9, 2)
        // 10월 단풍: 열끗1(사슴·고도리), 청단1, 피2
        yeol(10, godori = true)
        tti(10, TtiColor.CHEONG)
        pi(10, 2)
        // 11월 오동: 광1, 피3
        gwang(11)
        pi(11, 3)
        // 12월 비: 광1(비광), 열끗1(제비), 비띠1, 피1
        gwang(12, biGwang = true)
        yeol(12)
        tti(12, TtiColor.BI)
        pi(12, 1)
    }.also { check(it.size == 48) { "덱은 48장이어야 한다: ${it.size}" } }

    private fun MutableList<Card>.gwang(month: Int, biGwang: Boolean = false) {
        add(Card(id(month, "gwang"), Month.of(month), GWANG, isBiGwang = biGwang))
    }

    private fun MutableList<Card>.yeol(month: Int, godori: Boolean = false) {
        add(Card(id(month, "yeol"), Month.of(month), YEOL, isGodori = godori))
    }

    private fun MutableList<Card>.tti(month: Int, color: TtiColor) {
        add(Card(id(month, "tti"), Month.of(month), TTI, ttiColor = color))
    }

    private fun MutableList<Card>.pi(month: Int, count: Int) {
        repeat(count) { i -> add(Card(id(month, "pi${i + 1}"), Month.of(month), PI)) }
    }

    private fun id(month: Int, suffix: String) = "m%02d_%s".format(month, suffix)
}
