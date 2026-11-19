package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@Transactional
class EventRecurrenceServiceTest(
    @Autowired private val eventService: EventService,
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var calendar: Calendar
    private lateinit var ownerSubscribe: Subscribe

    // 2026-11-16은 월요일
    private val mondayStart = OffsetDateTime.parse("2026-11-16T08:00:00+09:00")
    private val mondayEnd = OffsetDateTime.parse("2026-11-16T09:00:00+09:00")

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        val group = persist(CalendarGroup.create(owner, "내 캘린더", null))
        ownerSubscribe = persist(Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun timedRequest(
        rrule: String?,
        timezone: String? = "Asia/Seoul",
        startAt: OffsetDateTime = mondayStart,
        endAt: OffsetDateTime = mondayEnd,
        title: String = "주간 회의",
    ) = EventCreateRequest(
        calendarId = calendar.id!!,
        categoryId = null,
        title = title,
        description = null,
        location = null,
        startAt = startAt,
        endAt = endAt,
        timezone = timezone,
        rrule = rrule,
    )

    private fun allDayRequest(date: String, rrule: String? = null, endDate: String = date) = EventCreateRequest(
        calendarId = calendar.id!!,
        categoryId = null,
        title = "종일",
        description = null,
        location = null,
        isAllDay = true,
        startDate = LocalDate.parse(date),
        endDate = LocalDate.parse(endDate),
        rrule = rrule,
    )

    private fun timedUpdate(
        rrule: String?,
        timezone: String? = null,
        startAt: OffsetDateTime = mondayStart,
        endAt: OffsetDateTime = mondayEnd,
    ) = EventUpdateRequest(
        categoryId = null,
        title = "주간 회의",
        description = null,
        location = null,
        startAt = startAt,
        endAt = endAt,
        timezone = timezone,
        rrule = rrule,
    )

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    private fun recurUntilOf(response: EventResponse): Instant? = eventRepository.findById(response.id).get().recurUntil

    // 하루 조회: date 00:00 ~ 다음 날 00:00 (offset 기준)
    private fun findDay(date: String, offset: String, timezone: String?) =
        eventService.findInRange(
            owner.id!!,
            OffsetDateTime.parse("${date}T00:00:00$offset"),
            OffsetDateTime.parse("${LocalDate.parse(date).plusDays(1)}T00:00:00$offset"),
            viewTimezone = timezone,
        )

    // ===== 생성 =====

    @Test
    fun `반복 일정을 만들면 정리된 규칙과 recurUntil이 저장된다`() {
        // when
        val response = eventService.create(owner.id!!, timedRequest(" RRULE:count=3;freq=weekly "))

        // then
        assertEquals("FREQ=WEEKLY;COUNT=3", response.rrule)
        assertEquals(response.startAt, response.occurrenceStart)
        assertEquals("Asia/Seoul", response.timezone)
        assertEquals(Instant.parse("2026-11-30T00:00:00Z"), recurUntilOf(response))
    }

    @Test
    fun `잘못된 반복 규칙이면 400 INVALID_RECURRENCE`() {
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            eventService.create(owner.id!!, timedRequest("FREQ=DAILY;BYMINUTE=0"))
        }
    }

    @Test
    fun `시간 일정 반복에 시간대가 없으면 400 INVALID_RECURRENCE`() {
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY", timezone = null))
        }
    }

    @Test
    fun `종일 반복 일정은 시간대 없이 만들 수 있다`() {
        // when
        val response = eventService.create(owner.id!!, allDayRequest("2026-11-16", "FREQ=WEEKLY;UNTIL=20261130"))

        // then
        assertEquals("UTC", response.timezone)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), recurUntilOf(response))
    }

    // ===== 수정 =====

    @Test
    fun `시작 시간을 바꾸면 recurUntil을 다시 계산한다`() {
        // given
        val created = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY;COUNT=3"))

        // when: 한 시간 늦춘다
        eventService.update(
            owner.id!!, created.id,
            timedUpdate("FREQ=WEEKLY;COUNT=3", "Asia/Seoul", mondayStart.plusHours(1), mondayEnd.plusHours(1)),
        )

        // then
        assertEquals(Instant.parse("2026-11-30T01:00:00Z"), recurUntilOf(created))
    }

    @Test
    fun `반복 요일을 월요일에서 화요일로 옮길 수 있다`() {
        // given
        val created = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY;BYDAY=MO"))

        // when
        val response = eventService.update(
            owner.id!!, created.id,
            timedUpdate("FREQ=WEEKLY;BYDAY=TU", "Asia/Seoul", mondayStart.plusDays(1), mondayEnd.plusDays(1)),
        )

        // then
        assertEquals("FREQ=WEEKLY;BYDAY=TU", response.rrule)
    }

    @Test
    fun `요일만 바꾸고 시작일을 그대로 두면 400 INVALID_RECURRENCE`() {
        // given
        val created = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY;BYDAY=MO"))

        // then
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            eventService.update(owner.id!!, created.id, timedUpdate("FREQ=WEEKLY;BYDAY=TU", "Asia/Seoul"))
        }
    }

    @Test
    fun `rrule을 보내지 않으면 반복이 해제되고 시간대는 유지된다`() {
        // given
        val created = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY;COUNT=3"))

        // when
        val response = eventService.update(owner.id!!, created.id, timedUpdate(rrule = null))

        // then
        assertNull(response.rrule)
        assertNull(recurUntilOf(created))
        assertEquals("Asia/Seoul", response.timezone)
    }

    @Test
    fun `시간대 없이 만든 일반 일정을 시간 반복으로 바꿀 때 시간대가 없으면 400 INVALID_RECURRENCE`() {
        // given
        val created = eventService.create(owner.id!!, timedRequest(rrule = null, timezone = null))

        // then
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            eventService.update(owner.id!!, created.id, timedUpdate("FREQ=WEEKLY"))
        }
    }

    @Test
    fun `수정으로 시간대를 바꿀 수 있다`() {
        // given
        val created = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY"))

        // when
        val response = eventService.update(owner.id!!, created.id, timedUpdate("FREQ=WEEKLY", "America/New_York"))

        // then
        assertEquals("America/New_York", response.timezone)
    }

    @Test
    fun `종일 일정을 시간 일정으로 바꾸며 날짜 UNTIL을 그대로 보내면 400 INVALID_RECURRENCE`() {
        // given
        val created = eventService.create(owner.id!!, allDayRequest("2026-11-16", "FREQ=WEEKLY;UNTIL=20261130"))

        // then
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            eventService.update(owner.id!!, created.id, timedUpdate("FREQ=WEEKLY;UNTIL=20261130", "Asia/Seoul"))
        }
    }

    // ===== 기간 조회 =====

    @Test
    fun `기간 조회에서 반복 일정을 회차로 펼치고 일반 일정과 시간 순으로 섞는다`() {
        // given: 월요일 08:00 매주 반복 + 11/24(화) 10:00 일반 일정
        val weekly = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY"))
        val single = eventService.create(
            owner.id!!,
            timedRequest(
                rrule = null,
                startAt = OffsetDateTime.parse("2026-11-24T10:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-24T11:00:00+09:00"),
                title = "일반",
            ),
        )

        // when: 한국 11/16 ~ 12/1
        val result = eventService.findInRange(
            owner.id!!,
            OffsetDateTime.parse("2026-11-16T00:00:00+09:00"),
            OffsetDateTime.parse("2026-12-01T00:00:00+09:00"),
        )

        // then
        assertEquals(listOf(weekly.id, weekly.id, single.id, weekly.id), result.map { it.id })
        assertEquals(
            listOf("2026-11-15T23:00:00Z", "2026-11-22T23:00:00Z", "2026-11-24T01:00:00Z", "2026-11-29T23:00:00Z"),
            result.map { it.startAt.toString() },
        )
        assertEquals(result.map { it.startAt }, result.map { it.occurrenceStart })
        assertEquals(Instant.parse("2026-11-23T00:00:00Z"), result[1].endAt)
        assertEquals("FREQ=WEEKLY", result[0].rrule)
    }

    @Test
    fun `끝난 반복 일정은 이후 기간 조회에 나오지 않는다`() {
        // given: 11/16, 11/23, 11/30
        eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY;COUNT=3"))

        // when
        val result = eventService.findInRange(
            owner.id!!,
            OffsetDateTime.parse("2026-12-01T00:00:00+09:00"),
            OffsetDateTime.parse("2027-01-01T00:00:00+09:00"),
        )

        // then
        assertEquals(emptyList(), result)
    }

    @Test
    fun `내가 숨긴 반복 일정은 기간 조회에서 빠진다`() {
        // given
        val hidden = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY"))
        val visible = eventService.create(owner.id!!, timedRequest("FREQ=WEEKLY", title = "보이는 일정"))
        persist(EventOption.create(ownerSubscribe, eventRepository.getReferenceById(hidden.id), calendar, isVisible = false))

        // when
        val result = findDay("2026-11-16", "+09:00", "Asia/Seoul")

        // then
        assertEquals(listOf(visible.id), result.map { it.id })
    }

    // ===== 종일 일정 경계 =====

    @Test
    fun `한국 18일 하루 조회에 17일 종일 일정은 나오지 않는다`() {
        // given
        eventService.create(owner.id!!, allDayRequest("2026-11-17"))
        val day18 = eventService.create(owner.id!!, allDayRequest("2026-11-18"))

        // when
        val result = findDay("2026-11-18", "+09:00", "Asia/Seoul")

        // then
        assertEquals(listOf(day18.id), result.map { it.id })
    }

    @Test
    fun `뉴욕 18일 하루 조회에 19일 종일 일정은 나오지 않는다`() {
        // given
        val day18 = eventService.create(owner.id!!, allDayRequest("2026-11-18"))
        eventService.create(owner.id!!, allDayRequest("2026-11-19"))

        // when
        val result = findDay("2026-11-18", "-05:00", "America/New_York")

        // then
        assertEquals(listOf(day18.id), result.map { it.id })
    }

    @Test
    fun `종일 반복 일정도 보는 사람 시간대의 날짜로 비교한다`() {
        // given: 매일 반복 종일 일정
        val daily = eventService.create(owner.id!!, allDayRequest("2026-11-01", "FREQ=DAILY"))

        // when
        val result = findDay("2026-11-18", "+09:00", "Asia/Seoul")

        // then: 18일 회차 하나만
        assertEquals(listOf(daily.id), result.map { it.id })
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), result[0].startAt)
    }

    @Test
    fun `시간대를 보내지 않으면 UTC 날짜로 비교한다`() {
        // given
        val day17 = eventService.create(owner.id!!, allDayRequest("2026-11-17"))
        val day18 = eventService.create(owner.id!!, allDayRequest("2026-11-18"))

        // when: 한국 18일 하루 = UTC 17일 15시 ~ 18일 15시 → UTC 날짜 17일, 18일
        val result = findDay("2026-11-18", "+09:00", null)

        // then
        assertEquals(listOf(day17.id, day18.id), result.map { it.id })
    }

    @Test
    fun `잘못된 보는 사람 시간대는 400 INVALID_INPUT`() {
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            findDay("2026-11-18", "+09:00", "Mars/Base")
        }
    }
}
