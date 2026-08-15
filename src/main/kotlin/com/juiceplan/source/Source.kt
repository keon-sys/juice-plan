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

    // 배정된 날짜. startMinutes와 항상 함께 설정되거나 함께 null이다.
    var scheduledDate: LocalDate? = null,

    // 배정된 시작 시각. 자정 기준 분(240=04:00 ~ 1650=27:30), 30의 배수.
    // 04~28시 그리드를 다루기 위해 LocalTime 대신 정수를 쓴다 (LocalTime은 28:00을 표현할 수 없다).
    // 그리드는 28:00(1680)까지 그리지만 28:00은 아래 경계라 시작 시각이 될 수 없다.
    var startMinutes: Int? = null
)
