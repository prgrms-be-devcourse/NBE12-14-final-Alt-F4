package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.global.security.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/calendars")
class SubscribeController(
    private val subscribeService: SubscribeService,
) {
    @DeleteMapping("/{calendarId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @CurrentUserId userId: Long,
        @PathVariable("calendarId") calendarId: Long,
    ) {
        subscribeService.cancel(userId, calendarId)
    }
}
