package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.EventOption
import org.springframework.data.jpa.repository.JpaRepository

interface EventOptionRepository : JpaRepository<EventOption, Long>
