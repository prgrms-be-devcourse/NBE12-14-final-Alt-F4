package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.room.dto.InviteLinkResponse
import com.team4.live_calendar.domain.room.dto.InvitePreviewResponse
import com.team4.live_calendar.domain.room.dto.RoomJoinRequest
import com.team4.live_calendar.domain.room.dto.RoomJoinResponse
import com.team4.live_calendar.domain.room.entity.RoomInviteLink
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.repository.RoomInviteLinkRepository
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import com.team4.live_calendar.global.security.OpaqueToken
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class RoomInviteLinkService(
    private val roomInviteLinkRepository: RoomInviteLinkRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val userRepository: UserRepository,
    private val roomAccess: RoomAccess,
    private val roomMembership: RoomMembership,
    private val publicIds: PublicIds,
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

    // 링크를 연 것만으로는 참여하지 않는다
    fun preview(userId: Long, rawToken: String): InvitePreviewResponse {
        val room = roomOfActiveLink(rawToken)
        val alreadyMember = roomMemberRepository.findCurrentMember(requireNotNull(room.id), userId) != null
        return InvitePreviewResponse.from(room, alreadyMember, publicIds)
    }

    // 이미 참여 중이면 그대로, 나갔던 회원은 다시 참여한다
    @Transactional
    fun accept(userId: Long, rawToken: String, request: RoomJoinRequest): RoomJoinResponse {
        val room = roomOfActiveLink(rawToken)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val group = roomMembership.myGroup(userId, request.groupId)
        roomMembership.join(room, user, group)
        return RoomJoinResponse(publicIds.room.encode(requireNotNull(room.id)))
    }

    // 폐기된 링크, 삭제된 방의 링크는 없는 링크와 같다
    private fun roomOfActiveLink(rawToken: String): SchedulingRoom {
        val link = roomInviteLinkRepository.findByTokenHashAndRevokedAtIsNull(OpaqueToken.hash(rawToken))
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        if (link.room.deletedAt != null) throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        return link.room
    }

}
