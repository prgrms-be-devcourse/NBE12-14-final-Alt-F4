package com.team4.live_calendar.domain.calendar.dto

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup

// 내 그룹과 그 그룹에 둔 내 캘린더 (조회 응답). 빈 그룹은 calendars가 빈 목록
data class CalendarGroupWithCalendarsResponse(
    val id: Long,
    val name: String,
    val displayOrder: Int?,
    val calendars: List<GroupCalendarResponse>,
) {
    companion object {
        fun from(group: CalendarGroup, calendars: List<GroupCalendarResponse>): CalendarGroupWithCalendarsResponse =
            CalendarGroupWithCalendarsResponse(
                id = requireNotNull(group.id),
                name = group.name,
                displayOrder = group.displayOrder,
                calendars = calendars,
            )
    }
}
