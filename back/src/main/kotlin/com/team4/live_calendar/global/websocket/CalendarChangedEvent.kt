package com.team4.live_calendar.global.websocket

import java.time.Instant

// 일정·캘린더·카테고리가 바뀌었다는 이벤트. 커밋 뒤 이 객체를 그대로 캘린더 신호로 보낸다
// 엔티티가 아니라 id와 값만 담는다 (커밋 뒤 리스너에서 지연 로딩을 하지 않으려고)
// from·to: 바뀐 기간. null이면 전체 (반복 일정, 캘린더·카테고리 변경)
data class CalendarChangedEvent(
    val type: SyncType,
    val calendarId: Long,
    val eventId: Long? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val occurredAt: Instant = Instant.now(),
)
