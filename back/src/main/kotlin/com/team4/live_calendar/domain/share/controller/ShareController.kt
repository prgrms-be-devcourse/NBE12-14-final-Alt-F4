package com.team4.live_calendar.domain.share.controller

import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchRequest
import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchResponse
import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.domain.share.dto.ShareCreateRequest
import com.team4.live_calendar.domain.share.dto.ShareCreateResponse
import com.team4.live_calendar.domain.share.dto.ShareDetailResponse
import com.team4.live_calendar.domain.share.dto.ShareSummaryResponse
import com.team4.live_calendar.domain.share.service.ShareService
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.security.CurrentUserId
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
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
    private val publicIds: PublicIds,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: ShareCreateRequest,
    ): ShareCreateResponse {
        val shareId = shareService.create(userId, request)
        return ShareCreateResponse(shareId = publicIds.share.encode(shareId))
    }

    @GetMapping
    fun findPublicShares(): List<ShareSummaryResponse> =
        shareService.findPublicShares()

    @GetMapping("/{shareId}")
    fun getDetail(
        @PathVariable("shareId") shareCode: String,
    ): ShareDetailResponse {
        val shareId = publicIds.share.decode(shareCode)
            ?: throw BusinessException(ErrorCode.SHARE_NOT_FOUND)

        return shareService.getDetail(shareId)
    }

    @PostMapping("/{shareId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(
        @CurrentUserId userId: Long,
        @PathVariable("shareId") shareCode: String,
        @Valid @RequestBody request: SubscribeBatchRequest,
    ): SubscribeBatchResponse {
        val shareId = publicIds.share.decode(shareCode)
            ?: throw BusinessException(ErrorCode.PUBLIC_SHARE_CALENDAR_NOT_FOUND)

        return SubscribeBatchResponse(
            subscriptionIds = subscribeService.subscribeBatchFromShare(userId, shareId, request),
        )
    }
}
