package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EventRepository : JpaRepository<Event, Long> {

    // [from, to) 기간과 조금이라도 겹치는 반복 없는 일정
    @Query(
        """
        select e from Event e
        where e.calendar.id in :calendarIds
          and e.rrule is null
          and e.startAt < :to
          and e.endAt > :from
        order by e.startAt, e.id
        """,
    )
    fun findSingleOverlapping(calendarIds: Collection<Long>, from: Instant, to: Instant): List<Event>

    // [from, to) 기간에 회차가 있을 수 있는 반복 일정 (recurUntil이 null이면 끝없이 반복)
    @Query(
        """
        select e from Event e
        where e.calendar.id in :calendarIds
          and e.rrule is not null
          and e.startAt < :to
          and (e.recurUntil is null or e.recurUntil > :from)
        """,
    )
    fun findRecurringCandidates(calendarIds: Collection<Long>, from: Instant, to: Instant): List<Event>

    // 캘린더 삭제: 이 캘린더의 일정 (카테고리보다 먼저)
    @Modifying
    @Query("delete from Event e where e.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int

    // 카테고리 삭제: 일정은 남기고 카테고리 칸만 비운다 (반복 일정 포함)
    // 벌크 update라 Auditing을 거치지 않아 updated_at은 바뀌지 않는다
    @Modifying
    @Query("update Event e set e.category = null where e.category.id = :categoryId")
    fun clearCategory(categoryId: Long): Int
}
