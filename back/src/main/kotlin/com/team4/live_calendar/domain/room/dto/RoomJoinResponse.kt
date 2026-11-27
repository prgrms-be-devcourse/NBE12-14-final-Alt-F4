package com.team4.live_calendar.domain.room.dto

// 참여 후 프론트가 이 방 상세로 이동한다 (링크 수락, 지정 초대 수락에서 같이 쓴다)
data class RoomJoinResponse(
    // 방 코드(Sqids)
    val roomId: String,
)