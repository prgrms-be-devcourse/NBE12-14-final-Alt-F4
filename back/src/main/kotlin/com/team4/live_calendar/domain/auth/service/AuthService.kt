package com.team4.live_calendar.domain.auth.service

import com.team4.live_calendar.domain.auth.dto.LoginRequest
import com.team4.live_calendar.domain.auth.dto.SignupRequest
import com.team4.live_calendar.domain.auth.dto.SignupResponse
import com.team4.live_calendar.domain.auth.dto.TokenResponse
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.security.JwtTokenProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Transactional
    fun signup(request: SignupRequest): SignupResponse {
        val email = User.normalizeEmail(request.email)
        if (userRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        val user = User.signUp(
            email = email,
            passwordHash = requireNotNull(passwordEncoder.encode(request.password)),
            nickname = request.nickname,
        )

        val saved = try {
            userRepository.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
        return SignupResponse(requireNotNull(saved.id))
    }

    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(User.normalizeEmail(request.email))
        val passwordHash = user?.passwordHash

        if (user == null || passwordHash == null || !passwordEncoder.matches(request.password, passwordHash)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }

        return TokenResponse(
            accessToken = jwtTokenProvider.createAccessToken(requireNotNull(user.id)),
            expiresIn = jwtTokenProvider.accessTokenExpirySeconds,
        )
    }

}