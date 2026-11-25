package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.global.publicid.PublicIds
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class RoomDetailResponse(
    // 방 코드(Sqids). 숫자 PK는 내보내지 않는다
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyStartTime: LocalTime,
    val dailyEndTime: LocalTime,
    val timezone: String,
    // 확정 일정이 들어가는 공통 캘린더. 프론트가 내 달력에서 이 방의 일정을 구분할 때 쓴다
    val calendarId: Long,
    // 방장의 회원 태그
    val ownerTag: String,
    val isOwner: Boolean,
    val members: List<RoomMemberResponse>,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            room: SchedulingRoom,
            members: List<RoomMember>,
            userId: Long,
            publicIds: PublicIds
        ): RoomDetailResponse =
            RoomDetailResponse(
                id = publicIds.room.encode(requireNotNull(room.id)),
                name = room.name,
                startDate = room.startDate,
                endDate = room.endDate,
                dailyStartTime = room.dailyStartTime,
                dailyEndTime = room.dailyEndTime,
                timezone = room.timezone,
                calendarId = requireNotNull(room.calendar.id),
                ownerTag = publicIds.user.encode(requireNotNull(room.owner.id)),
                isOwner = room.isOwner(userId),
                members = members.map { RoomMemberResponse.from(it, room, publicIds) },
                createdAt = room.createdAt,
            )
    }
}
