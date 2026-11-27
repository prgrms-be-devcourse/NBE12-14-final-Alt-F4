package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomInvitation
import com.team4.live_calendar.domain.room.entity.RoomInvitationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RoomInvitationRepository : JpaRepository<RoomInvitation, Long> {

    fun existsByRoomIdAndInviteeIdAndStatus(roomId: Long, inviteeId: Long, status: RoomInvitationStatus): Boolean

    // 내가 받은 대기 중 초대 (삭제된 방, 이미 참여 중인 방 제외). 화면에 방 이름·초대한 사람 닉네임을 쓰므로 같이 가져온다
    @Query(
        """
        select i from RoomInvitation i
        join fetch i.room r
        join fetch i.inviter
        where i.invitee.id = :userId
          and i.status = com.team4.live_calendar.domain.room.entity.RoomInvitationStatus.PENDING
          and r.deletedAt is null
          and not exists (
              select 1 from RoomMember m
              where m.room = r and m.user.id = :userId and m.leftAt is null
          )
        order by i.id desc
        """,
    )
    fun findPendingReceivedBy(userId: Long): List<RoomInvitation>
}
