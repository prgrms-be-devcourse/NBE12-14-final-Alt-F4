package com.team4.live_calendar.domain.room.dto

// inviteeTag는 태그로 회원 찾기(GET /api/v1/users/tags/{tag})로 확인한 회원의 tag
class RoomInvitationCreateRequest(
    val inviteeTag: String,
)
