package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.CategoryOption
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarGroupControllerTest(
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

    // Authentication.name = 회원 ID 인 인증 상태를 직접 넣는다 (EventControllerTest와 같음)
    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    private fun nameJson(name: String) = """{"name": "$name"}"""

    // ===== 생성 =====

    @Test
    fun `그룹을 만들면 201과 그룹을 반환한다`() {
        // when
        val result = mockMvc.post("/api/v1/groups") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = nameJson("운동")
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.name") { value("운동") }
            jsonPath("$.displayOrder") { value(0) }
        }
    }

    @Test
    fun `그룹 이름이 비었거나 공백뿐이거나 100자를 넘으면 400 INVALID_INPUT`() {
        listOf("", "   ", "가".repeat(101)).forEach { name ->
            // when
            val result = mockMvc.post("/api/v1/groups") {
                with(loginAs(me))
                contentType = MediaType.APPLICATION_JSON
                content = nameJson(name)
            }

            // then
            result.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value("name") }
            }
        }
    }

    @Test
    fun `그룹 이름 칸이 없으면 400`() {
        // when
        val result = mockMvc.post("/api/v1/groups") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }

        // then
        result.andExpect { status { isBadRequest() } }
    }

    // ===== 조회 =====

    @Test
    fun `내 그룹과 그 안의 캘린더를 200으로 반환한다`() {
        // given
        val sports = persist(CalendarGroup.create(me, "운동", 0))
        persist(CalendarGroup.create(me, "빈 그룹", 1))
        val baseball = persist(Calendar.create("야구", null, "#FF0000"))
        persist(Subscribe.create(me, sports, baseball, CalendarRole.OWNER, null))

        // when
        val result = mockMvc.get("/api/v1/groups") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(sports.id!!) }
            jsonPath("$[0].name") { value("운동") }
            jsonPath("$[0].displayOrder") { value(0) }
            jsonPath("$[0].calendars.length()") { value(1) }
            jsonPath("$[0].calendars[0].id") { value(baseball.id!!) }
            jsonPath("$[0].calendars[0].name") { value("야구") }
            jsonPath("$[0].calendars[0].color") { value("#FF0000") }
            jsonPath("$[0].calendars[0].role") { value("OWNER") }
            jsonPath("$[0].calendars[0].categories.length()") { value(0) }
            jsonPath("$[1].name") { value("빈 그룹") }
            jsonPath("$[1].calendars.length()") { value(0) }
        }
    }

    @Test
    fun `그룹 조회에서 캘린더의 categories를 반환한다`() {
        // given
        val sports = persist(CalendarGroup.create(me, "운동", 0))
        val baseball = persist(Calendar.create("야구", null, "#FF0000"))
        persist(Subscribe.create(me, sports, baseball, CalendarRole.OWNER, null))
        val category = persist(EventCategory.create(baseball, "경기", "#4285F4"))
        persist(EventCategory.create(baseball, "응원", null))

        // when
        val result = mockMvc.get("/api/v1/groups") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$[0].calendars[0].categories.length()") { value(2) }
            jsonPath("$[0].calendars[0].categories[0].id") { value(category.id!!) }
            jsonPath("$[0].calendars[0].categories[0].name") { value("경기") }
            jsonPath("$[0].calendars[0].categories[0].color") { value("#4285F4") }
            jsonPath("$[0].calendars[0].categories[1].name") { value("응원") }
        }
    }

    @Test
    fun `로그인 없이 부르면 401`() {
        // when
        val result = mockMvc.get("/api/v1/groups")

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    // ===== 이름 수정 =====

    @Test
    fun `그룹 이름을 바꾸면 200과 바뀐 그룹을 반환한다`() {
        // given
        val group = persist(CalendarGroup.create(me, "운동", 0))

        // when
        val result = mockMvc.patch("/api/v1/groups/${group.id}") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = nameJson("스포츠")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(group.id!!) }
            jsonPath("$.name") { value("스포츠") }
        }
    }

    @Test
    fun `남의 그룹 이름을 바꾸면 404 GROUP_NOT_FOUND`() {
        // given
        val othersGroup = persist(CalendarGroup.create(other, "남의 그룹", 0))

        // when
        val result = mockMvc.patch("/api/v1/groups/${othersGroup.id}") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = nameJson("내 거")
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("GROUP_NOT_FOUND") }
        }
    }

    // ===== 삭제 =====

    @Test
    fun `빈 그룹을 지우면 204`() {
        // given
        val group = persist(CalendarGroup.create(me, "운동", 0))

        // when
        val result = mockMvc.delete("/api/v1/groups/${group.id}") { with(loginAs(me)) }

        // then
        result.andExpect { status { isNoContent() } }
    }

    @Test
    fun `캘린더가 든 그룹을 지우면 409 GROUP_NOT_EMPTY`() {
        // given
        val group = persist(CalendarGroup.create(me, "운동", 0))
        persist(Subscribe.create(me, group, persist(Calendar.create("야구", null, null)), CalendarRole.OWNER, null))

        // when
        val result = mockMvc.delete("/api/v1/groups/${group.id}") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("GROUP_NOT_EMPTY") }
        }
    }

    @Test
    fun `남의 그룹을 지우면 404 GROUP_NOT_FOUND`() {
        // given
        val othersGroup = persist(CalendarGroup.create(other, "남의 그룹", 0))

        // when
        val result = mockMvc.delete("/api/v1/groups/${othersGroup.id}") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("GROUP_NOT_FOUND") }
        }
    }

    // ===== 내 화면 표시 칸 =====

    @Test
    fun `그룹 조회의 캘린더에 isVisible과 myColor 칸이 그 이름 그대로 나온다`() {
        // given
        val calendar = persist(Calendar.create("야구", null, "#FF0000", "Asia/Seoul"))
        val subscribe = persist(Subscribe.create(me, persist(CalendarGroup.create(me, "운동", 0)), calendar, CalendarRole.OWNER, null))
        subscribe.changeDisplay(false, "#00FF00")

        // when
        val result = mockMvc.get("/api/v1/groups") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$[0].calendars[0].isVisible") { value(false) }
            jsonPath("$[0].calendars[0].myColor") { value("#00FF00") }
            jsonPath("$[0].calendars[0].visible") { doesNotExist() }
        }
    }

    @Test
    fun `그룹 조회의 카테고리에 isVisible 칸이 그 이름 그대로 나온다`() {
        // given
        val calendar = persist(Calendar.create("야구", null, "#FF0000", "Asia/Seoul"))
        val subscribe = persist(Subscribe.create(me, persist(CalendarGroup.create(me, "운동", 0)), calendar, CalendarRole.OWNER, null))
        val category = persist(EventCategory.create(calendar, "경기", null))
        persist(CategoryOption.create(subscribe, category, calendar, isVisible = false))

        // when
        val result = mockMvc.get("/api/v1/groups") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$[0].calendars[0].categories[0].isVisible") { value(false) }
            jsonPath("$[0].calendars[0].categories[0].visible") { doesNotExist() }
        }
    }
}
