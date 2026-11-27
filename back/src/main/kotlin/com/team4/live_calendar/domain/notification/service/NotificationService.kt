
package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.notification.repository.NotificationRepository
import com.team4.live_calendar.domain.notification.dto.NotificationResponse
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {

    @Transactional
    fun save(
        user: User,
        calendar: Calendar?,
        type: NotificationType,
        params: Map<String, Any?>? = null,
        eventHistory: EventHistory? = null,
    ): Notification =
        notificationRepository.save(
            Notification.create(
                user = user,
                calendar = calendar,
                type = type,
                params = params,
                eventHistory = eventHistory,
            )
        )

    fun findAll(userId: Long, pageable: Pageable): Page<NotificationResponse> =
        notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable).map { NotificationResponse.from(it) }

    fun countUnread(userId: Long): Long = notificationRepository.countByUserIdAndIsReadFalse(userId)

    @Transactional
    fun markRead(userId: Long, notificationId: Long) {
        val notification = notificationRepository.findById(notificationId).orElse(null)
            ?: throw BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)
        if (notification.user.id != userId) throw BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)
        notification.markRead()
    }

    @Transactional
    fun markAllRead(userId: Long) {
        notificationRepository.markAllReadByUserId(userId)
    }
}
