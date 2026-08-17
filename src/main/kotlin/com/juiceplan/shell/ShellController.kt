package com.juiceplan.shell

import com.juiceplan.daynote.DayNoteService
import com.juiceplan.source.SourceService
import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

/** 셸 아래 영역이 가질 수 있는 뷰. 주소의 마지막 조각과 같다. */
private val TABS = setOf("add", "plan", "day")

private const val DEFAULT_TAB = "day"

/**
 * 앱 전체가 이 셸 하나다. `/schd/add`, `/schd/plan`, `/schd/day` 셋 다 같은 셸을 돌려주고
 * 어느 뷰로 열지만 다르다.
 *
 * 탭을 옮길 때는 클라이언트가 history.pushState 로 주소만 바꾸므로 여기까지 오지 않는다.
 * 페이지를 다시 그리면 구글맵이 통째로 재생성되기 때문이다. 이 경로들은 새로고침과
 * 북마크, 공유 링크로 들어올 때만 쓰인다.
 */
@Controller
class ShellController(
    private val sourceService: SourceService,
    private val tripService: TripService,
    private val dayNoteService: DayNoteService
) {
    @GetMapping("/schd/{tab}")
    fun shell(@PathVariable tab: String, model: Model): Any {
        if (tab !in TABS) return RedirectView("/schd/$DEFAULT_TAB")

        val trip = tripService.current()
        model.addAttribute("trip", trip)
        model.addAttribute("sources", sourceService.list())
        model.addAttribute(
            "dayNotes",
            if (trip == null) emptyMap<LocalDate, String>()
            else dayNoteService.allForRange(trip.startDate, trip.endDate)
        )
        return "shell/index"
    }

    @GetMapping("/", "/schd")
    fun root(): RedirectView = RedirectView("/schd/$DEFAULT_TAB")

    /** /schd 를 앞에 붙이기 전, 그리고 단일 셸로 합치기 전의 북마크를 살린다. */
    @GetMapping("/sources", "/add")
    fun legacyAdd(): RedirectView = RedirectView("/schd/add")

    @GetMapping("/plan")
    fun legacyPlan(): RedirectView = RedirectView("/schd/plan")

    @GetMapping("/day")
    fun legacyDay(): RedirectView = RedirectView("/schd/day")
}
