package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.dto.SubscribeCreateRequest
import com.team4.live_calendar.domain.calendar.dto.SubscribeCreateResponse
import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody


@RestController
@RequestMapping("/api/v1/calendars")
class SubscribeController(
    private val subscribeService: SubscribeService,
) {
    @PostMapping("/{calendarId}/subscription")
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(
        @CurrentUserId userId: Long,
        @PathVariable("calendarId") calendarId: Long,
        @Valid @RequestBody request: SubscribeCreateRequest,
    ): SubscribeCreateResponse {
        val subscriptionId = subscribeService.subscribeFromShare(
            userId = userId,
            shareId = request.shareId,
            calendarId = calendarId,
            groupId = request.groupId,
        )
        return SubscribeCreateResponse(subscriptionId)
    }

    @DeleteMapping("/{calendarId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @CurrentUserId userId: Long,
        @PathVariable("calendarId") calendarId: Long,
    ) {
        subscribeService.cancel(userId, calendarId)
    }
}
