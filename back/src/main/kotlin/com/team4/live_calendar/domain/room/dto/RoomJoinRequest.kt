package com.team4.live_calendar.domain.room.dto

// 참여할 때 공통 캘린더(확정 일정)를 넣을 내 그룹 (링크 수락, 지정 초대 수락에서 같이 쓴다)
data class RoomJoinRequest(
    val groupId: Long,
)