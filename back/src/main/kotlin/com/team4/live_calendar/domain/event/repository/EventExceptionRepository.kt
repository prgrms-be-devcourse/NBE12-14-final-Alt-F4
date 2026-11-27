package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.EventException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EventExceptionRepository : JpaRepository<EventException, Long> {

    fun deleteAllByEventId(eventId: Long)

    // 이 일정과 이후: 자른 지점(recurrenceId) 이후 회차의 예외 삭제
    fun deleteAllByEventIdAndRecurrenceIdGreaterThanEqual(eventId: Long, recurrenceId: Instant)

    // 회차 하나의 예외 (recurrenceId = 원래 회차 시작). 유일 제약으로 한 줄뿐이다
    fun findByEventIdAndRecurrenceId(eventId: Long, recurrenceId: Instant): EventException?

    // 펼친 회차 시작 범위 [from, to] 안의 예외 (기간 조회에서 펼친 회차를 빼는 데 사용)
    @Query(
        """
        select x from EventException x
        where x.event.id in :eventIds
          and x.recurrenceId >= :from
          and x.recurrenceId <= :to
        """,
    )
    fun findAllInRecurrenceRange(eventIds: Collection<Long>, from: Instant, to: Instant): List<EventException>

    // [from, to) 기간과 겹치도록 바뀐 회차 (다른 날로 옮긴 회차 포함)
    @Query(
        """
        select x from EventException x
        join fetch x.event e
        where e.calendar.id in :calendarIds
          and x.type = com.team4.live_calendar.domain.event.entity.EventExceptionType.MODIFIED
          and x.startAt < :to
          and x.endAt > :from
        """,
    )
    fun findModifiedOverlapping(calendarIds: Collection<Long>, from: Instant, to: Instant): List<EventException>

    // 캘린더 삭제: 이 캘린더 일정의 회차 예외
    @Modifying
    @Query("delete from EventException x where x.event.id in (select e.id from Event e where e.calendar.id = :calendarId)")
    fun deleteAllInCalendar(calendarId: Long): Int
}
