package com.team4.live_calendar.domain.event.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.service.EventService
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
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val eventService: EventService,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var viewer: User
    private lateinit var calendar: Calendar

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보기만"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        subscribe(owner, CalendarRole.OWNER)
        subscribe(viewer, CalendarRole.VIEWER)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun subscribe(user: User, role: CalendarRole): Subscribe {
        val group = persist(CalendarGroup.create(user, "내 캘린더", null))
        return persist(Subscribe.create(user, group, calendar, role, null))
    }

    // 로그인 API가 아직 없어서, Authentication.name = 회원 ID 인 인증 상태를 직접 넣는다
    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    private fun timedEventJson(title: String = "팀 회의") = """
        {"calendarId": ${calendar.id}, "title": "$title",
         "startAt": "2026-11-18T14:00:00+09:00", "endAt": "2026-11-18T15:00:00+09:00"}
    """

    private fun createEvent(): EventResponse =
        eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "팀 회의",
                description = null,
                location = null,
                startAt = OffsetDateTime.parse("2026-11-18T14:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-18T15:00:00+09:00"),
            ),
        )

    // ===== 생성 =====

    @Test
    fun `일정을 생성하면 201과 UTC 시간으로 바뀐 일정을 반환한다`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = timedEventJson()
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.title") { value("팀 회의") }
            jsonPath("$.calendarId") { value(calendar.id!!) }
            jsonPath("$.userId") { value(owner.id!!) }
            jsonPath("$.isAllDay") { value(false) }
            jsonPath("$.startAt") { value("2026-11-18T05:00:00Z") }
            jsonPath("$.endAt") { value("2026-11-18T06:00:00Z") }
            jsonPath("$.timezone") { value("UTC") }
        }
    }

    @Test
    fun `종일 일정은 보낸 날짜 그대로 UTC 자정으로 저장된다`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"calendarId": ${calendar.id}, "title": "워크숍", "isAllDay": true,
                 "startDate": "2026-11-18", "endDate": "2026-11-19"}
            """
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.isAllDay") { value(true) }
            jsonPath("$.startAt") { value("2026-11-18T00:00:00Z") }
            jsonPath("$.endAt") { value("2026-11-20T00:00:00Z") }
        }
    }

    @Test
    fun `제목이 비어 있으면 400 INVALID_INPUT`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = timedEventJson(title = "  ")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("title") }
        }
    }

    @Test
    fun `VIEWER가 일정을 생성하면 403 FORBIDDEN`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            with(loginAs(viewer))
            contentType = MediaType.APPLICATION_JSON
            content = timedEventJson()
        }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `로그인하지 않으면 일정 API를 호출할 수 없다`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            contentType = MediaType.APPLICATION_JSON
            content = timedEventJson()
        }

        // then
        result.andExpect { status { isUnauthorized() } }
    }

    // ===== 조회 =====

    @Test
    fun `일정 상세를 조회한다`() {
        // given
        val created = createEvent()

        // when
        val result = mockMvc.get("/api/v1/events/${created.id}") { with(loginAs(viewer)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(created.id) }
            jsonPath("$.title") { value("팀 회의") }
        }
    }

    @Test
    fun `없는 일정을 조회하면 404 EVENT_NOT_FOUND`() {
        // when
        val result = mockMvc.get("/api/v1/events/-1") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("EVENT_NOT_FOUND") }
        }
    }

    @Test
    fun `기간과 겹치는 일정 목록을 조회한다`() {
        // given
        val created = createEvent()

        // when: 쿼리 파라미터의 +는 공백으로 바뀌므로 UTC(Z) 형식으로 보낸다
        val result = mockMvc.get("/api/v1/events") {
            with(loginAs(owner))
            param("from", "2026-11-17T15:00:00Z")
            param("to", "2026-11-18T15:00:00Z")
            param("calendarIds", calendar.id.toString())
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(created.id) }
        }
    }

    @Test
    fun `조회 기간이 잘못되면 400 INVALID_DATE_RANGE`() {
        // when
        val result = mockMvc.get("/api/v1/events") {
            with(loginAs(owner))
            param("from", "2026-11-18T00:00:00Z")
            param("to", "2026-11-17T00:00:00Z")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_DATE_RANGE") }
        }
    }

    // ===== 수정 / 삭제 =====

    @Test
    fun `일정을 수정하면 200과 수정된 일정을 반환한다`() {
        // given
        val created = createEvent()

        // when
        val result = mockMvc.patch("/api/v1/events/${created.id}") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title": "제목 변경",
                 "startAt": "2026-11-18T14:00:00+09:00", "endAt": "2026-11-18T16:00:00+09:00"}
            """
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("제목 변경") }
            jsonPath("$.endAt") { value("2026-11-18T07:00:00Z") }
        }
    }

    @Test
    fun `일정을 삭제하면 204를 반환하고 다시 조회하면 404`() {
        // given
        val created = createEvent()

        // when
        val result = mockMvc.delete("/api/v1/events/${created.id}") { with(loginAs(owner)) }

        // then
        result.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/events/${created.id}") { with(loginAs(owner)) }
            .andExpect { status { isNotFound() } }
    }

    // ===== 반복 일정 =====

    private fun recurringEventJson(rrule: String) = """
        {"calendarId": ${calendar.id}, "title": "주간 회의",
         "startAt": "2026-11-16T08:00:00+09:00", "endAt": "2026-11-16T09:00:00+09:00",
         "timezone": "Asia/Seoul", "rrule": "$rrule"}
    """

    private fun createRecurringEvent(): Long {
        val body = mockMvc.post("/api/v1/events") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = recurringEventJson("FREQ=WEEKLY;BYDAY=MO")
        }.andReturn().response.contentAsString
        return Regex(""""id":(\d+)""").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `반복 일정을 JSON으로 생성하면 정리된 rrule과 occurrenceStart를 반환한다`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = recurringEventJson("rrule:byday=MO;freq=weekly")
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.rrule") { value("FREQ=WEEKLY;BYDAY=MO") }
            jsonPath("$.timezone") { value("Asia/Seoul") }
            jsonPath("$.startAt") { value("2026-11-15T23:00:00Z") }
            jsonPath("$.occurrenceStart") { value("2026-11-15T23:00:00Z") }
        }
    }

    @Test
    fun `잘못된 반복 규칙으로 생성하면 400 INVALID_RECURRENCE`() {
        // when
        val result = mockMvc.post("/api/v1/events") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = recurringEventJson("FREQ=HOURLY")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_RECURRENCE") }
        }
    }

    @Test
    fun `반복 일정을 JSON으로 수정하면 규칙과 시간대가 바뀐다`() {
        // given
        val id = createRecurringEvent()

        // when: 뉴욕 시간 화요일 10시 매주
        val result = mockMvc.patch("/api/v1/events/$id") {
            with(loginAs(owner))
            param("scope", "ALL")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title": "주간 회의",
                 "startAt": "2026-11-17T10:00:00-05:00", "endAt": "2026-11-17T11:00:00-05:00",
                 "timezone": "America/New_York", "rrule": "FREQ=WEEKLY;BYDAY=TU;COUNT=2"}
            """
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.rrule") { value("FREQ=WEEKLY;COUNT=2;BYDAY=TU") }
            jsonPath("$.timezone") { value("America/New_York") }
            jsonPath("$.startAt") { value("2026-11-17T15:00:00Z") }
        }
    }

    @Test
    fun `기간 조회는 반복 일정을 회차마다 펼쳐 반환한다`() {
        // given
        val id = createRecurringEvent()

        // when: 한국 11/16 ~ 11/30 (+09:00은 param으로 넣으면 그대로 전달된다)
        val result = mockMvc.get("/api/v1/events") {
            with(loginAs(viewer))
            param("from", "2026-11-16T00:00:00+09:00")
            param("to", "2026-11-30T00:00:00+09:00")
            param("timezone", "Asia/Seoul")
        }

        // then: 11/16, 11/23 (11/30 08:00은 범위 밖)
        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(id) }
            jsonPath("$[1].id") { value(id) }
            jsonPath("$[0].occurrenceStart") { value("2026-11-15T23:00:00Z") }
            jsonPath("$[1].startAt") { value("2026-11-22T23:00:00Z") }
            jsonPath("$[1].occurrenceStart") { value("2026-11-22T23:00:00Z") }
            jsonPath("$[1].endAt") { value("2026-11-23T00:00:00Z") }
            jsonPath("$[1].rrule") { value("FREQ=WEEKLY;BYDAY=MO") }
        }
    }

    @Test
    fun `반복 일정의 이 일정만 수정하면 바뀐 회차를 반환한다`() {
        // given: 매주 월요일 한국 08:00
        val id = createRecurringEvent()

        // when: 11/23 회차만 10시로 (+09:00은 param으로 넣으면 그대로 전달된다)
        val result = mockMvc.patch("/api/v1/events/$id") {
            with(loginAs(owner))
            param("scope", "THIS")
            param("occurrenceStart", "2026-11-23T08:00:00+09:00")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"title": "변경된 회의",
                 "startAt": "2026-11-23T10:00:00+09:00", "endAt": "2026-11-23T11:00:00+09:00",
                 "rrule": "FREQ=WEEKLY;BYDAY=MO"}
            """
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id) }
            jsonPath("$.title") { value("변경된 회의") }
            jsonPath("$.startAt") { value("2026-11-23T01:00:00Z") }
            jsonPath("$.occurrenceStart") { value("2026-11-22T23:00:00Z") }
        }
    }

    @Test
    fun `반복 일정의 이 일정만 삭제하면 204이고 그 회차만 조회에서 빠진다`() {
        // given
        val id = createRecurringEvent()

        // when
        val result = mockMvc.delete("/api/v1/events/$id") {
            with(loginAs(owner))
            param("scope", "THIS")
            param("occurrenceStart", "2026-11-22T23:00:00Z")
        }

        // then
        result.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/events") {
            with(loginAs(owner))
            param("from", "2026-11-16T00:00:00+09:00")
            param("to", "2026-12-01T00:00:00+09:00")
            param("timezone", "Asia/Seoul")
        }.andExpect {
            jsonPath("$.length()") { value(2) }
            jsonPath("$[1].occurrenceStart") { value("2026-11-29T23:00:00Z") }
        }
    }

    @Test
    fun `반복 일정을 scope 없이 삭제하면 400 RECURRENCE_SCOPE_REQUIRED`() {
        // given
        val id = createRecurringEvent()

        // when
        val result = mockMvc.delete("/api/v1/events/$id") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("RECURRENCE_SCOPE_REQUIRED") }
        }
    }

    @Test
    fun `기간 조회의 timezone이 잘못되면 400 INVALID_INPUT`() {
        // when
        val result = mockMvc.get("/api/v1/events") {
            with(loginAs(owner))
            param("from", "2026-11-16T00:00:00Z")
            param("to", "2026-11-17T00:00:00Z")
            param("timezone", "Mars/Base")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `VIEWER가 일정을 삭제하면 403 FORBIDDEN`() {
        // given
        val created = createEvent()

        // when
        val result = mockMvc.delete("/api/v1/events/${created.id}") { with(loginAs(viewer)) }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }
}
