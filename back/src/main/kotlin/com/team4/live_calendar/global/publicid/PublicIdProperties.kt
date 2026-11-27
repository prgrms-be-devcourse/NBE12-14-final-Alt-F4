package com.team4.live_calendar.global.publicid

import org.springframework.boot.context.properties.ConfigurationProperties

// 방 코드와 회원 태그는 알파벳이 달라서 같은 숫자 id라도 다른 코드가 나온다
// 운영 알파벳은 한 번 정하면 바꾸지 않는다 (바꾸면 이미 공유된 방 주소와 태그가 전부 깨진다)
@ConfigurationProperties(prefix = "app.sqids")
class PublicIdProperties(
    val roomAlphabet: String,
    val userAlphabet: String,
    val shareAlphabet: String,
    val minLength: Int,
)