package com.team4.live_calendar.domain.room.dto

import java.time.Instant

// token 원문은 발급 응답에서만 볼 수 있다 (DB에는 해시만 있어서 다시 조회할 수 없다)
// 프론트가 초대 주소를 만든다. 예: https://{프론트 주소}/invite/{token}
data class InviteLinkResponse(
    val token: String,
    val createdAt: Instant,
)
