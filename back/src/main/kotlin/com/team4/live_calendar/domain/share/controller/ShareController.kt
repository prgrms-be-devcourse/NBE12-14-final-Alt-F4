package com.team4.live_calendar.domain.share.controller

import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchRequest
import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchResponse
import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.domain.share.dto.ShareCreateRequest
import com.team4.live_calendar.domain.share.dto.ShareCreateResponse
import com.team4.live_calendar.domain.share.service.ShareService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/shares")
class ShareController(
    private val shareService: ShareService,
    private val subscribeService: SubscribeService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: ShareCreateRequest,
    ): ShareCreateResponse =
        ShareCreateResponse(shareId = shareService.create(userId, request))

    @PostMapping("/{shareId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(
        @CurrentUserId userId: Long,
        @PathVariable("shareId") shareId: Long,
        @Valid @RequestBody request: SubscribeBatchRequest,
    ): SubscribeBatchResponse =
        SubscribeBatchResponse(
            subscriptionIds = subscribeService.subscribeBatchFromShare(userId, shareId, request),
        )
}
