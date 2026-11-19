package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.Subscribe
import org.springframework.data.jpa.repository.JpaRepository

interface SubscribeRepository : JpaRepository<Subscribe, Long> {

    fun findByUserIdAndCalendarId(userId: Long, calendarId: Long): Subscribe?

    fun findAllByUserId(userId: Long): List<Subscribe>
}
