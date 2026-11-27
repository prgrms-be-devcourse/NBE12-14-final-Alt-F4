package com.team4.live_calendar.global.websocket

import java.time.Instant

// 내 화면 설정(표시·숨김·내 색·그룹)이 바뀌었다는 이벤트. 커밋 뒤 그 사람의 연결에만 표시 신호를 보낸다
data class DisplayChangedEvent(
    val userId: Long,
    val occurredAt: Instant = Instant.now(),
)

// 실제로 보내는 표시 신호 (userId는 빼고 보낸다)
data class DisplaySignal(
    val type: SyncType,
    val occurredAt: Instant,
)
