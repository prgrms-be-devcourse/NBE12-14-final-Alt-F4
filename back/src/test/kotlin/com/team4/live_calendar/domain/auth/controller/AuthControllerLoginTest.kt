package com.team4.live_calendar.domain.auth.controller

import com.jayway.jsonpath.JsonPath
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import com.team4.live_calendar.global.security.JwtProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerLoginTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtProperties: JwtProperties,
) {

    @BeforeEach
    fun setUp() {
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"test@mail.com","password":"password1!","nickname":"test"}"""
        }
    }

    private fun login(email: String, password: String) =
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }

    private fun getMe(token: String) =
        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }

    // 검증 대상 한 가지만 틀리게 만든 토큰을 발급한다
    private fun token(
        secret: String = jwtProperties.secret,
        issuer: String = jwtProperties.issuer,
        subject: String = "1",
        expiresAt: Instant = Instant.now().plusSeconds(600),
    ): String {
        val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .issuedAt(Instant.now().minusSeconds(3600))
            .expiresAt(expiresAt)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))
            .encode(JwtEncoderParameters.from(header, claims))
            .tokenValue
    }

    @Test
    fun `로그인에 성공하면 Access Token을 반환하고 그 토큰으로 내 정보를 조회한다`() {
        // given
        val body = login("test@mail.com", "password1!")
            .andExpect {
                status { isOk() }
                jsonPath("$.tokenType") { value("Bearer") }
            }
            .andReturn().response.contentAsString
        val accessToken = JsonPath.read<String>(body, "$.accessToken")

        // when
        val result = getMe(accessToken)

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("test@mail.com") }
            jsonPath("$.nickname") { value("test") }
        }
    }

    @Test
    fun `비밀번호가 틀리면 401 INVALID_CREDENTIALS`() {
        // when
        val result = login("test@mail.com", "wrong1234!")

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `없는 이메일도 같은 401 INVALID_CREDENTIALS`() {
        // when
        val result = login("none@mail.com", "password1!")

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `토큰 없이 내 정보를 조회하면 401`() {
        // when
        val result = mockMvc.get("/api/v1/users/me")

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `형식이 깨진 토큰이면 401`() {
        // when
        val result = getMe("invalid.token.value")

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `다른 키로 서명한 토큰이면 401`() {
        // given
        val token = token(secret = "another-secret-key-for-test-0123456789")

        // when
        val result = getMe(token)

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `만료된 토큰이면 401`() {
        // given: 기본 허용 오차(60초)보다 확실히 지난 시각
        val token = token(expiresAt = Instant.now().minusSeconds(120))

        // when
        val result = getMe(token)

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `발급자가 다른 토큰이면 401`() {
        // given
        val token = token(issuer = "other-service")

        // when
        val result = getMe(token)

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `sub가 숫자가 아닌 토큰이면 401`() {
        // given
        val token = token(subject = "abc")

        // when
        val result = getMe(token)

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `유효한 토큰이지만 회원이 없으면 404 USER_NOT_FOUND`() {
        // given: 키, 발급자, 만료는 모두 올바른 토큰
        val token = token(subject = "999999999")

        // when
        val result = getMe(token)

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
        }
    }
}
