package com.team4.live_calendar.domain.share.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class ShareCreateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    val content: String? = null,

    @field:NotEmpty
    val calendarIds: List<Long>,

    val isPublic: Boolean = false,
)