package com.team4.live_calendar.domain.notification.controller

import com.team4.live_calendar.domain.notification.dto.NotificationResponse
import com.team4.live_calendar.domain.notification.dto.UnreadNotificationCountResponse
import com.team4.live_calendar.domain.notification.service.NotificationService
import com.team4.live_calendar.global.security.CurrentUserId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping
    fun findAll(
        @CurrentUserId userId: Long,
        @PageableDefault(sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<NotificationResponse> = notificationService.findAll(userId, pageable)

    @GetMapping("/unread-count")
    fun countUnread(@CurrentUserId userId: Long): UnreadNotificationCountResponse =
        UnreadNotificationCountResponse(notificationService.countUnread(userId))

    @PatchMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(
        @CurrentUserId userId: Long,
        @PathVariable notificationId: Long,
    ) = notificationService.markRead(userId, notificationId)

    @PatchMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(@CurrentUserId userId: Long) = notificationService.markAllRead(userId)
}
