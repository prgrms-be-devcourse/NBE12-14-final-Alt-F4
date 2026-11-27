package com.team4.live_calendar.domain.user.dto

import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds

data class UserResponse(
    val id: Long,
    val email: String?,
    val nickname: String,
    // 내 회원 태그. 프론트가 내 정보 화면에서 복사 버튼으로 보여 준다 (다른 회원이 나를 초대할 때 쓴다)
    val tag: String,
) {
    companion object {
        fun from(user: User, publicIds: PublicIds) = UserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            nickname = user.nickname,
            tag = publicIds.user.encode(requireNotNull(user.id)),
        )
    }

}
