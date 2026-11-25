package com.team4.live_calendar.global.publicid

import org.springframework.stereotype.Component

// 종류별 인코더. 서비스는 publicIds.room / publicIds.user 로 꺼내 쓴다
@Component
class PublicIds(properties: PublicIdProperties) {

    // 조율방 주소 (/rooms/{roomId})
    val room = PublicIdCodec(properties.roomAlphabet, properties.minLength)

    // 회원 태그 (닉네임#태그, PR3 회원 검색)
    val user = PublicIdCodec(properties.userAlphabet, properties.minLength)
}