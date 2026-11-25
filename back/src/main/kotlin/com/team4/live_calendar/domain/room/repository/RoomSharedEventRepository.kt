package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RoomSharedEventRepository : JpaRepository<RoomSharedEvent, Long> {

    fun deleteAllByEventId(eventId: Long)

    // 이 일정의 조율방 공유 (이 일정과 이후 수정에서 새 일정으로 복사)
    fun findAllByEventId(eventId: Long): List<RoomSharedEvent>

    // 캘린더 삭제: 이 캘린더 일정의 조율방 공유 (다른 사람의 방에 공유한 것도 포함)
    @Modifying
    @Query("delete from RoomSharedEvent r where r.event.id in (select e.id from Event e where e.calendar.id = :calendarId)")
    fun deleteAllInCalendar(calendarId: Long): Int
}
