package com.team4.live_calendar.domain.auth.controller

import com.team4.live_calendar.domain.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerSignupTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
) {

    private fun signup(email: String, password: String = "password1!", nickname: String = "test") =
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password","nickname":"$nickname"}"""
        }

    @Test
    fun `회원가입에 성공하면 201과 userId를 반환하고 비밀번호는 해시로 저장된다`() {
        // when
        val result = signup("new@mail.com")

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.userId") { exists() }
        }
        val user = assertNotNull(userRepository.findByEmail("new@mail.com"))
        assertTrue(assertNotNull(user.passwordHash).startsWith("{bcrypt}"))
    }

    @Test
    fun `이미 가입된 이메일이면 409 DUPLICATE_EMAIL`() {
        // given
        signup("dup@mail.com")

        // when
        val result = signup("DUP@mail.com")

        // then
        result.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE_EMAIL") }
        }
    }

    @Test
    fun `비밀번호 정책을 어기면 400 INVALID_INPUT과 필드 에러이고 입력한 비밀번호는 응답에 없다`() {
        // when
        val result = signup("weak@mail.com", password = "12345678")

        // then
        val body = result
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value("password") }
            }
            .andReturn().response.contentAsString
        assertFalse(body.contains("12345678"))
    }

    @Test
    fun `비밀번호에 한글이나 공백이 있으면 400 INVALID_INPUT`() {
        // given
        val passwords = listOf("password1!가", "pass word1!")

        passwords.forEach { password ->
            // when
            val result = signup("ascii@mail.com", password = password)

            // then
            result.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("password") }
            }
        }
    }

    @Test
    fun `닉네임에 한글·영문·숫자 외의 문자가 있으면 400 INVALID_INPUT`() {
        // given: #, 공백, 특수문자, 이모지, 한글 자모
        val nicknames = listOf("모임#방장", "모임 방장", "방장!", "방장😀", "ㅋㅋ방장")

        nicknames.forEach { nickname ->
            // when
            val result = signup("first@mail.com", nickname = nickname)

            // then
            result.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value("nickname") }
            }
        }
    }

    @Test
    fun `닉네임이 2자보다 짧거나 20자보다 길면 400 INVALID_INPUT`() {
        // given: 1자, 21자
        val nicknames = listOf("가", "가".repeat(21))

        nicknames.forEach { nickname ->
            // when
            val result = signup("first@mail.com", nickname = nickname)

            // then
            result.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("nickname") }
            }
        }
    }

    @Test
    fun `닉네임은 한글·영문·숫자를 섞어 2자부터 20자까지 쓸 수 있다`() {
        // given: 2자, 섞은 것, 20자
        val nicknames = listOf("방장", "Room1방장", "가".repeat(20))

        nicknames.forEachIndexed { i, nickname ->
            // when
            val result = signup("ok$i@mail.com", nickname = nickname)

            // then
            result.andExpect { status { isCreated() } }
        }
    }

    @Test
    fun `같은 닉네임으로도 가입할 수 있다`() {
        // given
        signup("first@mail.com", nickname = "모임방장")

        // when
        val result = signup("second@mail.com", nickname = "모임방장")

        // then
        result.andExpect { status { isCreated() } }
    }

    @Test
    fun `인증 없이 보호된 API를 호출하면 거부된다`() {
        // when
        val result = mockMvc.post("/api/v1/anything")

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `가입 경로는 POST만 열려 있고 GET은 거부된다`() {
        // when
        val result = mockMvc.get("/api/v1/auth/signup")

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `헬스 체크는 인증 없이 열려 있다`() {
        // when
        val result = mockMvc.get("/actuator/health")

        // then
        result.andExpect { status { isOk() } }
    }
}
