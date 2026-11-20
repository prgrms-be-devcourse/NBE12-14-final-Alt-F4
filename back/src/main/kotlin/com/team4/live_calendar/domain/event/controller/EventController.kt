package com.team4.live_calendar.domain.event.controller

import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
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
    // timezone: 보는 사람 시간대 (예: Asia/Seoul). 종일 일정을 이 시간대의 날짜로 비교하며, 없으면 UTC
    @GetMapping
    fun findInRange(
        @CurrentUserId userId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime,
        @RequestParam(required = false) calendarIds: List<Long>?,
        @RequestParam(required = false) timezone: String?,
    ): List<EventResponse> = eventService.findInRange(userId, from, to, calendarIds, timezone)

    @GetMapping("/{id}")
    fun get(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ): EventResponse = eventService.get(userId, id)

    // 반복 일정은 scope 필수 (THIS: 이 일정만, ALL: 모든 반복 일정)
    // THIS는 occurrenceStart에 기간 조회 응답의 occurrenceStart(원래 회차 시작)를 그대로 보낸다
    @PatchMapping("/{id}")
    fun update(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) scope: RecurrenceScope?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) occurrenceStart: OffsetDateTime?,
        @Valid @RequestBody request: EventUpdateRequest,
    ): EventResponse = eventService.update(userId, id, request, scope, occurrenceStart)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) scope: RecurrenceScope?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) occurrenceStart: OffsetDateTime?,
    ) = eventService.delete(userId, id, scope, occurrenceStart)
}
