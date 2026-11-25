package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.CalendarInvitation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CalendarInvitationRepository : JpaRepository<CalendarInvitation, Long> {

    // 캘린더 삭제: 이 캘린더의 초대
    @Modifying
    @Query("delete from CalendarInvitation i where i.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int
}
