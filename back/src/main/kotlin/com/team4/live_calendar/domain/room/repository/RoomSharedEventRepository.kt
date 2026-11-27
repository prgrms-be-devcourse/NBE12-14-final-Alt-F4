package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RoomSharedEventRepository : JpaRepository<RoomSharedEvent, Long> {

    fun deleteAllByEventId(eventId: Long)

    fun findAllByMemberId(memberId: Long): List<RoomSharedEvent>

    // 현재 참여자가 공유한 것만 (나간 회원 것은 PR7에서 지우지만 한 번 더 거른다)
    // 공유한 사람이 지금도 그 캘린더를 이용 중인 것만 (구독 해지·다른 방 나가기로 못 보게 된 캘린더의 일정은 뺀다)
    // 응답에 닉네임·캘린더 이름을 쓰므로 같이 가져온다
    @Query(
        """
        select s from RoomSharedEvent s
        join fetch s.member m
        join fetch m.user
        join fetch s.event e
        join fetch e.calendar
        where m.room.id = :roomId
          and m.leftAt is null
          and exists (
              select 1 from Subscribe sub
              where sub.user = m.user and sub.calendar = e.calendar
          )
        """,
    )
    fun findAllActiveInRoom(roomId: Long): List<RoomSharedEvent>

    // 이 일정의 조율방 공유 (이 일정과 이후 수정에서 새 일정으로 복사)
    fun findAllByEventId(eventId: Long): List<RoomSharedEvent>

    // 캘린더 삭제: 이 캘린더 일정의 조율방 공유 (다른 사람의 방에 공유한 것도 포함)
    @Modifying
    @Query("delete from RoomSharedEvent r where r.event.id in (select e.id from Event e where e.calendar.id = :calendarId)")
    fun deleteAllInCalendar(calendarId: Long): Int
}
