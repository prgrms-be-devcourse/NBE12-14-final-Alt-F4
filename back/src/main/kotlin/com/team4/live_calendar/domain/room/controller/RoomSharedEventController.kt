package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.room.dto.MySharedEventResponse
import com.team4.live_calendar.domain.room.dto.SharedEventCreateRequest
import com.team4.live_calendar.domain.room.service.RoomSharedEventService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// 명세서의 /shared-schedules를 일정 API 이름(/events)에 맞춰 /shared-events로 쓴다
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/shared-events")
class RoomSharedEventController(
    private val roomSharedEventService: RoomSharedEventService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // roomId는 방 코드(Sqids)
    fun share(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
        @Valid @RequestBody request: SharedEventCreateRequest,
    ): List<MySharedEventResponse> = roomSharedEventService.share(userId, roomId, request)

    @DeleteMapping("/{sharedEventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unshare(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
        @PathVariable sharedEventId: Long,
    ) = roomSharedEventService.unshare(userId, roomId, sharedEventId)
}
