package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

class SubscribeBatchRequest(
    groupName: String,
    @field:NotEmpty
    val calendarIds: List<Long>,
) {
    @field:NotBlank
    @field:Size(max = 100)
    val groupName: String = groupName.trim()
}
