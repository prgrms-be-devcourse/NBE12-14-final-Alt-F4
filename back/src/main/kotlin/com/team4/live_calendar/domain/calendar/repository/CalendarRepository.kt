package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CalendarRepository : JpaRepository<Calendar, Long> {

    // 캘린더 삭제: 마지막으로 캘린더 자체
    @Modifying
    @Query("delete from Calendar c where c.id = :calendarId")
    fun deleteCalendar(calendarId: Long): Int
}
