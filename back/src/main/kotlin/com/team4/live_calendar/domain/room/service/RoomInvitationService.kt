package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.room.dto.RoomInvitationCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomInvitationResponse
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

@Service
@Transactional(readOnly = true)
class RoomInvitationService(
    private val roomInvitationRepository: RoomInvitationRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val userRepository: UserRepository,
    private val roomAccess: RoomAccess,
    private val publicIds: PublicIds,
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
}
