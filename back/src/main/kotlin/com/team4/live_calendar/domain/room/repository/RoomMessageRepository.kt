package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomMessage
import org.springframework.data.jpa.repository.JpaRepository

interface RoomMessageRepository : JpaRepository<RoomMessage, Long>
