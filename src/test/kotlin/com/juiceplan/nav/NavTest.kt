package com.juiceplan.nav

import com.juiceplan.check.CheckList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavTest {

    @Test
    fun `sections are ordered schd, budget, check`() {
        assertEquals(listOf("schd", "budget", "check"), Nav.SECTIONS.map { it.id })
    }

    @Test
    fun `the first section has no previous and the last has no next`() {
        assertNull(Nav.prevPath("schd"))
        assertNull(Nav.nextPath("check"))
    }

    @Test
    fun `budget sits between schd and check`() {
        assertEquals("/schd", Nav.prevPath("budget"))
        assertEquals("/check", Nav.nextPath("budget"))
    }

    @Test
    fun `arrows point at the section root, not at a tab`() {
        // 기본 탭이 무엇인지는 서버 리다이렉트가 정한다. 링크에 탭을 박으면
        // 기본 탭을 바꿀 때 여기저기 고쳐야 한다.
        assertEquals("/schd", Nav.section("schd").path)
    }

    @Test
    fun `every section's default tab is one of its own tabs`() {
        Nav.SECTIONS.forEach {
            assertTrue(it.has(it.defaultTab), "${it.id} 의 기본 탭이 탭 목록에 없다")
        }
    }

    @Test
    fun `default path joins the section and its default tab`() {
        assertEquals("/schd/day", Nav.section("schd").defaultPath())
        assertEquals("/budget/summary", Nav.section("budget").defaultPath())
        assertEquals("/check/shopping", Nav.section("check").defaultPath())
    }

    @Test
    fun `schd keeps its display order add, plan, day`() {
        assertEquals(listOf("add", "plan", "day"), Nav.section("schd").tabs.map { it.id })
    }

    @Test
    fun `an unknown section id is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) { Nav.section("nope") }
        assertThrows(IllegalArgumentException::class.java) { Nav.nextPath("nope") }
    }

    @Test
    fun `check tabs and the CheckList enum stay in step`() {
        // 목록을 하나 더할 때 다섯 군데를 함께 고쳐야 한다. 서버 쪽 둘만이라도 여기서 붙잡는다.
        assertEquals(
            CheckList.values().map { it.name },
            Nav.section("check").tabs.map { it.id.uppercase() }
        )
    }
}
