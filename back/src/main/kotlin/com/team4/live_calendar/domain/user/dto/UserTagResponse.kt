package com.team4.live_calendar.domain.user.dto

import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds

// 태그로 찾은 회원. 초대하기 전에 닉네임을 보고 맞는 사람인지 확인하는 용도
// 다른 회원에게 보여 주는 정보라 이메일·숫자 id는 돌려주지 않는다. 화면에는 "nickname#tag"
data class UserTagResponse(
    val tag: String,
    val nickname: String,
) {
    companion object {
        fun from(user: User, publicIds: PublicIds) = UserTagResponse(
            tag = publicIds.user.encode(requireNotNull(user.id)),
            nickname = user.nickname,
        )
    }
}
