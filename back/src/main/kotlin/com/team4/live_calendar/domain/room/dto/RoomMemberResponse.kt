package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.global.publicid.PublicIds
import java.time.Instant

// 방 상세의 참여자 한 명
data class RoomMemberResponse(
    // 회원 태그(Sqids). 화면에는 "닉네임#태그"로 보인다
    val tag: String,
    val nickname: String,
    val isOwner: Boolean,
    val joinedAt: Instant,
) {
    companion object {
        fun from(member: RoomMember, room: SchedulingRoom, publicIds: PublicIds): RoomMemberResponse {
            val userId = requireNotNull(member.user.id)
            return RoomMemberResponse(
                tag = publicIds.user.encode(userId),
                nickname = member.user.nickname,
                isOwner = room.isOwner(userId),
                joinedAt = member.joinedAt,
            )
        }
    }
}
