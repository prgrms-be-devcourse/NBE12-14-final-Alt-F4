package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomInviteLink
import org.springframework.data.jpa.repository.JpaRepository

interface RoomInviteLinkRepository : JpaRepository<RoomInviteLink, Long> {
    // 재발급·폐기할 때 쓰던 링크 (보통 1개)
    fun findAllByRoomIdAndRevokedAtIsNull(roomId: Long): List<RoomInviteLink>

    fun findByTokenHashAndRevokedAtIsNull(tokenHash: String): RoomInviteLink?
    
}
