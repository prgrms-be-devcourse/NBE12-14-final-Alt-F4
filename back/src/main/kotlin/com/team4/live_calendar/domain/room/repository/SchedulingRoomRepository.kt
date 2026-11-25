package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import org.springframework.data.jpa.repository.JpaRepository

interface SchedulingRoomRepository : JpaRepository<SchedulingRoom, Long> {

    // 이 캘린더를 공통 캘린더로 쓰는 조율방이 있는지 (있으면 캘린더 삭제를 막는다)
    fun existsByCalendarId(calendarId: Long): Boolean
}
