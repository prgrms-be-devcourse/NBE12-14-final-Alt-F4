package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.Event
import org.springframework.data.jpa.repository.JpaRepository

interface EventRepository : JpaRepository<Event, Long>