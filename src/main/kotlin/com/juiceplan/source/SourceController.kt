package com.juiceplan.source

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class SourceRequest(
    val googleMapsUrl: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType,
    val durationHours: Int,
    val durationMinutesPart: Int,
    val reservationRequired: Boolean = false,
    val reservationDeadline: LocalDate? = null,
    val memo: String? = null
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

/**
 * 셸이 페이지를 리로드하지 않아야 하므로 (리로드하면 지도가 재생성된다) 폼 전송 대신 JSON을 쓴다.
 * 저장/수정은 엔티티를 그대로 돌려준다. 클라이언트가 생성된 id를 알아야 목록에 넣고
 * 이후 수정·삭제·배정을 걸 수 있다.
 */
@RestController
class SourceController(private val sourceService: SourceService) {

    @PostMapping("/api/sources")
    fun create(@RequestBody request: SourceRequest): Source =
        sourceService.create(request.toInput())

    @PutMapping("/api/sources/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: SourceRequest): Source =
        sourceService.update(id, request.toInput())

    @DeleteMapping("/api/sources/{id}")
    fun delete(@PathVariable id: Long) {
        sourceService.delete(id)
    }
}
