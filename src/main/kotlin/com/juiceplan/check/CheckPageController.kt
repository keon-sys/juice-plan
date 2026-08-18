package com.juiceplan.check

import com.juiceplan.nav.Nav
import com.juiceplan.nav.addNav
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView

private const val SECTION = "check"

/**
 * 체크리스트 페이지. 세 탭이 같은 페이지를 쓰고 클라이언트가 갈아끼운다.
 * 세 목록의 항목을 한 번에 실어 보내므로 탭을 옮길 때 서버로 오지 않는다.
 */
@Controller
class CheckPageController(private val checkService: CheckService) {

    @GetMapping("/check")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/check/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        model.addAttribute("items", checkService.list())
        model.addNav(SECTION, tab)
        return "check/index"
    }
}
