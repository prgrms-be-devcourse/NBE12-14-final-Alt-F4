package com.team4.live_calendar.domain.notification.controller

import com.team4.live_calendar.domain.notification.service.NotificationSseService
import com.team4.live_calendar.global.security.CurrentUserId
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationSseController(
    private val notificationSseService: NotificationSseService,
) {

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun connect(@CurrentUserId userId: Long): SseEmitter = notificationSseService.connect(userId)
}
