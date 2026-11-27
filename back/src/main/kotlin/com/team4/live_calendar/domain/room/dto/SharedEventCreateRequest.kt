package com.team4.live_calendar.domain.room.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

// 내 일정 여러 개를 한 번에 공유한다. 표시 방식은 모두 "시간만"으로 시작한다
class SharedEventCreateRequest(
    @field:NotEmpty
    @field:Size(max = 50)
    val eventIds: List<Long>,
)
