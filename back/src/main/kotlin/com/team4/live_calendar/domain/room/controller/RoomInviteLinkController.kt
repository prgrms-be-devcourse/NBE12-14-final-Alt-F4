package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.room.dto.InviteLinkResponse
import com.team4.live_calendar.domain.room.service.RoomInviteLinkService
import com.team4.live_calendar.global.security.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// 방장의 링크 관리(/rooms/{roomId}/invite-links)와 받은 사람의 확인·수락(/invite-links/{token})
@RestController
@RequestMapping("/api/v1")
class RoomInviteLinkController(
    private val roomInviteLinkService: RoomInviteLinkService,
) {

    @PostMapping("/rooms/{roomId}/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
    ): InviteLinkResponse = roomInviteLinkService.issue(userId, roomId)

    @DeleteMapping("/rooms/{roomId}/invite-links")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
    ) = roomInviteLinkService.revoke(userId, roomId)
}
