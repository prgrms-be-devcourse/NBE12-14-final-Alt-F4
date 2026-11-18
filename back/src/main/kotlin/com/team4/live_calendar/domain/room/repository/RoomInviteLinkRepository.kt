package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomInviteLink
import org.springframework.data.jpa.repository.JpaRepository

interface RoomInviteLinkRepository : JpaRepository<RoomInviteLink, Long>
