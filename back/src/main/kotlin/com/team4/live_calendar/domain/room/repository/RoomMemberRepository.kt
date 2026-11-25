package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RoomMemberRepository : JpaRepository<RoomMember, Long> {

    // 나간 기록까지 포함 (재참여할 때 같은 행을 찾는다)
    fun findByRoomIdAndUserId(roomId: Long, userId: Long): RoomMember?

    // 사용 중인 방의 현재 참여 행. 방도 같이 가져온다 (쿼리 한 번)
    @Query(
        """
        select m from RoomMember m
        join fetch m.room r
        where r.id = :roomId
          and r.deletedAt is null
          and m.user.id = :userId
          and m.leftAt is null
        """,
    )
    fun findCurrentMember(roomId: Long, userId: Long): RoomMember?

    // 상세 화면의 참여자 목록. 닉네임을 쓰므로 회원을 같이 가져온다
    @Query(
        """
        select m from RoomMember m
        join fetch m.user
        where m.room.id = :roomId
          and m.leftAt is null
        order by m.joinedAt, m.id
        """,
    )
    fun findActiveMembersWithUser(roomId: Long): List<RoomMember>
}

