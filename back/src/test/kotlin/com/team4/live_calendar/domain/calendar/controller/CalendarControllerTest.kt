package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
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
import java.time.LocalDate
import java.time.LocalTime

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarControllerTest(
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

    // Authentication.name = 회원 ID 인 인증 상태를 직접 넣는다 (CalendarGroupControllerTest와 같음)
    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    // 내 그룹에 내가 OWNER인 캘린더 (다른 사람은 role로 이용 관계를 더 붙인다)
    private fun myCalendar(name: String = "야구"): Calendar {
        val calendar = persist(Calendar.create(name, "설명", "#FF0000", "Asia/Seoul"))
        persist(Subscribe.create(me, persist(CalendarGroup.create(me, "내 그룹", 0)), calendar, CalendarRole.OWNER, null))
        return calendar
    }

    private fun join(user: User, calendar: Calendar, role: CalendarRole) =
        persist(Subscribe.create(user, persist(CalendarGroup.create(user, "그룹", 0)), calendar, role, null))

    private fun json(name: String, color: String? = null, timezone: String? = null): String {
        val colorPart = color?.let { ", \"color\": \"$it\"" } ?: ""
        val timezonePart = timezone?.let { ", \"timezone\": \"$it\"" } ?: ""
        return """{"name": "$name"$colorPart$timezonePart}"""
    }

    // ===== 생성 =====

    @Test
    fun `캘린더를 만들면 201과 원본 정보, 내 역할과 그룹을 반환한다`() {
        // given
        val group = persist(CalendarGroup.create(me, "운동", 0))

        // when
        val result = mockMvc.post("/api/v1/calendars") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "야구", "description": "KBO", "color": "#FF0000", "timezone": "Asia/Seoul", "groupId": ${group.id}}"""
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.name") { value("야구") }
            jsonPath("$.description") { value("KBO") }
            jsonPath("$.color") { value("#FF0000") }
            jsonPath("$.timezone") { value("Asia/Seoul") }
            jsonPath("$.role") { value("OWNER") }
            jsonPath("$.groupId") { value(group.id!!) }
        }
    }

    @Test
    fun `생성에서 이름이 비었거나 공백뿐이거나 101자이거나 색상이 31자면 400 INVALID_INPUT`() {
        listOf(json(""), json("   "), json("가".repeat(101)), json("야구", color = "#".repeat(31))).forEach { body ->
            // when
            val result = mockMvc.post("/api/v1/calendars") {
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
    fun `생성에서 잘못된 시간대는 400 INVALID_INPUT`() {
        // when
        val result = mockMvc.post("/api/v1/calendars") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = json("야구", timezone = "Mars/Base")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.detail") { value("올바르지 않은 시간대입니다: Mars/Base") }
        }
    }

    // ===== 상세 =====

    @Test
    fun `이용 관계가 있으면 캘린더를 200으로 반환한다`() {
        // given
        val calendar = myCalendar()
        join(other, calendar, CalendarRole.SUBSCRIBER)

        // when
        val result = mockMvc.get("/api/v1/calendars/${calendar.id}") { with(loginAs(other)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(calendar.id!!) }
            jsonPath("$.name") { value("야구") }
            jsonPath("$.role") { value("SUBSCRIBER") }
        }
    }

    @Test
    fun `이용 관계가 없으면 403, 없는 캘린더는 404 CALENDAR_NOT_FOUND`() {
        // given
        val calendar = myCalendar()

        // when, then
        mockMvc.get("/api/v1/calendars/${calendar.id}") { with(loginAs(other)) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mockMvc.get("/api/v1/calendars/${Long.MAX_VALUE}") { with(loginAs(me)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CALENDAR_NOT_FOUND") }
        }
    }

    // ===== 수정 =====

    @Test
    fun `OWNER가 고치면 200, 시간대를 안 보내면 기존 시간대가 남는다`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = json("프로야구")
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("프로야구") }
            jsonPath("$.description") { doesNotExist() }
            jsonPath("$.color") { doesNotExist() }
            jsonPath("$.timezone") { value("Asia/Seoul") }
        }
    }

    @Test
    fun `OWNER가 아니면 수정은 403`() {
        // given
        val calendar = myCalendar()
        join(other, calendar, CalendarRole.EDITOR)

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}") {
            with(loginAs(other))
            contentType = MediaType.APPLICATION_JSON
            content = json("내 거")
        }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `수정에서 이름이 비었거나 공백뿐이거나 101자이거나 색상이 31자면 400 INVALID_INPUT`() {
        // given
        val calendar = myCalendar()

        listOf(json(""), json("   "), json("가".repeat(101)), json("야구", color = "#".repeat(31))).forEach { body ->
            // when
            val result = mockMvc.patch("/api/v1/calendars/${calendar.id}") {
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

    // ===== 그룹 이동 =====

    @Test
    fun `내 그룹으로 옮기면 200과 바뀐 groupId를 반환한다`() {
        // given
        val calendar = myCalendar()
        val hobby = persist(CalendarGroup.create(me, "취미", 1))

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}/group") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupId": ${hobby.id}}"""
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.groupId") { value(hobby.id!!) }
        }
    }

    @Test
    fun `groupId가 없으면 400 INVALID_INPUT이고 errors에 groupId가 나온다`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}/group") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("groupId") }
        }
    }

    @Test
    fun `남의 그룹으로 옮기면 404 GROUP_NOT_FOUND`() {
        // given
        val calendar = myCalendar()
        val othersGroup = persist(CalendarGroup.create(other, "남의 그룹", 0))

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}/group") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupId": ${othersGroup.id}}"""
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("GROUP_NOT_FOUND") }
        }
    }

    // ===== 삭제 =====

    @Test
    fun `OWNER가 지우면 204`() {
        // given
        val calendar = myCalendar()
        join(other, calendar, CalendarRole.SUBSCRIBER)

        // when
        val result = mockMvc.delete("/api/v1/calendars/${calendar.id}") { with(loginAs(me)) }

        // then
        result.andExpect { status { isNoContent() } }
    }

    @Test
    fun `조율방이 쓰는 캘린더를 지우면 409 CALENDAR_IN_USE_BY_ROOM`() {
        // given
        val calendar = myCalendar()
        persist(
            SchedulingRoom.create(
                owner = me,
                calendar = calendar,
                name = "모임",
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 7),
                dailyStartTime = LocalTime.of(9, 0),
                dailyEndTime = LocalTime.of(18, 0),
                timezone = "UTC",
            ),
        )

        // when
        val result = mockMvc.delete("/api/v1/calendars/${calendar.id}") { with(loginAs(me)) }

        // then
        result.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CALENDAR_IN_USE_BY_ROOM") }
        }
    }

    @Test
    fun `로그인 없이 부르면 401 UNAUTHORIZED`() {
        // when
        val result = mockMvc.get("/api/v1/calendars/1")

        // then
        result.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    // ===== 내 화면 표시 설정 =====

    @Test
    fun `표시 설정을 바꾸면 204이고 그룹 조회에 내 표시 여부와 내 색이 나온다`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}/display") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"isVisible": false, "color": "#00FF00"}"""
        }

        // then
        result.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/groups") { with(loginAs(me)) }.andExpect {
            jsonPath("$[0].calendars[0].isVisible") { value(false) }
            jsonPath("$[0].calendars[0].myColor") { value("#00FF00") }
            jsonPath("$[0].calendars[0].color") { value("#00FF00") }
        }
    }

    @Test
    fun `표시 설정에 isVisible이 없으면 400 INVALID_INPUT`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}/display") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"color": "#00FF00"}"""
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("isVisible") }
        }
    }

    @Test
    fun `표시 설정의 color가 30자를 넘으면 400 INVALID_INPUT`() {
        // given
        val calendar = myCalendar()

        // when
        val result = mockMvc.patch("/api/v1/calendars/${calendar.id}/display") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"isVisible": true, "color": "${"a".repeat(31)}"}"""
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("color") }
        }
    }

    @Test
    fun `없는 캘린더의 표시 설정은 404이고 이용하지 않는 캘린더는 403이다`() {
        // given
        val calendar = myCalendar()

        // when
        val notFound = mockMvc.patch("/api/v1/calendars/-1/display") {
            with(loginAs(me))
            contentType = MediaType.APPLICATION_JSON
            content = """{"isVisible": false}"""
        }
        val forbidden = mockMvc.patch("/api/v1/calendars/${calendar.id}/display") {
            with(loginAs(other))
            contentType = MediaType.APPLICATION_JSON
            content = """{"isVisible": false}"""
        }

        // then
        notFound.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CALENDAR_NOT_FOUND") }
        }
        forbidden.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `로그인하지 않으면 표시 설정을 바꿀 수 없다`() {
        // given
        val calendar = myCalendar()

        // when & then
        mockMvc.patch("/api/v1/calendars/${calendar.id}/display") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"isVisible": false}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }
}
