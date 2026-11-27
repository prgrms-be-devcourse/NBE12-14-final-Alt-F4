package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SchedulingRoomRepository : JpaRepository<SchedulingRoom, Long> {

    // 이 캘린더를 공통 캘린더로 쓰는 조율방이 있는지 (있으면 캘린더 삭제를 막는다)
    fun existsByCalendarId(calendarId: Long): Boolean

    // 내가 지금 참여 중인 방 (나간 방·삭제된 방 제외), 최근에 만든 방부터
    @Query(
        """
        select r from SchedulingRoom r
        join RoomMember m on m.room = r
        where m.user.id = :userId
          and m.leftAt is null
          and r.deletedAt is null
        order by r.id desc
        """,
    )
    fun findActiveRoomsOf(userId: Long): List<SchedulingRoom>
}
