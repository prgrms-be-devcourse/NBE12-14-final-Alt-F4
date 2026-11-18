package com.team4.live_calendar.domain.calendar.entity

// 만료 여부는 expiresAt으로 판단하고, 상태에는 발급자가 직접 끈 경우만 남긴다
enum class InvitationStatus {
    ACTIVE,
    REVOKED,
}
