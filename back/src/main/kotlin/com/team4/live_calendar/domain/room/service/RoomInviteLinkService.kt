package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.room.dto.InviteLinkResponse
import com.team4.live_calendar.domain.room.entity.RoomInviteLink
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.repository.RoomInviteLinkRepository
import com.team4.live_calendar.global.security.OpaqueToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class RoomInviteLinkService(
    private val roomInviteLinkRepository: RoomInviteLinkRepository,
    private val roomAccess: RoomAccess,
) {

    // 발급과 재발급은 같다: 쓰던 링크를 모두 폐기하고 새 링크 하나를 만든다
    @Transactional
    // roomId는 방 코드(Sqids)
    fun issue(userId: Long, roomId: String): InviteLinkResponse {
        val room = roomAccess.ownedRoom(roomId, userId)
        revokeActive(room)

        val rawToken = OpaqueToken.generate()
        val link = roomInviteLinkRepository.save(RoomInviteLink.create(room, room.owner, OpaqueToken.hash(rawToken)))
        return InviteLinkResponse(token = rawToken, createdAt = link.createdAt)
    }

    // 폐기해도 이미 참여한 회원은 그대로다
    @Transactional
    fun revoke(userId: Long, roomId: String) {
        revokeActive(roomAccess.ownedRoom(roomId, userId))
    }

    private fun revokeActive(room: SchedulingRoom) {
        val now = Instant.now()
        roomInviteLinkRepository.findAllByRoomIdAndRevokedAtIsNull(requireNotNull(room.id)).forEach { it.revoke(now) }
    }
}
