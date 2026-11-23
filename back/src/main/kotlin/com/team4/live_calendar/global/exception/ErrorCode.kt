package com.team4.live_calendar.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증 · 회원
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),

    // 일정
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    CALENDAR_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더를 찾을 수 없습니다."),
    CATEGORY_NOT_IN_CALENDAR(HttpStatus.BAD_REQUEST, "일정과 같은 캘린더의 카테고리만 지정할 수 있습니다."),
    INVALID_EVENT_TIME(HttpStatus.BAD_REQUEST, "종료 일시는 시작 일시보다 빠를 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),
    INVALID_RECURRENCE(HttpStatus.BAD_REQUEST, "반복 규칙이 올바르지 않습니다."),
    RECURRENCE_SCOPE_REQUIRED(HttpStatus.BAD_REQUEST, "반복 일정은 수정·삭제 범위(scope)를 지정해야 합니다."),
    OCCURRENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "반복 일정의 해당 회차를 찾을 수 없습니다."),
}
