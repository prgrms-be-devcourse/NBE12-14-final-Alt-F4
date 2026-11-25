package com.team4.live_calendar.domain.notification.repository

import com.team4.live_calendar.domain.notification.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationRepository : JpaRepository<Notification, Long> {

    // 캘린더 삭제: 알림은 남기고 캘린더 칸만 비운다
    @Modifying
    @Query("update Notification n set n.calendar = null where n.calendar.id = :calendarId")
    fun clearCalendar(calendarId: Long): Int
}
