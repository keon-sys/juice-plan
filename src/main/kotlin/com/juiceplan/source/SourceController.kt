package com.juiceplan.source

import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

@Controller
class SourceController(
    private val sourceService: SourceService,
    private val tripService: TripService
) {

    @GetMapping("/sources")
    fun index(model: Model): String {
        model.addAttribute("sources", sourceService.list())
        model.addAttribute("trip", tripService.current())
        model.addAttribute("placeTypes", PlaceType.entries)
        return "sources/index"
    }

    @PostMapping("/sources")
    fun create(@ModelAttribute form: SourceForm): RedirectView {
        sourceService.create(form.toInput())
        return RedirectView("/sources")
    }

    @PutMapping("/sources/{id}")
    fun update(@PathVariable id: Long, @ModelAttribute form: SourceForm): RedirectView {
        sourceService.update(id, form.toInput())
        return RedirectView("/sources")
    }

    @DeleteMapping("/sources/{id}")
    fun delete(@PathVariable id: Long): RedirectView {
        sourceService.delete(id)
        return RedirectView("/sources")
    }
}

data class SourceForm(
    val googleMapsUrl: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType,
    val durationHours: Int,
    val durationMinutesPart: Int,
    val reservationRequired: Boolean = false,
    val reservationDeadline: LocalDate?,
    val memo: String?
) {
    fun toInput() = SourceInput(
        googleMapsUrl = googleMapsUrl,
        name = name,
        latitude = latitude,
        longitude = longitude,
        placeType = placeType,
        durationHours = durationHours,
        durationMinutesPart = durationMinutesPart,
        reservationRequired = reservationRequired,
        reservationDeadline = reservationDeadline,
        memo = memo
    )
}
