package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.room.dto.MySharedEventResponse
import com.team4.live_calendar.domain.room.dto.SharedEventCreateRequest
import com.team4.live_calendar.domain.room.dto.SharedEventDisplayModeRequest
import com.team4.live_calendar.domain.room.dto.SharedEventResponse
import com.team4.live_calendar.domain.room.service.RoomSharedEventService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

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

    // 기간 [from, to)와 겹치는 공유 일정 (최대 93일). 시간에 +가 들어가면 %2B로 인코딩하거나 UTC(Z)로 보낸다
    @GetMapping
    fun findInRoom(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime,
    ): List<SharedEventResponse> = roomSharedEventService.findInRoom(userId, roomId, from, to)

    @PatchMapping("/{sharedEventId}")
    fun changeDisplayMode(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
        @PathVariable sharedEventId: Long,
        @RequestBody request: SharedEventDisplayModeRequest,
    ): MySharedEventResponse = roomSharedEventService.changeDisplayMode(userId, roomId, sharedEventId, request)

    @DeleteMapping("/{sharedEventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unshare(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
        @PathVariable sharedEventId: Long,
    ) = roomSharedEventService.unshare(userId, roomId, sharedEventId)
}
