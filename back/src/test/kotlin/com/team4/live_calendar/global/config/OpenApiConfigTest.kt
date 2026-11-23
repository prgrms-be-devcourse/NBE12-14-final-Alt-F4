package com.team4.live_calendar.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `API 문서에 Bearer 인증이 정의되고 기본으로 모든 API에 적용된다`() {
        // when
        val result = mockMvc.get("/v3/api-docs")

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.security[0].bearerAuth") { exists() }
        }
    }

    @Test
    fun `인증 API는 문서에서 인증 없이 호출하도록 표시된다`() {
        // when
        val result = mockMvc.get("/v3/api-docs")

        // then
        result.andExpect {
            jsonPath("$.paths['/api/v1/auth/login'].post.security") { isEmpty() }
            jsonPath("$.paths['/api/v1/auth/refresh'].post.security") { isEmpty() }
        }
    }
}
