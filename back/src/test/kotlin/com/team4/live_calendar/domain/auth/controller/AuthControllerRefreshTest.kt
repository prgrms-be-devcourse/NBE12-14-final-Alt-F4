package com.team4.live_calendar.domain.auth.controller

import com.team4.live_calendar.domain.auth.entity.RefreshToken
import com.team4.live_calendar.domain.auth.repository.RefreshTokenRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.security.RefreshTokenCookieFactory
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerRefreshTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val cookieName = RefreshTokenCookieFactory.COOKIE_NAME

    @BeforeEach
    fun setUp() {
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@mail.com","password":"password1!","nickname":"test"}"""
        }
    }

    private fun loginAndGetRefreshCookie(): Cookie {
        val response = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@mail.com","password":"password1!"}"""
        }.andReturn().response
        return assertNotNull(response.getCookie(cookieName))
    }

    private fun refresh(cookie: Cookie) =
        mockMvc.post("/api/v1/auth/refresh") { cookie(cookie) }

    @Test
    fun `로그인하면 HttpOnly Refresh Token 쿠키가 발급된다`() {
        // when
        val cookie = loginAndGetRefreshCookie()

        // then
        assertTrue(cookie.isHttpOnly)
        assertEquals("/api/v1/auth", cookie.path)
    }

    @Test
    fun `Refresh Token으로 새 Access Token과 새 Refresh Token을 받는다`() {
        // given
        val old = loginAndGetRefreshCookie()

        // when
        val result = refresh(old)

        // then
        val response = result.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
        }.andReturn().response
        assertNotEquals(old.value, assertNotNull(response.getCookie(cookieName)).value)
    }

    @Test
    fun `한 번 사용한 Refresh Token은 재사용할 수 없다 (Rotation)`() {
        // given
        val old = loginAndGetRefreshCookie()
        refresh(old)

        // when
        val result = refresh(old)

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `쿠키 없이 재발급하면 401`() {
        // when
        val result = mockMvc.post("/api/v1/auth/refresh")

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그아웃하면 쿠키가 만료되고 그 Refresh Token은 더 이상 쓸 수 없다`() {
        // given
        val cookie = loginAndGetRefreshCookie()

        // when
        val logoutResponse = mockMvc.post("/api/v1/auth/logout") { cookie(cookie) }
            .andExpect { status { isNoContent() } }
            .andReturn().response

        // then
        assertEquals(0, assertNotNull(logoutResponse.getCookie(cookieName)).maxAge)
        refresh(cookie).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `존재하지 않는 Refresh Token이면 401 INVALID_REFRESH_TOKEN`() {
        // when
        val result = refresh(Cookie(cookieName, "unknown-token"))

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `만료된 Refresh Token이면 401 INVALID_REFRESH_TOKEN`() {
        // given
        val user = assertNotNull(userRepository.findByEmail("test@mail.com"))
        val rawToken = "expired-token-for-test"
        refreshTokenRepository.saveAndFlush(
            RefreshToken.create(
                user = user,
                tokenHash = sha256Hex(rawToken),
                expiresAt = Instant.now().minusSeconds(60),
            ),
        )

        // when
        val result = refresh(Cookie(cookieName, rawToken))

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `잘못된 Authorization 헤더가 있으면 permitAll 경로에서도 401이고 Refresh Token은 소모되지 않는다`() {
        // given
        val cookie = loginAndGetRefreshCookie()

        // when
        val result = mockMvc.post("/api/v1/auth/refresh") {
            cookie(cookie)
            header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value")
        }

        // then
        result.andExpect { status { isUnauthorized() } }
        refresh(cookie).andExpect { status { isOk() } }
    }

    // RefreshTokenService.hash()와 같은 방식 (테스트에서 DB에 직접 넣을 해시를 만든다)
    private fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
}
