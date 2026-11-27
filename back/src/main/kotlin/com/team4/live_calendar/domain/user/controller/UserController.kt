package com.team4.live_calendar.domain.user.controller

import com.team4.live_calendar.domain.user.dto.UserResponse
import com.team4.live_calendar.domain.user.dto.UserTagResponse
import com.team4.live_calendar.domain.user.service.UserService
import com.team4.live_calendar.global.security.CurrentUserId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/me")
    fun getMe(@CurrentUserId userId: Long): UserResponse = userService.getMe(userId)

    // 조율방 회원 지정 초대에서 대상을 찾을 때 쓴다. 로그인한 회원만 (SecurityConfig 기본 규칙)
    @GetMapping("/tags/{tag}")
    fun findByTag(@PathVariable tag: String): UserTagResponse = userService.findByTag(tag)

}
