package com.juiceplan.shell

import com.juiceplan.daynote.DayNoteService
import com.juiceplan.source.SourceService
import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

/**
 * 앱 전체가 이 셸 하나다. 지도는 한 번만 만들어지고 아래 영역(뷰)만 해시로 전환된다.
 * 페이지를 다시 그리면 구글맵이 통째로 재생성되므로 서버 라우트를 늘리지 않는다.
 */
@Controller
class ShellController(
    private val sourceService: SourceService,
    private val tripService: TripService,
    private val dayNoteService: DayNoteService
) {
    @GetMapping("/")
    fun shell(model: Model): String {
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

    /** 단일 셸로 합치기 전의 북마크를 살린다. */
    @GetMapping("/sources", "/plan", "/day")
    fun legacy(): RedirectView = RedirectView("/")
}
