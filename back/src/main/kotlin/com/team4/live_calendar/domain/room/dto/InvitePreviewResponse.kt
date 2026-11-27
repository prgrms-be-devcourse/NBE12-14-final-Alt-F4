package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.global.publicid.PublicIds
import java.time.LocalDate
import java.time.LocalTime

// 초대 확인 화면용. 참여 전이라 참여자 목록·공유 일정은 넣지 않는다
data class InvitePreviewResponse(
    // 방 코드(Sqids)
    val roomId: String,
    val name: String,
    val ownerNickname: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyStartTime: LocalTime,
    val dailyEndTime: LocalTime,
    val timezone: String,
    // 이미 참여 중이면 프론트가 수락 버튼 대신 "방으로 이동"을 보여 준다
    val alreadyMember: Boolean,
) {
    companion object {
        fun from(room: SchedulingRoom, alreadyMember: Boolean, publicIds: PublicIds): InvitePreviewResponse =
            InvitePreviewResponse(
                roomId = publicIds.room.encode(requireNotNull(room.id)),
                name = room.name,
                ownerNickname = room.owner.nickname,
                startDate = room.startDate,
                endDate = room.endDate,
                dailyStartTime = room.dailyStartTime,
                dailyEndTime = room.dailyEndTime,
                timezone = room.timezone,
                alreadyMember = alreadyMember,
            )
    }
}