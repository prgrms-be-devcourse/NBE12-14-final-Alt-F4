package com.team4.live_calendar.domain.user.controller

import com.team4.live_calendar.domain.user.dto.UserResponse
import com.team4.live_calendar.domain.user.service.UserService
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal jwt: Jwt): UserResponse {
        val userId = jwt.subject?.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        return userService.getMe(userId)
    }
}