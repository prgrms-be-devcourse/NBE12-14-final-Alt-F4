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
    EVENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 수정했습니다. 최신 내용을 다시 불러와 주세요."),

    // 공유
    SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유 항목을 찾을 수 없습니다."),

    // 구독
    CALENDAR_RELATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 이용 중인 캘린더입니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "해지할 구독을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    PUBLIC_SHARE_CALENDAR_NOT_FOUND(HttpStatus.NOT_FOUND, "구독할 수 있는 공개 캘린더를 찾을 수 없습니다."),

    // 캘린더 그룹
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."),
    GROUP_NOT_EMPTY(HttpStatus.CONFLICT, "캘린더가 들어 있는 그룹은 삭제할 수 없습니다. 먼저 다른 그룹으로 옮겨 주세요."),

    // 캘린더
    CALENDAR_IN_USE_BY_ROOM(HttpStatus.CONFLICT, "조율방에서 쓰는 캘린더는 삭제할 수 없습니다."),

    // 카테고리
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),

    // 조율방
    // 없는 방, 삭제된 방, 참여자가 아닌 방, 나간 방 모두 이 코드 (방이 있는지 알려주지 않는다)
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "조율방을 찾을 수 없습니다."),
    NOT_ROOM_OWNER(HttpStatus.FORBIDDEN, "방장만 할 수 있습니다."),
    INVALID_ROOM_PERIOD(HttpStatus.BAD_REQUEST, "조율 기간이나 시간 범위가 올바르지 않습니다."),
    INVITE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 초대 링크입니다."),
    ALREADY_ROOM_MEMBER(HttpStatus.CONFLICT, "이미 참여 중인 회원입니다."),
    DUPLICATE_INVITATION(HttpStatus.CONFLICT, "이미 초대를 보낸 회원입니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "초대를 찾을 수 없습니다."),
    INVITATION_ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 초대입니다."),

    // 이 방 자신의 공통 캘린더(확정 일정)만 막는다. 다른 방의 확정 일정·구독 캘린더 일정은 공유할 수 있다
    EVENT_NOT_SHAREABLE(HttpStatus.BAD_REQUEST, "이 조율방의 확정 일정은 공유할 수 없습니다."),
    SHARED_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "공유한 일정을 찾을 수 없습니다."),
}
