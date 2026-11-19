package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EventRepository : JpaRepository<Event, Long> {

    // [from, to) 기간과 조금이라도 겹치는 일정
    @Query(
        """
        select e from Event e
        where e.calendar.id in :calendarIds
          and e.startAt < :to
          and e.endAt > :from
        order by e.startAt, e.id
        """,
    )
    fun findOverlapping(calendarIds: Collection<Long>, from: Instant, to: Instant): List<Event>
}
