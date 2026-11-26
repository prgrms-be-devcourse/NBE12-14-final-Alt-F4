package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.global.publicid.PublicIds
import java.time.LocalDate
import java.time.LocalTime

// 목록 화면용. 참여자 목록은 상세에서 본다
data class RoomSummaryResponse(
    // 방 코드(Sqids)
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyStartTime: LocalTime,
    val dailyEndTime: LocalTime,
    val timezone: String,
    // 확정 일정이 들어가는 공통 캘린더. 프론트가 사이드바에서 조율방 캘린더를 구분하고 방으로 이동할 때 쓴다
    val calendarId: Long,
    val isOwner: Boolean,
) {
    companion object {
        fun from(room: SchedulingRoom, userId: Long, publicIds: PublicIds): RoomSummaryResponse =
            RoomSummaryResponse(
                id = publicIds.room.encode(requireNotNull(room.id)),
                name = room.name,
                startDate = room.startDate,
                endDate = room.endDate,
                dailyStartTime = room.dailyStartTime,
                dailyEndTime = room.dailyEndTime,
                timezone = room.timezone,
                calendarId = requireNotNull(room.calendar.id),
                isOwner = room.isOwner(userId),
            )
    }
}
