package com.team4.live_calendar.domain.share.dto

import java.time.Instant

data class ShareCalendarPreviewResponse(
    val calendarId: Long,
    val categories: List<PreviewCategory>,
    val events: List<PreviewEvent>,
) {
    data class PreviewCategory(
        val id: Long,
        val name: String,
        val color: String?,
    )

    data class PreviewEvent(
        val id: Long,
        val categoryId: Long?,
        val title: String,
        val startAt: Instant,
        val endAt: Instant,
        val isAllDay: Boolean,
        val occurrenceStart: Instant,
    )
}