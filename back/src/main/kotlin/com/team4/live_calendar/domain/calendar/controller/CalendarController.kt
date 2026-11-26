package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.dto.CalendarCreateRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarDisplayRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupMoveRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarResponse
import com.team4.live_calendar.domain.calendar.dto.CalendarUpdateRequest
import com.team4.live_calendar.domain.calendar.service.CalendarService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 구독 해지(DELETE /{calendarId}/subscription)는 SubscribeController가 같은 기본 경로에서 맡는다
@RestController
@RequestMapping("/api/v1/calendars")
class CalendarController(
    private val calendarService: CalendarService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CalendarCreateRequest,
    ): CalendarResponse = calendarService.create(userId, request)

    @GetMapping("/{id}")
    fun get(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ): CalendarResponse = calendarService.get(userId, id)

    // 모든 칸 보내기 (timezone만 없으면 기존 값 유지)
    @PatchMapping("/{id}")
    fun update(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CalendarUpdateRequest,
    ): CalendarResponse = calendarService.update(userId, id, request)

    // 조율방이 쓰는 캘린더는 409 CALENDAR_IN_USE_BY_ROOM
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ) = calendarService.delete(userId, id)

    // 내 화면에서 이 캘린더를 둘 그룹 바꾸기
    @PatchMapping("/{id}/group")
    fun moveGroup(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CalendarGroupMoveRequest,
    ): CalendarResponse = calendarService.moveGroup(userId, id, request)

    // 내 화면 표시 여부와 내 색 (모든 칸 보내기, color가 null이면 원본 색)
    @PatchMapping("/{id}/display")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changeDisplay(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CalendarDisplayRequest,
    ) = calendarService.changeDisplay(userId, id, request)
}
