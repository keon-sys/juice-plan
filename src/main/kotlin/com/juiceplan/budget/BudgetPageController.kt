package com.juiceplan.budget

import com.juiceplan.nav.Nav
import com.juiceplan.nav.addNav
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView

private const val SECTION = "budget"

/**
 * 예산 페이지. 두 탭이 같은 페이지를 쓰고 클라이언트가 갈아끼운다. 지도가 없으므로
 * 셸처럼 조심할 건 없지만, 탭을 옮길 때 서버로 다시 갈 이유도 없다.
 */
@Controller
class BudgetPageController {

    @GetMapping("/budget")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/budget/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        model.addNav(SECTION, tab)
        return "budget/index"
    }
}
