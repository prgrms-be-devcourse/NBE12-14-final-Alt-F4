package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.RoomInvitation
import com.team4.live_calendar.domain.room.entity.RoomInvitationStatus
import com.team4.live_calendar.global.publicid.PublicIds
import java.time.Instant

data class RoomInvitationResponse(
    // 지정 초대 id는 숫자 그대로 (사람이 공유하지 않는 값, 홈 "설계 결정 · 방 주소")
    val id: Long,
    // 방 코드(Sqids)
    val roomId: String,
    val roomName: String,
    val inviterNickname: String,
    val status: RoomInvitationStatus,
    val createdAt: Instant,
) {
    companion object {
        fun from(invitation: RoomInvitation, publicIds: PublicIds): RoomInvitationResponse =
            RoomInvitationResponse(
                id = requireNotNull(invitation.id),
                roomId = publicIds.room.encode(requireNotNull(invitation.room.id)),
                roomName = invitation.room.name,
                inviterNickname = invitation.inviter.nickname,
                status = invitation.status,
                createdAt = invitation.createdAt,
            )
    }
}
