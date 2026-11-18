package com.team4.live_calendar.domain.room.entity

// 조율방에 내 일정을 보여줄 때 시간만 보일지, 캘린더 이름도 같이 보일지
enum class SharedEventDisplayMode {
    TIME_ONLY,
    CALENDAR_NAME_AND_TIME,
}
