package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import org.springframework.data.jpa.repository.JpaRepository

interface RoomSharedEventRepository : JpaRepository<RoomSharedEvent, Long> {

    fun deleteAllByEventId(eventId: Long)

    // 이 일정의 조율방 공유 (이 일정과 이후 수정에서 새 일정으로 복사)
    fun findAllByEventId(eventId: Long): List<RoomSharedEvent>
}
