package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.Positive

data class SubscribeCreateRequest(
    @field:Positive
    val shareId: Long,

    @field:Positive
    val groupId: Long,
)