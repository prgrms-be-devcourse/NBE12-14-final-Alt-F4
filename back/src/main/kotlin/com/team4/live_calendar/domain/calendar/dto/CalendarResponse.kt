package com.team4.live_calendar.domain.calendar.dto

import com.team4.live_calendar.domain.calendar.entity.Subscribe

// 캘린더 하나 (생성·상세·수정·그룹 이동 응답)
// id부터 timezone까지는 원본 캘린더, role과 groupId는 요청한 사람의 이용 관계 값
// color는 원본 색상 (내 화면 색상은 GET /groups에서 보여 준다)
data class CalendarResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val color: String?,
    val timezone: String,
    val role: String,
    val groupId: Long,
) {
    companion object {
        fun from(subscribe: Subscribe): CalendarResponse {
            val calendar = subscribe.calendar
            return CalendarResponse(
                id = requireNotNull(calendar.id),
                name = calendar.name,
                description = calendar.description,
                color = calendar.color,
                timezone = calendar.timezone,
                role = subscribe.role.name,
                groupId = requireNotNull(subscribe.group.id),
            )
        }
    }
}
