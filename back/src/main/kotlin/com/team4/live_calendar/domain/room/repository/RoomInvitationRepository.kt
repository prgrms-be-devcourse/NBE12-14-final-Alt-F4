package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomInvitation
import org.springframework.data.jpa.repository.JpaRepository

interface RoomInvitationRepository : JpaRepository<RoomInvitation, Long>
