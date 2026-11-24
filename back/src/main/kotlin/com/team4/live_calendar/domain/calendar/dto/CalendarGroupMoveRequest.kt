package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotNull

// 내 화면에서 캘린더를 둘 그룹 바꾸기
// Long?로 받아야 칸이 없을 때 400 INVALID_INPUT(errors에 groupId)이 난다. Long이면 JSON 변환에서 실패해 code 없이 400이 된다
class CalendarGroupMoveRequest(
    @field:NotNull
    val groupId: Long?,
)
