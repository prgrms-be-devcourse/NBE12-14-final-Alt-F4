package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import org.springframework.data.jpa.repository.JpaRepository

interface RoomSharedEventRepository : JpaRepository<RoomSharedEvent, Long> {

    fun deleteAllByEventId(eventId: Long)
}
