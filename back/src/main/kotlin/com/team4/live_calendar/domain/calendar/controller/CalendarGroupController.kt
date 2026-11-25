package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.dto.CalendarGroupRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupResponse
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupWithCalendarsResponse
import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchRequest
import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchResponse
import com.team4.live_calendar.domain.calendar.service.CalendarGroupService
import com.team4.live_calendar.domain.calendar.service.SubscribeService
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

// 노션 API 명세서의 그룹 수정은 PATCH /groups 이지만, 어떤 그룹인지 알아야 해서 /groups/{id} 로 받는다
@RestController
@RequestMapping("/api/v1/groups")
class CalendarGroupController(
    private val calendarGroupService: CalendarGroupService,
    private val subscribeService: SubscribeService,
) {

    // 내 그룹과 그 안의 내 캘린더. 처음엔 그룹이 없어 빈 목록이다
    @GetMapping
    fun findMyGroups(@CurrentUserId userId: Long): List<CalendarGroupWithCalendarsResponse> =
        calendarGroupService.findMyGroups(userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CalendarGroupRequest,
    ): CalendarGroupResponse = calendarGroupService.create(userId, request)

    @PostMapping("/{groupName}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(
        @CurrentUserId userId: Long,
        @PathVariable("groupName") groupName: String,
        @Valid @RequestBody request: SubscribeBatchRequest,
    ): SubscribeBatchResponse =
        SubscribeBatchResponse(
            subscriptionIds = subscribeService.subscribeBatchFromShare(userId, groupName, request),
        )

    // 이름만 바꾼다
    @PatchMapping("/{id}")
    fun rename(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CalendarGroupRequest,
    ): CalendarGroupResponse = calendarGroupService.rename(userId, id, request)

    // 캘린더가 들어 있는 그룹은 409 GROUP_NOT_EMPTY
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ) = calendarGroupService.delete(userId, id)
}
