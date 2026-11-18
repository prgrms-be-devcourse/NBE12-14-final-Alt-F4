package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomMember
import org.springframework.data.jpa.repository.JpaRepository

interface RoomMemberRepository : JpaRepository<RoomMember, Long>
