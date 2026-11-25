package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import org.springframework.stereotype.Component

// 조율방 접근 판단을 한 곳에 모은다. "현재 참여자"는 left_at이 비어 있는 참여 행이다 (ERD 10절)
// roomId는 밖에서 받은 방 코드(Sqids)다. 숫자 PK는 받지 않는다
@Component
class RoomAccess(
    private val roomMemberRepository: RoomMemberRepository,
    private val publicIds: PublicIds,
) {

    // 못 읽는 코드, 없는 방, 참여자가 아닌 방 모두 같은 404 (방이 있는지 알려주지 않는다)
    fun currentMember(roomId: String, userId: Long): RoomMember {
        val id = publicIds.room.decode(roomId) ?: throw BusinessException(ErrorCode.ROOM_NOT_FOUND)
        return roomMemberRepository.findCurrentMember(id, userId)
            ?: throw BusinessException(ErrorCode.ROOM_NOT_FOUND)
    }

    // 비참여자는 404, 참여자지만 방장이 아니면 403 (참여자는 방이 있는 걸 이미 안다)
    fun ownedRoom(roomId: String, userId: Long): SchedulingRoom {
        val room = currentMember(roomId, userId).room
        if (!room.isOwner(userId)) throw BusinessException(ErrorCode.NOT_ROOM_OWNER)
        return room
    }
}