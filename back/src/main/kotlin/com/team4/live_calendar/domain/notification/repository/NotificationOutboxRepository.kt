package com.team4.live_calendar.domain.notification.repository

import com.team4.live_calendar.domain.notification.entity.NotificationOutbox
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationOutboxRepository : JpaRepository<NotificationOutbox, Long>