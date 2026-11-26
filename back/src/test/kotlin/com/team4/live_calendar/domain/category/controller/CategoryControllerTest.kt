package com.team4.live_calendar.domain.category.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val em: EntityManager,
) {

    private lateinit var me: User
    private lateinit var other: User

    @BeforeEach
    fun setUp() {
        me = persist(User.signUp("me@mail.com", "{bcrypt}hash", "나"))
        other = persist(User.signUp("other@mail.com", "{bcrypt}hash", "다른 사람"))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // Authentication.name = 회원 ID 인 인증 상태를 직접 넣는다 (CalendarControllerTest와 같음)
    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    // 내 그룹에 내가 OWNER인 캘린더 (다른 사람은 join으로 이용 관계를 더 붙인다)
    private fun myCalendar(name: String = "스터디"): Calendar {
        val calendar = persist(Calendar.create(name, null, "#000000"))
        persist(Subscribe.create(me, persist(CalendarGroup.create(me, "내 그룹", 0)), calendar, CalendarRole.OWNER, null))
        return calendar
    }

    private fun join(user: User, calendar: Calendar, role: CalendarRole) =
        persist(Subscribe.create(user, persist(CalendarGroup.create(user, "그룹", 0)), calendar, role, null))

    private fun myCategory(): EventCategory = persist(EventCategory.create(myCalendar(), "멘토링", "#4285F4"))

    private fun createJson(calendarId: Long?, name: String = "멘토링", color: String? = null): String {
        val calendarPart = calendarId?.let { "\"calendarId\": $it, " } ?: ""
        val colorPart = color?.let { ", \"color\": \"$it\"" } ?: ""
        return """{$calendarPart"name": "$name"$colorPart}"""
    }

    private fun updateJson(name: String, color: String? = null): String {
        val colorPart = color?.let { ", \"color\": \"$it\"" } ?: ""
        return """{"name": "$name"$colorPart}"""
    }

    // ===== 생성 =====

    @Test
    fun `카테고리를 만들면 201과 카테고리를 반환한다`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.post("/api/v1/categories") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = createJson(calendar.id, "멘토링", "#4285F4")
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.calendarId") { value(calendar.id!!) }
            jsonPath("$.name") { value("멘토링") }
            jsonPath("$.color") { value("#4285F4") }
        }
    }

    @Test
    fun `생성에서 이름이 비었거나 공백뿐이거나 101자이거나 색이 31자면 400 INVALID_INPUT`() {
        // given
        val calendar = myCalendar()

        listOf(
            createJson(calendar.id, ""),
            createJson(calendar.id, "   "),
            createJson(calendar.id, "가".repeat(101)),
            createJson(calendar.id, "멘토링", color = "#".repeat(31)),
        ).forEach { body ->
            // when
            val result = mockMvc.post("/api/v1/categories") {
                with(loginAs(me))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

            // then
            result.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    @Test
    fun `생성에서 calendarId가 없으면 400 INVALID_INPUT이고 errors에 calendarId가 나온다`() {
        // when
        val result = mockMvc.post("/api/v1/categories") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = createJson(calendarId = null)
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("calendarId") }
        }
    }

    @Test
    fun `생성에서 이름 칸이 없으면 400`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.post("/api/v1/categories") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"calendarId": ${calendar.id}}"""
        }

        // then
        result.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `남의 캘린더에 만들면 403 FORBIDDEN`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.post("/api/v1/categories") {
            with(loginAs(other))
            contentType = MediaType.APPLICATION_JSON
            content = createJson(calendar.id)
        }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `없는 캘린더에 만들면 404 CALENDAR_NOT_FOUND`() {
        // when
        val result = mockMvc.post("/api/v1/categories") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = createJson(Long.MAX_VALUE)
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CALENDAR_NOT_FOUND") }
        }
    }

    // ===== 수정 =====

    @Test
    fun `카테고리를 고치면 200과 바뀐 카테고리를 반환한다`() {
        // given
        val category = myCategory()

        // when
        val result = mockMvc.patch("/api/v1/categories/${category.id}") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = updateJson("1:1 멘토링", "#34A853")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(category.id!!) }
            jsonPath("$.calendarId") { value(category.calendar.id!!) }
            jsonPath("$.name") { value("1:1 멘토링") }
            jsonPath("$.color") { value("#34A853") }
        }
    }

    @Test
    fun `수정에서 이름이 비었거나 공백뿐이거나 101자이거나 색이 31자면 400 INVALID_INPUT`() {
        // given
        val category = myCategory()

        listOf(
            updateJson(""),
            updateJson("   "),
            updateJson("가".repeat(101)),
            updateJson("멘토링", color = "#".repeat(31)),
        ).forEach { body ->
            // when
            val result = mockMvc.patch("/api/v1/categories/${category.id}") {
                with(loginAs(me))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }

            // then
            result.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    @Test
    fun `VIEWER가 고치면 403 FORBIDDEN`() {
        // given
        val category = myCategory()
        join(other, category.calendar, CalendarRole.VIEWER)

        // when
        val result = mockMvc.patch("/api/v1/categories/${category.id}") {
            with(loginAs(other))
            contentType = MediaType.APPLICATION_JSON
            content = updateJson("1:1 멘토링")
        }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `없는 카테고리를 고치면 404 CATEGORY_NOT_FOUND`() {
        // when
        val result = mockMvc.patch("/api/v1/categories/${Long.MAX_VALUE}") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = updateJson("1:1 멘토링")
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CATEGORY_NOT_FOUND") }
        }
    }

    // ===== 삭제 =====

    @Test
    fun `카테고리를 지우면 204`() {
        // given
        val category = myCategory()

        // when
        val result = mockMvc.delete("/api/v1/categories/${category.id}") { with(loginAs(me)) }

        // then
        result.andExpect { status { isNoContent() } }
        em.clear()
        assertNull(em.find(EventCategory::class.java, category.id))
    }

    @Test
    fun `VIEWER가 지우면 403 FORBIDDEN`() {
        // given
        val category = myCategory()
        join(other, category.calendar, CalendarRole.VIEWER)

        // when
        val result = mockMvc.delete("/api/v1/categories/${category.id}") { with(loginAs(other)) }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `없는 카테고리를 지우면 404 CATEGORY_NOT_FOUND`() {
        // when
        val result = mockMvc.delete("/api/v1/categories/${Long.MAX_VALUE}") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CATEGORY_NOT_FOUND") }
        }
    }

    @Test
    fun `로그인 없이 부르면 401 UNAUTHORIZED`() {
        // when
        val result = mockMvc.post("/api/v1/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = createJson(1)
        }

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    // ===== 내 화면 숨김·복원 =====

    @Test
    fun `보기 권한도 카테고리를 숨기고 복원하면 204다`() {
        // given
        val category = myCategory()
        join(other, category.calendar, CalendarRole.VIEWER)

        // when
        val hide = mockMvc.put("/api/v1/categories/${category.id}/hidden") { with(loginAs(other)) }
        val show = mockMvc.delete("/api/v1/categories/${category.id}/hidden") { with(loginAs(other)) }

        // then
        hide.andExpect { status { isNoContent() } }
        show.andExpect { status { isNoContent() } }
    }

    @Test
    fun `없는 카테고리를 숨기거나 복원하면 404 CATEGORY_NOT_FOUND`() {
        // when
        val hide = mockMvc.put("/api/v1/categories/-1/hidden") { with(loginAs(me)) }
        val show = mockMvc.delete("/api/v1/categories/-1/hidden") { with(loginAs(me)) }

        // then
        listOf(hide, show).forEach {
            it.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("CATEGORY_NOT_FOUND") }
            }
        }
    }

    @Test
    fun `이용하지 않는 캘린더의 카테고리를 숨기거나 복원하면 403`() {
        // given
        val category = myCategory()

        // when
        val hide = mockMvc.put("/api/v1/categories/${category.id}/hidden") { with(loginAs(other)) }
        val show = mockMvc.delete("/api/v1/categories/${category.id}/hidden") { with(loginAs(other)) }

        // then
        listOf(hide, show).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        }
    }

    @Test
    fun `로그인하지 않으면 카테고리 숨김 API를 호출할 수 없다`() {
        // given
        val category = myCategory()

        // when & then
        mockMvc.put("/api/v1/categories/${category.id}/hidden").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
        mockMvc.delete("/api/v1/categories/${category.id}/hidden").andExpect { status { isUnauthorized() } }
    }
}
