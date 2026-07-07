package com.flowercards.domain.model

/**
 * 화투 12개월. 구성은 docs/planning/rules.md §1 표를 따른다.
 */
enum class Month(val number: Int, val koreanName: String) {
    JANUARY(1, "송학"),
    FEBRUARY(2, "매조"),
    MARCH(3, "벚꽃"),
    APRIL(4, "흑싸리"),
    MAY(5, "난초"),
    JUNE(6, "모란"),
    JULY(7, "홍싸리"),
    AUGUST(8, "공산"),
    SEPTEMBER(9, "국진"),
    OCTOBER(10, "단풍"),
    NOVEMBER(11, "오동"),
    DECEMBER(12, "비"),
    ;

    companion object {
        fun of(number: Int): Month = entries.first { it.number == number }
    }
}

/** 카드 종류: 광·열끗·띠·피 (rules §1) */
enum class CardKind { GWANG, YEOL, TTI, PI }

/** 띠 색: 홍단·초단·청단·비띠 (rules §2) */
enum class TtiColor { HONG, CHO, CHEONG, BI }

/**
 * 화투 카드 1장.
 *
 * @param id 덱 내 유일 식별자 (예: "m02_yeol")
 * @param ttiColor [CardKind.TTI]일 때만 non-null
 * @param isGodori 고도리 구성 열끗(2월 매조·8월 기러기·10월 사슴) 여부 (rules §2)
 * @param isBiGwang 12월 비광 여부 — 3광 감점 판정에 사용 (scoring §1.1)
 */
data class Card(
    val id: String,
    val month: Month,
    val kind: CardKind,
    val ttiColor: TtiColor? = null,
    val isGodori: Boolean = false,
    val isBiGwang: Boolean = false,
) {
    init {
        require((kind == CardKind.TTI) == (ttiColor != null)) { "띠 카드만 ttiColor를 가진다: $id" }
        require(!isGodori || kind == CardKind.YEOL) { "고도리는 열끗만 가능: $id" }
        require(!isBiGwang || (kind == CardKind.GWANG && month == Month.DECEMBER)) { "비광은 12월 광만 가능: $id" }
    }
}
