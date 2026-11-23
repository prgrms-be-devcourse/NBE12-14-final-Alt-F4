package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import org.springframework.data.jpa.repository.JpaRepository

interface CalendarGroupRepository : JpaRepository<CalendarGroup, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): CalendarGroup?
}
