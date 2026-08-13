package com.juiceplan.source

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate

enum class PlaceType { RESTAURANT, ATTRACTION }

@Entity
class Source(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var googleMapsUrl: String,
    var name: String,
    var latitude: Double,
    var longitude: Double,

    @Enumerated(EnumType.STRING)
    var placeType: PlaceType,

    var durationMinutes: Int,
    var reservationRequired: Boolean,
    var reservationDeadline: LocalDate? = null,
    var memo: String? = null,

    var scheduledDate: LocalDate? = null,
    var sortOrder: Int = 0
)
