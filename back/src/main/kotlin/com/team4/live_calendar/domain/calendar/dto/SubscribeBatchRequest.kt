package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class SubscribeBatchRequest(
    @field:Positive
    val shareId: Long,

    @field:NotEmpty
    val calendarIds: List<Long>,
)
