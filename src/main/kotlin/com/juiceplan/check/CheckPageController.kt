package com.juiceplan.check

import com.juiceplan.nav.Nav
import com.juiceplan.nav.addNav
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView

private const val SECTION = "check"

/** 아직 내용이 없다. 화살표 이동과 리다이렉트 구조를 먼저 완성해 두려고 자리만 잡는다. */
@Controller
class CheckPageController {

    @GetMapping("/check")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/check/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        model.addNav(SECTION, tab)
        return "check/index"
    }
}
