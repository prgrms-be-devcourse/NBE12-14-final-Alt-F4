package com.team4.live_calendar.domain.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

class SignupRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 64)
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d])[\\x21-\\x7E]+$",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함하고, 영문·숫자·특수문자(공백 제외)만 사용할 수 있습니다.",
    )
    val password: String,

    @field:NotBlank
    @field:Size(max = 50)
    val nickname: String,
)