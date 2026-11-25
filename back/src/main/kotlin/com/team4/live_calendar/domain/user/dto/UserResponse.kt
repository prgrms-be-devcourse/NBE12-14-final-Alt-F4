package com.team4.live_calendar.domain.user.dto

import com.team4.live_calendar.domain.user.entity.User

data class UserResponse(
    val id: Long,
    val email: String?,
    val nickname: String,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            nickname = user.nickname,
        )
    }

}