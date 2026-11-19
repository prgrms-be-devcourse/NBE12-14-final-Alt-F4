package com.team4.live_calendar.domain.event.controller

import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

// 노션 API 명세서는 /schedules 이지만, 엔티티·테이블 이름(Event)에 맞춰 /events 로 통일
@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val eventService: EventService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: EventCreateRequest,
    ): EventResponse = eventService.create(userId, request)

    // 기간 [from, to)와 겹치는 일정. 시간에 +가 들어가면 %2B로 인코딩하거나 UTC(Z)로 보낸다
    @GetMapping
    fun findInRange(
        @CurrentUserId userId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime,
        @RequestParam(required = false) calendarIds: List<Long>?,
    ): List<EventResponse> = eventService.findInRange(userId, from, to, calendarIds)

    @GetMapping("/{id}")
    fun get(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ): EventResponse = eventService.get(userId, id)

    @PatchMapping("/{id}")
    fun update(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: EventUpdateRequest,
    ): EventResponse = eventService.update(userId, id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ) = eventService.delete(userId, id)
}
