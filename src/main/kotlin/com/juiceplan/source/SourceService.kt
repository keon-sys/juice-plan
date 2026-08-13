package com.juiceplan.source

import org.springframework.stereotype.Service
import java.time.LocalDate

data class SourceInput(
    val googleMapsUrl: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType,
    val durationHours: Int,
    val durationMinutesPart: Int,
    val reservationRequired: Boolean,
    val reservationDeadline: LocalDate?,
    val memo: String?
)

@Service
class SourceService(private val sourceRepository: SourceRepository) {

    fun list(): List<Source> = sourceRepository.findAll()

    fun get(id: Long): Source = sourceRepository.findById(id)
        .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $id") }

    fun create(input: SourceInput): Source {
        validate(input)
        val source = Source(
            googleMapsUrl = input.googleMapsUrl,
            name = input.name,
            latitude = input.latitude,
            longitude = input.longitude,
            placeType = input.placeType,
            durationMinutes = toDurationMinutes(input.durationHours, input.durationMinutesPart),
            reservationRequired = input.reservationRequired,
            reservationDeadline = input.reservationDeadline,
            memo = input.memo?.ifBlank { null }
        )
        return sourceRepository.save(source)
    }

    fun update(id: Long, input: SourceInput): Source {
        validate(input)
        val source = get(id)
        source.googleMapsUrl = input.googleMapsUrl
        source.name = input.name
        source.latitude = input.latitude
        source.longitude = input.longitude
        source.placeType = input.placeType
        source.durationMinutes = toDurationMinutes(input.durationHours, input.durationMinutesPart)
        source.reservationRequired = input.reservationRequired
        source.reservationDeadline = input.reservationDeadline
        source.memo = input.memo?.ifBlank { null }
        return sourceRepository.save(source)
    }

    fun delete(id: Long) {
        sourceRepository.deleteById(id)
    }

    private fun toDurationMinutes(hours: Int, minutes: Int): Int = hours * 60 + minutes

    private fun validate(input: SourceInput) {
        require(!(input.reservationRequired && input.reservationDeadline == null)) {
            "예약이 필요한 경우 예약 마감일을 입력해야 합니다."
        }
    }
}
