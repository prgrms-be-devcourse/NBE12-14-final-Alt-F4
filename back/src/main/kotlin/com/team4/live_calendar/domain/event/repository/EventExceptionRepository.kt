package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.EventException
import org.springframework.data.jpa.repository.JpaRepository

interface EventExceptionRepository : JpaRepository<EventException, Long> {

    fun deleteAllByEventId(eventId: Long)
}
