package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.room.dto.RoomCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomDetailResponse
import com.team4.live_calendar.domain.room.dto.RoomSummaryResponse
import com.team4.live_calendar.domain.room.service.RoomService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/rooms")
class RoomController(
    private val roomService: RoomService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: RoomCreateRequest,
    ): RoomDetailResponse = roomService.create(userId, request)

    @GetMapping
    fun findMine(@CurrentUserId userId: Long): List<RoomSummaryResponse> =
        roomService.findMine(userId)

    // roomId는 방 코드(Sqids, Crockford Base32 6자 이상)
    @GetMapping("/{roomId}")
    fun get(
        @CurrentUserId userId: Long,
        @PathVariable roomId: String,
    ): RoomDetailResponse = roomService.get(userId, roomId)

}
