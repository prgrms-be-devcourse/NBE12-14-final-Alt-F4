package com.team4.live_calendar.domain.notification.repository

import com.team4.live_calendar.domain.notification.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long>
