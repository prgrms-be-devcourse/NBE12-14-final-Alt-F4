package com.team4.live_calendar.global.security

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorResponseTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accessDeniedHandler: ProblemDetailAccessDeniedHandler,
) {

    @Test
    fun `토큰 없이 인증 API를 호출하면 401 ProblemDetail과 WWW-Authenticate 헤더를 준다`() {
        // when
        val result = mockMvc.get("/api/v1/users/me")

        // then
        result.andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            header { string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")) }
            jsonPath("$.code") { value("UNAUTHORIZED") }
            jsonPath("$.status") { value(401) }
            jsonPath("$.instance") { value("/api/v1/users/me") }
        }
    }

    @Test
    fun `깨진 토큰이면 401 ProblemDetail이고 헤더에 invalid_token을 알린다`() {
        // when
        val result = mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value")
        }

        // then
        val response = result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }.andReturn().response
        assertTrue(assertNotNull(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token"))
    }

    @Test
    fun `권한이 없으면 403 ProblemDetail을 준다`() {
        // given
        val request = MockHttpServletRequest("GET", "/api/v1/users/me")
        val response = MockHttpServletResponse()

        // when
        accessDeniedHandler.handle(request, response, AccessDeniedException("denied"))

        // then
        assertEquals(403, response.status)
        assertEquals("FORBIDDEN", JsonPath.read<String>(response.contentAsString, "$.code"))
    }
}
