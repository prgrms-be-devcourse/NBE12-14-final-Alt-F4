package com.team4.live_calendar.domain.room.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime

// 조율 범위: startDate~endDate(마지막 날 포함)의 매일 dailyStartTime~dailyEndTime
// 끝이 시작보다 이르거나 같으면 다음 날로 넘어간다 (22:00~02:00, 00:00~00:00 = 하루 24시간)
// 날짜·시간은 timezone(IANA 이름, 예: Asia/Seoul) 기준 현지 값이다
class RoomCreateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    val startDate: LocalDate,

    val endDate: LocalDate,

    // "09:00" 형식
    val dailyStartTime: LocalTime,

    val dailyEndTime: LocalTime,

    @field:NotBlank
    @field:Size(max = 100)
    val timezone: String,

    // 공통 캘린더(확정 일정)를 넣을 내 그룹
    val groupId: Long,
)
