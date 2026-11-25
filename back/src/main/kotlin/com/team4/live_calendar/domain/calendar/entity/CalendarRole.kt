package com.team4.live_calendar.domain.calendar.entity

enum class CalendarRole {
    OWNER,
    EDITOR,
    VIEWER,
    SUBSCRIBER,
    ;

    companion object {
        // 일정·카테고리를 만들고 고치고 지울 수 있는 역할
        val EDITABLE_ROLES: Set<CalendarRole> = setOf(OWNER, EDITOR)
    }
}
