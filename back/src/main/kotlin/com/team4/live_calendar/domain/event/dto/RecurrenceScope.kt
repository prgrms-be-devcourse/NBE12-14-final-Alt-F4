package com.team4.live_calendar.domain.event.dto

// 반복 일정 수정·삭제 범위
enum class RecurrenceScope {
    // 이 일정만 (event_exceptions에 회차 예외로 저장)
    THIS,

    // 이 일정과 이후 (원래 반복은 고른 회차 전에 끝내고, 수정이면 고른 회차부터 새 일정을 만든다)
    THIS_AND_FOLLOWING,

    // 모든 반복 일정
    ALL,
}
