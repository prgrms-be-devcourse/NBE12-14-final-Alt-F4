package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class EventServiceTest(
    @Autowired private val eventService: EventService,
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val em: EntityManager,
    @Autowired private val jsonMapper: JsonMapper,
) {

    private lateinit var owner: User
    private lateinit var viewer: User
    private lateinit var stranger: User
    private lateinit var calendar: Calendar
    private lateinit var category: EventCategory
    private lateinit var otherCategory: EventCategory
    private lateinit var ownerSubscribe: Subscribe

    private val start = OffsetDateTime.parse("2026-11-18T14:00:00+09:00")
    private val end = OffsetDateTime.parse("2026-11-18T15:00:00+09:00")

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보기만"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "남"))

        calendar = persist(Calendar.create("팀 캘린더", null, null))
        category = persist(EventCategory.create(calendar, "회의", null))
        val otherCalendar = persist(Calendar.create("다른 캘린더", null, null))
        otherCategory = persist(EventCategory.create(otherCalendar, "다른 카테고리", null))

        ownerSubscribe = subscribe(owner, calendar, CalendarRole.OWNER)
        subscribe(viewer, calendar, CalendarRole.VIEWER)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun subscribe(user: User, calendar: Calendar, role: CalendarRole): Subscribe {
        val group = persist(CalendarGroup.create(user, "내 캘린더", null))
        return persist(Subscribe.create(user, group, calendar, role, null))
    }

    private fun createRequest(
        categoryId: Long? = null,
        startAt: OffsetDateTime? = start,
        endAt: OffsetDateTime? = end,
        timezone: String? = null,
    ) = EventCreateRequest(
        calendarId = calendar.id!!,
        categoryId = categoryId,
        title = "  팀 회의  ",
        description = "주간 회의",
        location = "회의실",
        startAt = startAt,
        endAt = endAt,
        timezone = timezone,
    )

    private fun allDayRequest(startDate: LocalDate?, endDate: LocalDate?) = EventCreateRequest(
        calendarId = calendar.id!!,
        categoryId = null,
        title = "워크숍",
        description = null,
        location = null,
        isAllDay = true,
        startDate = startDate,
        endDate = endDate,
    )

    private fun updateRequest(
        categoryId: Long? = null,
        startAt: OffsetDateTime = start,
        endAt: OffsetDateTime = end,
    ) = EventUpdateRequest(
        categoryId = categoryId,
        title = "제목 변경",
        description = null,
        location = null,
        startAt = startAt,
        endAt = endAt,
    )

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    // ===== 생성 =====

    @Test
    fun `편집 권한이 있으면 일정을 생성하고 시간은 UTC로 저장된다`() {
        // when
        val response = eventService.create(owner.id!!, createRequest(categoryId = category.id, timezone = "Asia/Seoul"))

        // then
        assertEquals("팀 회의", response.title)
        assertEquals(calendar.id, response.calendarId)
        assertEquals(category.id, response.categoryId)
        assertEquals(owner.id, response.userId)
        assertEquals(Instant.parse("2026-11-18T05:00:00Z"), response.startAt)
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), response.endAt)
        assertEquals("Asia/Seoul", response.timezone)
    }

    @Test
    fun `시간대를 보내지 않으면 UTC로 저장된다`() {
        // when
        val response = eventService.create(owner.id!!, createRequest())

        // then
        assertEquals("UTC", response.timezone)
    }

    @Test
    fun `종일 일정은 시작일 UTC 자정부터 종료일 다음 날 UTC 자정까지로 저장된다`() {
        // given
        val request = allDayRequest(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19))

        // when
        val response = eventService.create(owner.id!!, request)

        // then
        assertTrue(response.isAllDay)
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), response.startAt)
        assertEquals(Instant.parse("2026-11-20T00:00:00Z"), response.endAt)
    }

    @Test
    fun `JSON으로 받은 종일 일정 날짜는 밀리지 않고 시간 일정은 UTC로 바뀐다`() {
        // given: 실제 요청처럼 JSON을 앱의 JsonMapper로 읽는다
        val allDayJson = """
            {"calendarId": ${calendar.id}, "title": "워크숍", "isAllDay": true,
             "startDate": "2026-11-18", "endDate": "2026-11-18"}
        """
        val timedJson = """
            {"calendarId": ${calendar.id}, "title": "팀 회의",
             "startAt": "2026-11-18T00:00:00+09:00", "endAt": "2026-11-18T01:00:00+09:00"}
        """

        // when
        val allDay = eventService.create(owner.id!!, jsonMapper.readValue(allDayJson, EventCreateRequest::class.java))
        val timed = eventService.create(owner.id!!, jsonMapper.readValue(timedJson, EventCreateRequest::class.java))

        // then
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), allDay.startAt)
        assertEquals(Instant.parse("2026-11-19T00:00:00Z"), allDay.endAt)
        assertEquals(Instant.parse("2026-11-17T15:00:00Z"), timed.startAt)
    }

    @Test
    fun `종일 일정에 날짜가 없거나 시간 일정에 시간이 없으면 INVALID_INPUT`() {
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            eventService.create(owner.id!!, allDayRequest(LocalDate.of(2026, 11, 18), null))
        }
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            eventService.create(owner.id!!, createRequest(startAt = null))
        }
    }

    @Test
    fun `종일 일정의 종료일이 시작일보다 빠르면 생성할 수 없다`() {
        assertErrorCode(ErrorCode.INVALID_EVENT_TIME) {
            eventService.create(owner.id!!, allDayRequest(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17)))
        }
    }

    @Test
    fun `VIEWER는 일정을 생성할 수 없다`() {
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.create(viewer.id!!, createRequest()) }
    }

    @Test
    fun `이용하지 않는 캘린더에는 일정을 생성할 수 없다`() {
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.create(stranger.id!!, createRequest()) }
    }

    @Test
    fun `없는 캘린더에는 일정을 생성할 수 없다`() {
        // given
        val request = EventCreateRequest(
            calendarId = -1L, categoryId = null, title = "제목", description = null, location = null,
            startAt = start, endAt = end, timezone = null,
        )

        // when, then
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) { eventService.create(owner.id!!, request) }
    }

    @Test
    fun `다른 캘린더의 카테고리는 지정할 수 없다`() {
        assertErrorCode(ErrorCode.CATEGORY_NOT_IN_CALENDAR) {
            eventService.create(owner.id!!, createRequest(categoryId = otherCategory.id))
        }
    }

    @Test
    fun `종료 일시가 시작 일시보다 빠르면 생성할 수 없다`() {
        assertErrorCode(ErrorCode.INVALID_EVENT_TIME) {
            eventService.create(owner.id!!, createRequest(startAt = end, endAt = start))
        }
    }

    @Test
    fun `올바르지 않은 시간대는 거부한다`() {
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            eventService.create(owner.id!!, createRequest(timezone = "Mars/Base"))
        }
    }

    // ===== 상세 조회 =====

    @Test
    fun `이용 관계가 있으면 VIEWER도 일정을 조회할 수 있다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest())

        // when
        val response = eventService.get(viewer.id!!, created.id)

        // then
        assertEquals(created.id, response.id)
    }

    @Test
    fun `이용 관계가 없으면 일정을 조회할 수 없다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest())

        // when, then
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.get(stranger.id!!, created.id) }
    }

    @Test
    fun `없는 일정은 EVENT_NOT_FOUND`() {
        assertErrorCode(ErrorCode.EVENT_NOT_FOUND) { eventService.get(owner.id!!, -1L) }
    }

    // ===== 기간 조회 =====

    @Test
    fun `기간과 겹치는 일정만 시작 시간 순으로 조회한다`() {
        // given
        val inside = eventService.create(owner.id!!, createRequest())
        val overlapping = eventService.create(
            owner.id!!,
            createRequest(
                startAt = OffsetDateTime.parse("2026-11-17T23:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-18T01:00:00+09:00"),
            ),
        )
        eventService.create(
            owner.id!!,
            createRequest(
                startAt = OffsetDateTime.parse("2026-11-20T10:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-20T11:00:00+09:00"),
            ),
        )

        // when
        val result = eventService.findInRange(
            owner.id!!,
            from = OffsetDateTime.parse("2026-11-18T00:00:00+09:00"),
            to = OffsetDateTime.parse("2026-11-19T00:00:00+09:00"),
        )

        // then
        assertEquals(listOf(overlapping.id, inside.id), result.map { it.id })
    }

    @Test
    fun `내가 숨긴 일정은 기간 조회에서 빠진다`() {
        // given
        val hidden = eventService.create(owner.id!!, createRequest())
        val shown = eventService.create(owner.id!!, createRequest())
        persist(EventOption.create(ownerSubscribe, eventRepository.getReferenceById(hidden.id), calendar, isVisible = false))

        // when
        val ownerResult = eventService.findInRange(owner.id!!, start.minusDays(1), end.plusDays(1))
        val viewerResult = eventService.findInRange(viewer.id!!, start.minusDays(1), end.plusDays(1))

        // then
        assertEquals(listOf(shown.id), ownerResult.map { it.id })
        assertEquals(setOf(hidden.id, shown.id), viewerResult.map { it.id }.toSet())
    }

    @Test
    fun `이용하지 않는 캘린더의 일정은 기간 조회에 나오지 않는다`() {
        // given
        eventService.create(owner.id!!, createRequest())

        // when
        val result = eventService.findInRange(stranger.id!!, start.minusDays(1), end.plusDays(1))

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `조회 기간이 93일을 넘거나 시작이 끝보다 늦으면 실패한다`() {
        assertErrorCode(ErrorCode.INVALID_DATE_RANGE) {
            eventService.findInRange(owner.id!!, start, start.plusDays(94))
        }
        assertErrorCode(ErrorCode.INVALID_DATE_RANGE) {
            eventService.findInRange(owner.id!!, end, start)
        }
    }

    // ===== 수정 =====

    @Test
    fun `편집 권한이 있으면 일정을 수정한다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest(categoryId = category.id))

        // when
        val response = eventService.update(
            owner.id!!,
            created.id,
            updateRequest(endAt = OffsetDateTime.parse("2026-11-18T16:00:00+09:00")),
        )

        // then
        assertEquals("제목 변경", response.title)
        assertNull(response.categoryId)
        assertNull(response.description)
        assertEquals(Instant.parse("2026-11-18T07:00:00Z"), response.endAt)
        assertEquals(created.createdAt, response.createdAt)
    }

    @Test
    fun `VIEWER는 일정을 수정할 수 없다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest())

        // when, then
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.update(viewer.id!!, created.id, updateRequest()) }
    }

    @Test
    fun `수정할 때도 다른 캘린더의 카테고리와 잘못된 시간은 거부한다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest())

        // when, then
        assertErrorCode(ErrorCode.CATEGORY_NOT_IN_CALENDAR) {
            eventService.update(owner.id!!, created.id, updateRequest(categoryId = otherCategory.id))
        }
        assertErrorCode(ErrorCode.INVALID_EVENT_TIME) {
            eventService.update(owner.id!!, created.id, updateRequest(startAt = end, endAt = start))
        }
    }

    // ===== 삭제 =====

    @Test
    fun `일정을 삭제하면 숨김 설정과 반복 예외도 함께 지워진다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest())
        val event = eventRepository.getReferenceById(created.id)
        persist(EventOption.create(ownerSubscribe, event, calendar, isVisible = false))
        persist(EventException.create(event, event.startAt, EventExceptionType.DELETED))
        em.flush()

        // when
        eventService.delete(owner.id!!, created.id)
        em.flush()
        em.clear()

        // then
        assertFalse(eventRepository.existsById(created.id))
        assertEquals(0L, count("EventOption"))
        assertEquals(0L, count("EventException"))
    }

    @Test
    fun `VIEWER는 일정을 삭제할 수 없다`() {
        // given
        val created = eventService.create(owner.id!!, createRequest())

        // when, then
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.delete(viewer.id!!, created.id) }
        assertTrue(eventRepository.existsById(created.id))
    }

    private fun count(entityName: String): Long =
        em.createQuery("select count(x) from $entityName x").singleResult as Long
}
