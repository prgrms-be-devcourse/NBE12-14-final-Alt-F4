package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class EventRecurrenceScopeServiceTest(
    @Autowired private val eventService: EventService,
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val eventExceptionRepository: EventExceptionRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var viewer: User
    private lateinit var calendar: Calendar
    private lateinit var otherCalendar: Calendar
    private lateinit var category: EventCategory
    private lateinit var ownerSubscribe: Subscribe

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "조회자"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        otherCalendar = persist(Calendar.create("다른 캘린더", null, null))
        category = persist(EventCategory.create(calendar, "회의", null))
        val ownerGroup = persist(CalendarGroup.create(owner, "내 캘린더", null))
        ownerSubscribe = persist(Subscribe.create(owner, ownerGroup, calendar, CalendarRole.OWNER, null))
        persist(Subscribe.create(owner, ownerGroup, otherCalendar, CalendarRole.OWNER, null))
        val viewerGroup = persist(CalendarGroup.create(viewer, "내 캘린더", null))
        persist(Subscribe.create(viewer, viewerGroup, calendar, CalendarRole.VIEWER, null))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // 한국 시각 (2026-11-16은 월요일, 한국 오전 8시 = UTC 일요일 23시)
    private fun kst(dateTime: String) = OffsetDateTime.parse("${dateTime}:00+09:00")

    // 매주 월요일 한국 08:00~09:00 반복
    private fun createWeekly(rrule: String = "FREQ=WEEKLY;BYDAY=MO", start: String = "2026-11-16T08:00", end: String = "2026-11-16T09:00") =
        eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "주간 회의",
                description = null,
                location = null,
                startAt = kst(start),
                endAt = kst(end),
                timezone = "Asia/Seoul",
                rrule = rrule,
            ),
        )

    private fun createAllDayDaily(timezone: String? = null) =
        eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "종일",
                description = null,
                location = null,
                isAllDay = true,
                startDate = LocalDate.parse("2026-11-16"),
                endDate = LocalDate.parse("2026-11-16"),
                timezone = timezone,
                rrule = "FREQ=DAILY;COUNT=5",
            ),
        )

    private fun timedUpdate(
        start: String,
        end: String,
        title: String = "주간 회의",
        rrule: String? = "FREQ=WEEKLY;BYDAY=MO",
        isAllDay: Boolean = false,
        categoryId: Long? = null,
        timezone: String? = null,
    ) = EventUpdateRequest(
        categoryId = categoryId,
        title = title,
        description = "설명",
        location = "회의실",
        isAllDay = isAllDay,
        startAt = kst(start),
        endAt = kst(end),
        startDate = if (isAllDay) LocalDate.parse(start.take(10)) else null,
        endDate = if (isAllDay) LocalDate.parse(end.take(10)) else null,
        timezone = timezone,
        rrule = rrule,
    )

    private fun allDayUpdate(date: String, title: String, timezone: String? = null) = EventUpdateRequest(
        categoryId = null,
        title = title,
        description = null,
        location = null,
        isAllDay = true,
        startDate = LocalDate.parse(date),
        endDate = LocalDate.parse(date),
        timezone = timezone,
        rrule = "FREQ=DAILY;COUNT=5",
    )

    private fun updateThis(event: EventResponse, occurrence: OffsetDateTime, request: EventUpdateRequest) =
        eventService.update(owner.id!!, event.id, request, RecurrenceScope.THIS, occurrence)

    private fun deleteThis(event: EventResponse, occurrence: OffsetDateTime) =
        eventService.delete(owner.id!!, event.id, RecurrenceScope.THIS, occurrence)

    // 한국 날짜 [from, to) 조회
    private fun findKst(fromDate: String, toDate: String, calendarIds: Collection<Long>? = null) =
        eventService.findInRange(
            owner.id!!,
            OffsetDateTime.parse("${fromDate}T00:00:00+09:00"),
            OffsetDateTime.parse("${toDate}T00:00:00+09:00"),
            calendarIds,
            "Asia/Seoul",
        )

    private fun exceptionsOf(event: EventResponse) = eventExceptionRepository.findAll().filter { it.event.id == event.id }

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    // ===== 이 일정만 수정 =====

    @Test
    fun `이 일정만 수정하면 그 회차만 바뀌고 반복 일정은 그대로다`() {
        // given: 11/16, 11/23, 11/30
        val event = createWeekly()

        // when: 11/23 회차만 10시로 옮기고 제목을 바꾼다
        val response = updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00", title = "변경된 회의"))

        // then: 응답은 바뀐 회차, occurrenceStart는 원래 회차 (한국 월요일 8시 = UTC 일요일 23시)
        assertEquals("변경된 회의", response.title)
        assertEquals(Instant.parse("2026-11-23T01:00:00Z"), response.startAt)
        assertEquals(Instant.parse("2026-11-22T23:00:00Z"), response.occurrenceStart)

        val result = findKst("2026-11-16", "2026-12-01")
        assertEquals(listOf("주간 회의", "변경된 회의", "주간 회의"), result.map { it.title })
        assertEquals(listOf("2026-11-15T23:00:00Z", "2026-11-23T01:00:00Z", "2026-11-29T23:00:00Z"), result.map { it.startAt.toString() })
        assertEquals("주간 회의", eventService.get(owner.id!!, event.id).title)
    }

    @Test
    fun `같은 회차를 두 번 수정하면 예외는 한 줄이고 마지막 값이 남는다`() {
        // given
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00", title = "첫 수정"))

        // when
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T12:00", "2026-11-23T13:00", title = "두 번째 수정"))

        // then
        val exceptions = exceptionsOf(event)
        assertEquals(1, exceptions.size)
        assertEquals("두 번째 수정", exceptions.single().title)
        assertEquals(Instant.parse("2026-11-23T03:00:00Z"), exceptions.single().startAt)
    }

    @Test
    fun `이 일정만 수정한 응답의 updatedAt은 이번 수정 시각이다`() {
        // given
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00", title = "첫 수정"))

        // when: 같은 회차를 다시 고친다 (기존 예외를 고치는 경우)
        val response = updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T12:00", "2026-11-23T13:00"))

        // then
        assertEquals(exceptionsOf(event).single().updatedAt, response.updatedAt)
    }

    @Test
    fun `같은 회차에 예외가 두 줄 생겨도 이 일정만 수정과 조회가 된다`() {
        // given: 동시 요청으로 같은 회차에 예외가 두 줄 생긴 상황
        val event = createWeekly()
        val seriesRef = eventRepository.getReferenceById(event.id)
        val recurrenceId = Instant.parse("2026-11-22T23:00:00Z")
        repeat(2) {
            eventExceptionRepository.save(
                EventException.create(
                    seriesRef, recurrenceId, EventExceptionType.MODIFIED, "중복", null, null,
                    Instant.parse("2026-11-24T23:00:00Z"), Instant.parse("2026-11-25T00:00:00Z"),
                ),
            )
        }

        // when
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-25T08:00", "2026-11-25T09:00", title = "고친 회의"))

        // then: 500 없이 수정되고, 옮긴 회차는 한 번만 나온다
        assertEquals(1, findKst("2026-11-25", "2026-11-26").size)
    }

    @Test
    fun `회차를 다른 날로 옮기면 원래 날짜에서 빠지고 옮긴 날짜에 나온다`() {
        // given
        val event = createWeekly()

        // when: 11/23(월) 회차를 11/25(수)로 옮긴다
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-25T08:00", "2026-11-25T09:00"))

        // then
        assertTrue(findKst("2026-11-23", "2026-11-24").isEmpty())
        val moved = findKst("2026-11-25", "2026-11-26").single()
        assertEquals(Instant.parse("2026-11-24T23:00:00Z"), moved.startAt)
        assertEquals(Instant.parse("2026-11-22T23:00:00Z"), moved.occurrenceStart)
    }

    @Test
    fun `반복이 끝난 뒤 날짜로 옮긴 회차도 그 날짜 조회에 나온다`() {
        // given: 11/16, 11/23 두 번만
        val event = createWeekly(rrule = "FREQ=WEEKLY;BYDAY=MO;COUNT=2")

        // when: 마지막 회차를 12/10으로 옮긴다
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-12-10T08:00", "2026-12-10T09:00"))

        // then
        assertEquals(Instant.parse("2026-12-09T23:00:00Z"), findKst("2026-12-10", "2026-12-11").single().startAt)
    }

    @Test
    fun `숨긴 반복 일정은 옮긴 회차도 나오지 않는다`() {
        // given
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-25T08:00", "2026-11-25T09:00"))

        // when
        persist(EventOption.create(ownerSubscribe, eventRepository.getReferenceById(event.id), calendar, isVisible = false))

        // then
        assertTrue(findKst("2026-11-25", "2026-11-26").isEmpty())
    }

    @Test
    fun `calendarIds로 다른 캘린더만 조회하면 옮긴 회차는 나오지 않는다`() {
        // given
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-25T08:00", "2026-11-25T09:00"))

        // then
        assertTrue(findKst("2026-11-25", "2026-11-26", calendarIds = listOf(otherCalendar.id!!)).isEmpty())
    }

    // ===== 이 일정만 삭제 =====

    @Test
    fun `이 일정만 삭제하면 그 회차만 조회에서 빠진다`() {
        // given
        val event = createWeekly()

        // when
        deleteThis(event, kst("2026-11-23T08:00"))

        // then
        assertEquals(listOf("2026-11-15T23:00:00Z", "2026-11-29T23:00:00Z"), findKst("2026-11-16", "2026-12-01").map { it.startAt.toString() })
    }

    @Test
    fun `조회 범위의 첫 회차와 마지막 회차를 지워도 조회에서 빠진다`() {
        // given
        val event = createWeekly()

        // when: 11/16, 11/30 회차를 지운다 (예외 조회 범위의 양 끝)
        deleteThis(event, kst("2026-11-16T08:00"))
        deleteThis(event, kst("2026-11-30T08:00"))

        // then
        assertEquals(listOf("2026-11-22T23:00:00Z"), findKst("2026-11-16", "2026-12-01").map { it.startAt.toString() })
    }

    @Test
    fun `수정한 회차를 삭제하면 DELETED로 바뀌고 조회에서 빠진다`() {
        // given
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-25T08:00", "2026-11-25T09:00"))

        // when
        deleteThis(event, kst("2026-11-23T08:00"))

        // then
        val exception = exceptionsOf(event).single()
        assertEquals(EventExceptionType.DELETED, exception.type)
        assertEquals(null, exception.title)
        assertTrue(findKst("2026-11-23", "2026-11-26").isEmpty())
    }

    // ===== 종일, 시간대, 서머타임, 길이 0 =====

    @Test
    fun `종일 반복 일정도 이 일정만 수정하고 삭제할 수 있다`() {
        // given: 11/16 ~ 11/20 매일
        val event = createAllDayDaily()

        // when
        updateThis(event, OffsetDateTime.parse("2026-11-17T00:00:00Z"), allDayUpdate("2026-11-17", "바뀐 종일"))
        deleteThis(event, OffsetDateTime.parse("2026-11-18T00:00:00Z"))

        // then
        val result = findKst("2026-11-16", "2026-11-21")
        assertEquals(listOf("2026-11-16", "2026-11-17", "2026-11-19", "2026-11-20"), result.map { it.startAt.toString().take(10) })
        assertEquals("바뀐 종일", result[1].title)
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), result[1].occurrenceStart)
    }

    @Test
    fun `뉴욕 시간대로 만든 종일 반복 일정도 이 일정만 삭제할 수 있다`() {
        // given: 회차 확인이 일정 시간대가 아니라 UTC 기준인지
        val event = createAllDayDaily(timezone = "America/New_York")

        // when
        deleteThis(event, OffsetDateTime.parse("2026-11-18T00:00:00Z"))

        // then
        assertEquals(EventExceptionType.DELETED, exceptionsOf(event).single().type)
    }

    @Test
    fun `서머타임 때문에 밀린 회차도 이 일정만 삭제할 수 있다`() {
        // given: 뉴욕 매일 02:30, 2026-03-08 02:30은 없는 시각이라 03:30으로 밀린다
        val event = eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "새벽 일정",
                description = null,
                location = null,
                startAt = OffsetDateTime.parse("2026-03-07T02:30:00-05:00"),
                endAt = OffsetDateTime.parse("2026-03-07T03:00:00-05:00"),
                timezone = "America/New_York",
                rrule = "FREQ=DAILY;COUNT=3",
            ),
        )

        // when: 펼치기에 나온 03:30(EDT) 회차를 지운다
        deleteThis(event, OffsetDateTime.parse("2026-03-08T03:30:00-04:00"))

        // then
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), exceptionsOf(event).single().recurrenceId)
    }

    @Test
    fun `시작과 끝이 같은 반복 일정의 회차도 이 일정만 삭제할 수 있다`() {
        // given
        val event = createWeekly(start = "2026-11-16T09:00", end = "2026-11-16T09:00")

        // when
        deleteThis(event, kst("2026-11-23T09:00"))

        // then
        assertEquals(EventExceptionType.DELETED, exceptionsOf(event).single().type)
    }

    // ===== scope 규칙 =====

    @Test
    fun `일반 일정을 반복으로 바꿀 때는 scope 없이 수정된다`() {
        // given
        val event = createWeekly(rrule = "")

        // when: 일반 일정을 처음 시간 반복으로 바꿀 때는 timezone이 필요하다
        val response = eventService.update(
            owner.id!!, event.id,
            timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", timezone = "Asia/Seoul"),
        )

        // then
        assertEquals("FREQ=WEEKLY;BYDAY=MO", response.rrule)
    }

    @Test
    fun `반복 일정의 반복을 해제할 때도 scope가 필요하다`() {
        // given
        val event = createWeekly()

        // then
        assertErrorCode(ErrorCode.RECURRENCE_SCOPE_REQUIRED) {
            eventService.update(owner.id!!, event.id, timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", rrule = null))
        }
    }

    @Test
    fun `반복 일정을 scope 없이 수정하거나 삭제하면 400 RECURRENCE_SCOPE_REQUIRED`() {
        // given
        val event = createWeekly()

        // then
        assertErrorCode(ErrorCode.RECURRENCE_SCOPE_REQUIRED) {
            eventService.update(owner.id!!, event.id, timedUpdate("2026-11-16T08:00", "2026-11-16T09:00"))
        }
        assertErrorCode(ErrorCode.RECURRENCE_SCOPE_REQUIRED) { eventService.delete(owner.id!!, event.id) }
    }

    // ===== 오류 =====

    @Test
    fun `이 일정만인데 occurrenceStart가 없으면 400 INVALID_INPUT`() {
        // given
        val event = createWeekly()

        // then
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            eventService.delete(owner.id!!, event.id, RecurrenceScope.THIS, null)
        }
    }

    @Test
    fun `실제 회차가 아닌 시각이면 404 OCCURRENCE_NOT_FOUND`() {
        // given
        val event = createWeekly()

        // then: 화요일, 같은 월요일의 다른 시각
        assertErrorCode(ErrorCode.OCCURRENCE_NOT_FOUND) { deleteThis(event, kst("2026-11-24T08:00")) }
        assertErrorCode(ErrorCode.OCCURRENCE_NOT_FOUND) {
            updateThis(event, kst("2026-11-23T08:01"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00"))
        }
    }

    @Test
    fun `지운 회차를 다시 수정하거나 삭제하면 404 OCCURRENCE_NOT_FOUND`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))

        // then: 종일 여부를 바꾼 요청이어도 지운 회차 검사가 먼저다
        assertErrorCode(ErrorCode.OCCURRENCE_NOT_FOUND) {
            updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T00:00", "2026-11-23T00:00", isAllDay = true))
        }
        assertErrorCode(ErrorCode.OCCURRENCE_NOT_FOUND) { deleteThis(event, kst("2026-11-23T08:00")) }
    }

    @Test
    fun `이 일정만 수정에서 종일 여부나 카테고리를 바꾸면 400 INVALID_INPUT`() {
        // given
        val event = createWeekly()

        // then
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T00:00", "2026-11-23T00:00", isAllDay = true))
        }
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00", categoryId = category.id))
        }
    }

    @Test
    fun `편집 권한이 없으면 없는 회차여도 403 FORBIDDEN`() {
        // given
        val event = createWeekly()

        // then: 회차가 있는지 알 수 없도록 회차 확인보다 권한 검사가 먼저다
        assertErrorCode(ErrorCode.FORBIDDEN) {
            eventService.delete(viewer.id!!, event.id, RecurrenceScope.THIS, kst("2026-11-23T08:00"))
        }
        assertErrorCode(ErrorCode.FORBIDDEN) {
            eventService.delete(viewer.id!!, event.id, RecurrenceScope.THIS, kst("2026-11-24T08:00"))
        }
        assertErrorCode(ErrorCode.FORBIDDEN) {
            eventService.update(
                viewer.id!!, event.id,
                timedUpdate("2026-11-23T10:00", "2026-11-23T11:00"),
                RecurrenceScope.THIS, kst("2026-11-23T08:00"),
            )
        }
    }

    // ===== 전체 수정과 예외 =====

    @Test
    fun `전체 수정으로 시간을 바꾸면 예외가 지워지고 지운 회차도 다시 나타난다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))

        // when: 반복 전체를 한 시간 늦춘다
        eventService.update(owner.id!!, event.id, timedUpdate("2026-11-16T09:00", "2026-11-16T10:00"), RecurrenceScope.ALL)

        // then
        assertTrue(exceptionsOf(event).isEmpty())
        assertEquals(3, findKst("2026-11-16", "2026-12-01").size)
    }

    @Test
    fun `전체 수정으로 제목만 바꾸면 예외가 남는다`() {
        // given
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-25T08:00", "2026-11-25T09:00", title = "옮긴 회의"))

        // when
        eventService.update(owner.id!!, event.id, timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", title = "새 이름"), RecurrenceScope.ALL)

        // then
        assertEquals(1, exceptionsOf(event).size)
        assertEquals(listOf("새 이름", "옮긴 회의", "새 이름"), findKst("2026-11-16", "2026-12-01").map { it.title })
    }

    @Test
    fun `전체 수정에서 같은 규칙을 다른 모양으로 보내면 예외가 남는다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))

        // when: 소문자, 순서가 다른 같은 규칙
        eventService.update(
            owner.id!!, event.id,
            timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", rrule = "byday=MO;freq=weekly"),
            RecurrenceScope.ALL,
        )

        // then
        assertEquals(1, exceptionsOf(event).size)
        assertEquals(2, findKst("2026-11-16", "2026-12-01").size)
    }

    @Test
    fun `종일 반복 일정을 전체 수정으로 시간대만 바꾸면 예외가 남는다`() {
        // given: 11/16 ~ 11/20 매일, 11/17 회차만 이름을 바꿈
        val event = createAllDayDaily(timezone = "Asia/Seoul")
        updateThis(event, OffsetDateTime.parse("2026-11-17T00:00:00Z"), allDayUpdate("2026-11-17", "바뀐 종일"))

        // when: 종일 회차는 UTC 날짜로 펼치므로 시간대만 바꿔도 회차는 그대로다
        eventService.update(owner.id!!, event.id, allDayUpdate("2026-11-16", "종일", timezone = "America/New_York"), RecurrenceScope.ALL)

        // then
        assertEquals(1, exceptionsOf(event).size)
        assertEquals("바뀐 종일", findKst("2026-11-16", "2026-11-21")[1].title)
    }

    @Test
    fun `시간 반복 일정을 전체 수정으로 시간대만 바꾸면 예외가 지워진다`() {
        // given: 11/23 회차만 이름을 바꿈
        val event = createWeekly()
        updateThis(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T08:00", "2026-11-23T09:00", title = "바뀐 회의"))

        // when: 시각은 그대로 두고 시간대만 바꾼다 (도쿄는 한국과 같은 시각이라 월요일 그대로). 시간 일정은 시간대가 회차 계산 기준이라 예외를 지운다
        eventService.update(
            owner.id!!, event.id,
            timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", timezone = "Asia/Tokyo"),
            RecurrenceScope.ALL,
        )

        // then
        assertTrue(exceptionsOf(event).isEmpty())
    }
}
