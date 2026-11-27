package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.room.dto.RoomInvitationCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomInvitationResponse
import com.team4.live_calendar.domain.room.dto.RoomJoinRequest
import com.team4.live_calendar.domain.room.dto.RoomJoinResponse
import com.team4.live_calendar.domain.room.entity.RoomInvitation
import com.team4.live_calendar.domain.room.entity.RoomInvitationStatus
import com.team4.live_calendar.domain.room.repository.RoomInvitationRepository
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class RoomInvitationService(
    private val roomInvitationRepository: RoomInvitationRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val userRepository: UserRepository,
    private val roomAccess: RoomAccess,
    private val publicIds: PublicIds,
    private val roomMembership: RoomMembership,
) {

    @Transactional
    // roomId는 방 코드(Sqids), inviteeTag는 회원 태그(Sqids)
    fun invite(userId: Long, roomId: String, request: RoomInvitationCreateRequest): RoomInvitationResponse {
        val room = roomAccess.ownedRoom(roomId, userId)
        val roomPk = requireNotNull(room.id)
        // 못 읽는 태그도 없는 회원과 같은 404
        val inviteeId = publicIds.user.decode(request.inviteeTag)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val invitee = userRepository.findByIdOrNull(inviteeId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        // 나갔던 회원은 현재 참여자가 아니므로 다시 초대할 수 있다
        if (roomMemberRepository.findCurrentMember(roomPk, inviteeId) != null) {
            throw BusinessException(ErrorCode.ALREADY_ROOM_MEMBER)
        }
        if (roomInvitationRepository.existsByRoomIdAndInviteeIdAndStatus(
                roomPk,
                inviteeId,
                RoomInvitationStatus.PENDING
            )
        ) {
            throw BusinessException(ErrorCode.DUPLICATE_INVITATION)
        }

        val invitation = roomInvitationRepository.save(RoomInvitation.create(room, room.owner, invitee))
        return RoomInvitationResponse.from(invitation, publicIds)
    }

    // 알림 기능이 생기기 전까지 프론트는 이 목록으로 "받은 초대"를 보여 준다
    fun findPendingReceived(userId: Long): List<RoomInvitationResponse> =
        roomInvitationRepository.findPendingReceivedBy(userId).map { RoomInvitationResponse.from(it, publicIds) }

    @Transactional
    fun accept(userId: Long, invitationId: Long, request: RoomJoinRequest): RoomJoinResponse {
        val invitation = roomInvitationRepository.findByIdOrNull(invitationId)
        // 남에게 온 초대도 "없는 초대"로 답한다 (초대 ID를 바꿔 가며 남의 초대를 확인하지 못하게)
        if (invitation == null || invitation.invitee.id != userId || invitation.room.deletedAt != null) {
            throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        }
        val room = invitation.room
        val roomCode = publicIds.room.encode(requireNotNull(room.id))
        val group = roomMembership.myGroup(userId, request.groupId)

        when (invitation.status) {
            RoomInvitationStatus.PENDING -> invitation.accept(Instant.now())
            // 수락 버튼을 두 번 누른 경우는 성공으로. 수락 후 나간 회원은 새 초대가 필요하다
            RoomInvitationStatus.ACCEPTED ->
                if (roomMemberRepository.findCurrentMember(requireNotNull(room.id), userId) != null) {
                    return RoomJoinResponse(roomCode)
                } else {
                    throw BusinessException(ErrorCode.INVITATION_ALREADY_RESPONDED)
                }

            RoomInvitationStatus.DECLINED -> throw BusinessException(ErrorCode.INVITATION_ALREADY_RESPONDED)
        }

        roomMembership.join(room, invitation.invitee, group)
        return RoomJoinResponse(roomCode)
    }

}
