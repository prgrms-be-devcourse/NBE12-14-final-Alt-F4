package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.CalendarInvitation
import org.springframework.data.jpa.repository.JpaRepository

interface CalendarInvitationRepository : JpaRepository<CalendarInvitation, Long>
