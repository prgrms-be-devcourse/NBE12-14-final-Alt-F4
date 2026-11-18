package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import org.springframework.data.jpa.repository.JpaRepository

interface CalendarRepository : JpaRepository<Calendar, Long>
