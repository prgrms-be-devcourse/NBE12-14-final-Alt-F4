package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.room.dto.RoomInvitationCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomInvitationResponse
import com.team4.live_calendar.domain.room.service.RoomInvitationService
import com.team4.live_calendar.global.security.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// 방장의 초대(/rooms/{roomId}/invitations)와 받은 사람의 조회·수락(/invitations)
@RestController
@RequestMapping("/api/v1")
class RoomInvitationController(
    private val roomInvitationService: RoomInvitationService,
) {

    @PostMapping("/rooms/{roomId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
        @RequestBody request: RoomInvitationCreateRequest,
    ): RoomInvitationResponse = roomInvitationService.invite(userId, roomId, request)
}
