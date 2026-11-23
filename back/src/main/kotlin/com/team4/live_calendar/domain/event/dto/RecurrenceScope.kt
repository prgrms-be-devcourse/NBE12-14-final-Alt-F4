package com.team4.live_calendar.domain.event.dto

// 반복 일정 수정·삭제 범위. 이 일정과 이후(THIS_AND_FOLLOWING)는 다음 단계에서 추가
enum class RecurrenceScope {
    // 이 일정만 (event_exceptions에 회차 예외로 저장)
    THIS,

    // 모든 반복 일정
    ALL,
}
