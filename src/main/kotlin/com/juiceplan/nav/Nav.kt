package com.juiceplan.nav

import org.springframework.ui.Model

/** 탭바에 한 칸으로 그려지는 탭. id 는 주소의 마지막 조각과 같다. */
data class NavTab(val id: String, val icon: String, val label: String)

/**
 * 화면 묶음 하나. 탭바 가운데에 자기 탭들을 그리고, 양 끝 화살표로 이웃 섹션과 오간다.
 *
 * defaultTab 을 따로 갖는 이유는 표시 순서와 기본 탭이 다를 수 있어서다. schd 는
 * add · plan · day 순으로 그리지만 처음 열리는 탭은 day 다.
 */
data class NavSection(val id: String, val defaultTab: String, val tabs: List<NavTab>) {
    val path: String get() = "/$id"

    fun defaultPath(): String = "$path/$defaultTab"

    fun has(tab: String): Boolean = tabs.any { it.id == tab }
}

/**
 * 섹션 목록은 여기 하나뿐이다. 순서가 곧 탭바 화살표의 순서다.
 *
 * 클라이언트(shell.js, budget.js)도 자기 섹션의 탭 목록을 갖고 있다. 중복이지만,
 * 그쪽은 지도를 재생성하지 않으려고 주소만 바꾸는 클라이언트 사정이라 서버 설정을
 * 내려받게 엮지 않는다.
 */
object Nav {
    val SECTIONS = listOf(
        NavSection(
            id = "schd", defaultTab = "day",
            tabs = listOf(
                NavTab("add", "📍", "장소 추가"),
                NavTab("plan", "🗓️", "동선 변경"),
                NavTab("day", "🧭", "계획 보기"),
            )
        ),
        NavSection(
            id = "budget", defaultTab = "summary",
            tabs = listOf(
                NavTab("summary", "📊", "예산 요약"),
                NavTab("list", "🧾", "지출 내역"),
            )
        ),
        NavSection(
            id = "check", defaultTab = "shopping",
            tabs = listOf(
                NavTab("shopping", "🛒", "쇼핑 목록"),
            )
        ),
    )

    fun section(id: String): NavSection =
        SECTIONS.firstOrNull { it.id == id } ?: throw IllegalArgumentException("알 수 없는 섹션: $id")

    /** 이웃이 없으면 null. 탭바는 그 자리에 눌리지 않는 화살표를 그린다. */
    fun prevPath(id: String): String? = neighbor(id, -1)

    fun nextPath(id: String): String? = neighbor(id, 1)

    private fun neighbor(id: String, step: Int): String? {
        val index = SECTIONS.indexOfFirst { it.id == id }
        require(index >= 0) { "알 수 없는 섹션: $id" }
        return SECTIONS.getOrNull(index + step)?.path
    }
}

/** 탭바 프래그먼트가 쓰는 값 네 개. 섹션 컨트롤러마다 같은 네 줄을 쓰지 않도록 묶었다. */
fun Model.addNav(sectionId: String, tab: String) {
    addAttribute("section", Nav.section(sectionId))
    addAttribute("activeTab", tab)
    addAttribute("prevPath", Nav.prevPath(sectionId))
    addAttribute("nextPath", Nav.nextPath(sectionId))
}
