package com.juiceplan.trip

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

@Controller
class TripController(private val tripService: TripService) {

    @PostMapping("/trip")
    fun save(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): RedirectView {
        tripService.save(LocalDate.parse(startDate), LocalDate.parse(endDate))
        return RedirectView("/sources")
    }
}
