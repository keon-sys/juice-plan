package com.juiceplan.plan

import com.juiceplan.daynote.DayNoteService
import com.juiceplan.source.SourceService
import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class PlanController(
    private val sourceService: SourceService,
    private val tripService: TripService,
    private val dayNoteService: DayNoteService
) {
    @GetMapping("/plan")
    fun index(model: Model): String {
        val trip = tripService.current()
        if (trip == null) {
            model.addAttribute("tripMissing", true)
            return "plan/index"
        }
        model.addAttribute("tripMissing", false)
        model.addAttribute("trip", trip)
        model.addAttribute("sources", sourceService.list())
        model.addAttribute("dayNotes", dayNoteService.allForRange(trip.startDate, trip.endDate))
        return "plan/index"
    }
}
