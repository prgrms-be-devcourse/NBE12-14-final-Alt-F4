package com.team4.live_calendar.global.websocket

// 실시간 동기화 신호 종류 (신호에는 내용이 없다. 받으면 프론트가 조회 API로 다시 불러온다)
enum class SyncType {
    // 캘린더 신호 (/topic/calendars/{calendarId})
    EVENTS_CHANGED,
    CALENDAR_UPDATED,
    CALENDAR_DELETED,
    CATEGORIES_CHANGED,

    // 표시 신호 (/user/queue/display, 나에게만)
    DISPLAY_CHANGED,
}
