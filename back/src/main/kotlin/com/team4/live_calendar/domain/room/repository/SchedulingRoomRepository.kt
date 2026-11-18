package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import org.springframework.data.jpa.repository.JpaRepository

interface SchedulingRoomRepository : JpaRepository<SchedulingRoom, Long>
