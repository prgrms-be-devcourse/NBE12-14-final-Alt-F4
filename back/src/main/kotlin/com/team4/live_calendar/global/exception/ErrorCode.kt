package com.team4.live_calendar.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    // 일정
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    CALENDAR_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더를 찾을 수 없습니다."),
    CATEGORY_NOT_IN_CALENDAR(HttpStatus.BAD_REQUEST, "일정과 같은 캘린더의 카테고리만 지정할 수 있습니다."),
    INVALID_EVENT_TIME(HttpStatus.BAD_REQUEST, "종료 일시는 시작 일시보다 빠를 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),
}
