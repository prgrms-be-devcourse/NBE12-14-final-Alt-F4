package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import com.team4.live_calendar.domain.event.repository.EventOptionRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.entity.SharedEventDisplayMode
import com.team4.live_calendar.domain.room.repository.RoomSharedEventRepository
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
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class EventRecurrenceFollowingServiceTest(
    @Autowired private val eventService: EventService,
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val eventExceptionRepository: EventExceptionRepository,
    @Autowired private val eventOptionRepository: EventOptionRepository,
    @Autowired private val roomSharedEventRepository: RoomSharedEventRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var viewer: User
    private lateinit var calendar: Calendar
    private lateinit var viewerSubscribe: Subscribe

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "조회자"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        val ownerGroup = persist(CalendarGroup.create(owner, "내 캘린더", null))
        persist(Subscribe.create(owner, ownerGroup, calendar, CalendarRole.OWNER, null))
        val viewerGroup = persist(CalendarGroup.create(viewer, "내 캘린더", null))
        viewerSubscribe = persist(Subscribe.create(viewer, viewerGroup, calendar, CalendarRole.VIEWER, null))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // 한국 시각 (2026-11-16은 월요일, 한국 오전 8시 = UTC 일요일 23시)
    private fun kst(dateTime: String) = OffsetDateTime.parse("${dateTime}:00+09:00")

    // 매주 월요일 한국 08:00~09:00 반복 (11/16, 11/23, 11/30, 12/7, 12/14 …)
    private fun createWeekly(rrule: String = "FREQ=WEEKLY;BYDAY=MO") =
        eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "주간 회의",
                description = null,
                location = null,
                startAt = kst("2026-11-16T08:00"),
                endAt = kst("2026-11-16T09:00"),
                timezone = "Asia/Seoul",
                rrule = rrule,
            ),
        )

    // 11/16 ~ 11/20 매일 종일
    private fun createAllDayDaily() =
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
                rrule = "FREQ=DAILY;COUNT=5",
            ),
        )

    private fun timedUpdate(
        start: String,
        end: String,
        title: String = "주간 회의",
        rrule: String? = "FREQ=WEEKLY;BYDAY=MO",
        timezone: String? = null,
        version: Long = 0,
    ) = EventUpdateRequest(
        categoryId = null,
        title = title,
        description = null,
        location = null,
        startAt = kst(start),
        endAt = kst(end),
        timezone = timezone,
        rrule = rrule,
        version = version,
    )

    private fun allDayUpdate(date: String, rrule: String?, title: String = "새 종일", version: Long = 0) = EventUpdateRequest(
        categoryId = null,
        title = title,
        description = null,
        location = null,
        isAllDay = true,
        startDate = LocalDate.parse(date),
        endDate = LocalDate.parse(date),
        rrule = rrule,
        version = version,
    )

    private fun updateFollowing(event: EventResponse, occurrence: OffsetDateTime?, request: EventUpdateRequest, userId: Long = owner.id!!) =
        eventService.update(userId, event.id, request, RecurrenceScope.THIS_AND_FOLLOWING, occurrence)

    private fun deleteFollowing(event: EventResponse, occurrence: OffsetDateTime?, userId: Long = owner.id!!) =
        eventService.delete(userId, event.id, RecurrenceScope.THIS_AND_FOLLOWING, occurrence)

    private fun deleteThis(event: EventResponse, occurrence: OffsetDateTime) =
        eventService.delete(owner.id!!, event.id, RecurrenceScope.THIS, occurrence)

    // 한국 날짜 [from, to) 조회
    private fun findKst(fromDate: String, toDate: String, userId: Long = owner.id!!) =
        eventService.findInRange(
            userId,
            OffsetDateTime.parse("${fromDate}T00:00:00+09:00"),
            OffsetDateTime.parse("${toDate}T00:00:00+09:00"),
            viewTimezone = "Asia/Seoul",
        )

    private fun seriesOf(event: EventResponse) = eventRepository.findById(event.id).get()

    private fun exceptionsOf(eventId: Long) = eventExceptionRepository.findAll().filter { it.event.id == eventId }

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    // ===== 이후 삭제 =====

    @Test
    fun `이 일정과 이후 삭제하면 고른 회차부터 사라지고 지난 회차는 남는다`() {
        // given
        val event = createWeekly()

        // when: 12/7 회차부터 삭제
        deleteFollowing(event, kst("2026-12-07T08:00"))

        // then: 11/16, 11/23, 11/30만 남고 12/7 1초 전에 반복이 끝난다
        assertEquals(
            listOf("2026-11-15T23:00:00Z", "2026-11-22T23:00:00Z", "2026-11-29T23:00:00Z"),
            findKst("2026-11-16", "2026-12-22").map { it.startAt.toString() },
        )
        assertTrue(seriesOf(event).rrule!!.contains("UNTIL=20261206T225959Z"))
        assertEquals(Instant.parse("2026-11-30T00:00:00Z"), seriesOf(event).recurUntil)
    }

    @Test
    fun `COUNT가 있는 반복을 자르면 원래 규칙이 UNTIL로 바뀐다`() {
        // given: 10번 반복
        val event = createWeekly(rrule = "FREQ=WEEKLY;BYDAY=MO;COUNT=10")

        // when
        deleteFollowing(event, kst("2026-11-30T08:00"))

        // then
        val rrule = seriesOf(event).rrule!!
        assertFalse(rrule.contains("COUNT"))
        assertTrue(rrule.contains("UNTIL=20261129T225959Z"))
        assertEquals(2, findKst("2026-11-16", "2027-01-31").size)
    }

    @Test
    fun `종일 반복을 자르면 고른 회차 전날까지 남는다`() {
        // given: 11/16 ~ 11/20
        val event = createAllDayDaily()

        // when: 11/18부터 삭제
        deleteFollowing(event, OffsetDateTime.parse("2026-11-18T00:00:00Z"))

        // then
        assertEquals(listOf("2026-11-16", "2026-11-17"), findKst("2026-11-16", "2026-11-21").map { it.startAt.toString().take(10) })
        assertTrue(seriesOf(event).rrule!!.contains("UNTIL=20261117"))
    }

    @Test
    fun `자른 지점 뒤의 이 일정만 예외는 지워지고 앞쪽 예외는 남는다`() {
        // given: 11/23과 12/14 회차를 이 일정만 삭제
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))
        deleteThis(event, kst("2026-12-14T08:00"))

        // when: 12/7부터 삭제
        deleteFollowing(event, kst("2026-12-07T08:00"))

        // then
        assertEquals(listOf(Instant.parse("2026-11-22T23:00:00Z")), exceptionsOf(event.id).map { it.recurrenceId })
    }

    // ===== 이후 수정 =====

    @Test
    fun `이 일정과 이후 수정하면 원래 일정은 고른 회차 전까지, 새 일정은 고른 회차부터 나온다`() {
        // given
        val event = createWeekly()

        // when: 12/7부터 화요일 10시로
        val response = updateFollowing(
            event, kst("2026-12-07T08:00"),
            timedUpdate("2026-12-08T10:00", "2026-12-08T11:00", title = "화요일 회의", rrule = "FREQ=WEEKLY;BYDAY=TU"),
        )

        // then
        assertNotEquals(event.id, response.id)
        val result = findKst("2026-11-30", "2026-12-16")
        assertEquals(listOf("주간 회의", "화요일 회의", "화요일 회의"), result.map { it.title })
        assertEquals(listOf(event.id, response.id, response.id), result.map { it.id })
        assertEquals(Instant.parse("2026-12-08T01:00:00Z"), result[1].startAt)
    }

    @Test
    fun `옮긴 회차에서 이후 수정하면 원래 회차 시각 기준으로 잘리고 옮긴 예외는 지워진다`() {
        // given: 11/23 회차를 11/25로 옮김
        val event = createWeekly()
        val moved = eventService.update(
            owner.id!!, event.id, timedUpdate("2026-11-25T08:00", "2026-11-25T09:00", title = "옮긴 회의"),
            RecurrenceScope.THIS, kst("2026-11-23T08:00"),
        )

        // when: 옮긴 회차(원래 11/23)를 골라 이후 수정
        updateFollowing(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00", title = "새 회의", version = moved.version))

        // then: 11/23 전에 잘리고, 옮긴 회차는 사라진다
        assertTrue(seriesOf(event).rrule!!.contains("UNTIL=20261122T225959Z"))
        assertTrue(exceptionsOf(event.id).isEmpty())
        assertEquals(listOf("주간 회의", "새 회의"), findKst("2026-11-16", "2026-11-26").map { it.title })
    }

    @Test
    fun `원래 일정을 숨긴 사람에게는 새 일정도 보이지 않는다`() {
        // given: 조회자가 반복 일정을 숨김
        val event = createWeekly()
        persist(EventOption.create(viewerSubscribe, eventRepository.getReferenceById(event.id), calendar, isVisible = false))

        // when
        val response = updateFollowing(event, kst("2026-12-07T08:00"), timedUpdate("2026-12-07T10:00", "2026-12-07T11:00"))

        // then
        assertEquals(1, eventOptionRepository.findHiddenByEventId(response.id).size)
        assertTrue(findKst("2026-11-30", "2026-12-16", userId = viewer.id!!).isEmpty())
        assertEquals(3, findKst("2026-11-30", "2026-12-16").size)
    }

    @Test
    fun `조율방에 공유한 일정은 새 일정도 같은 참여자와 표시 방식으로 공유된다`() {
        // given
        val event = createWeekly()
        val room = persist(
            SchedulingRoom.create(
                owner, calendar, "회식 날짜", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20),
                LocalTime.of(9, 0), LocalTime.of(18, 0), "Asia/Seoul",
            ),
        )
        val member = persist(RoomMember.create(room, owner))
        persist(RoomSharedEvent.create(member, eventRepository.getReferenceById(event.id), SharedEventDisplayMode.CALENDAR_NAME_AND_TIME))

        // when
        val response = updateFollowing(event, kst("2026-12-07T08:00"), timedUpdate("2026-12-07T10:00", "2026-12-07T11:00"))

        // then
        val shared = roomSharedEventRepository.findAllByEventId(response.id).single()
        assertEquals(member.id, shared.member.id)
        assertEquals(SharedEventDisplayMode.CALENDAR_NAME_AND_TIME, shared.displayMode)
        assertEquals(1, roomSharedEventRepository.findAllByEventId(event.id).size)
    }

    @Test
    fun `새 rrule 없이 이후 수정하면 반복 없는 일정 하나가 생긴다`() {
        // given
        val event = createWeekly()

        // when
        val response = updateFollowing(event, kst("2026-12-07T08:00"), timedUpdate("2026-12-07T10:00", "2026-12-07T11:00", rrule = null))

        // then
        assertNull(response.rrule)
        assertEquals(listOf(response.id), findKst("2026-12-07", "2026-12-22").map { it.id })
    }

    @Test
    fun `새 일정 검증에 실패하면 원래 일정은 그대로다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-12-14T08:00"))

        // when: 잘못된 반복 규칙
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            updateFollowing(event, kst("2026-12-07T08:00"), timedUpdate("2026-12-07T10:00", "2026-12-07T11:00", rrule = "FREQ=HOURLY", version = 1))
        }

        // then
        assertEquals("FREQ=WEEKLY;BYDAY=MO", seriesOf(event).rrule)
        assertNull(seriesOf(event).recurUntil)
        assertEquals(1, exceptionsOf(event.id).size)
    }

    @Test
    fun `종일 반복을 시간 반복으로 이후 수정할 때 timezone이 없으면 400 INVALID_RECURRENCE`() {
        // given: 종일 반복은 시간대 없이 만들 수 있어 기본값 UTC가 저장된다
        val event = createAllDayDaily()

        // then: 저장된 UTC로 시간 반복을 계산하지 않고 막는다
        assertErrorCode(ErrorCode.INVALID_RECURRENCE) {
            updateFollowing(
                event, OffsetDateTime.parse("2026-11-18T00:00:00Z"),
                timedUpdate("2026-11-18T08:00", "2026-11-18T09:00", rrule = "FREQ=DAILY"),
            )
        }
        assertEquals("FREQ=DAILY;COUNT=5", seriesOf(event).rrule)
    }

    // ===== 새 일정 시작 검사 (앞 반복의 마지막 회차가 끝난 뒤에 시작) =====

    @Test
    fun `고른 회차보다 앞당겨도 앞 회차가 끝난 뒤면 이후 수정된다`() {
        // given: 매주 월요일 08:00 (11/16, 11/23, 11/30 …)
        val event = createWeekly()

        // when: 11/30 회차부터 일요일 10시로 (앞 회차 11/23 09:00이 끝난 뒤)
        val response = updateFollowing(
            event, kst("2026-11-30T08:00"),
            timedUpdate("2026-11-29T10:00", "2026-11-29T11:00", title = "일요일 회의", rrule = "FREQ=WEEKLY;BYDAY=SU"),
        )

        // then
        assertNotEquals(event.id, response.id)
        assertEquals(
            listOf("주간 회의", "주간 회의", "일요일 회의", "일요일 회의"),
            findKst("2026-11-16", "2026-12-07").map { it.title },
        )
    }

    @Test
    fun `앞 회차가 끝나기 전에 시작하면 400 INVALID_INPUT이고 원래 일정은 그대로다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-12-14T08:00"))
        val eventCount = eventRepository.count()

        // when: 11/30 회차부터인데 새 시작이 앞 회차(11/23 08:00~09:00) 도중
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateFollowing(event, kst("2026-11-30T08:00"), timedUpdate("2026-11-23T08:30", "2026-11-23T09:30", version = 1))
        }

        // then: 원래 반복을 자르지 않았고 새 일정도 없다
        assertEquals("FREQ=WEEKLY;BYDAY=MO", seriesOf(event).rrule)
        assertNull(seriesOf(event).recurUntil)
        assertEquals(1, exceptionsOf(event.id).size)
        assertEquals(eventCount, eventRepository.count())
    }

    @Test
    fun `앞 회차가 끝나는 시각에 시작하면 이후 수정되고 1분 앞이면 400이다`() {
        // given
        val event = createWeekly()

        // then: 앞 회차는 11/23 09:00에 끝난다
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateFollowing(event, kst("2026-11-30T08:00"), timedUpdate("2026-11-23T08:59", "2026-11-23T09:59"))
        }
        val response = updateFollowing(event, kst("2026-11-30T08:00"), timedUpdate("2026-11-23T09:00", "2026-11-23T10:00"))
        assertNotEquals(event.id, response.id)
        assertEquals(Instant.parse("2026-11-23T00:00:00Z"), response.startAt)
    }

    @Test
    fun `새 rrule 없이도 앞 회차가 끝나기 전에 시작하면 400이다`() {
        // given
        val event = createWeekly()

        // then
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateFollowing(event, kst("2026-11-30T08:00"), timedUpdate("2026-11-23T08:30", "2026-11-23T09:30", rrule = null))
        }
        assertEquals("FREQ=WEEKLY;BYDAY=MO", seriesOf(event).rrule)
    }

    @Test
    fun `종일 반복은 앞 반복의 마지막 날에 새 종일을 시작하면 400이고 다음 날이면 이후 수정된다`() {
        // given: 11/16 ~ 11/20 매일 종일
        val event = createAllDayDaily()
        val occurrence = OffsetDateTime.parse("2026-11-18T00:00:00Z")

        // then: 11/18부터 자르면 앞 반복은 11/17까지
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateFollowing(event, occurrence, allDayUpdate("2026-11-17", rrule = "FREQ=DAILY;COUNT=3"))
        }
        val response = updateFollowing(event, occurrence, allDayUpdate("2026-11-18", rrule = "FREQ=DAILY;COUNT=3"))
        assertNotEquals(event.id, response.id)
    }

    @Test
    fun `종일 반복을 한국 오전 시간 일정으로 이후 수정하면 날짜가 겹치지 않아 된다`() {
        // given: 11/16 ~ 11/20 매일 종일. 11/18부터 자르면 앞 반복은 11/17까지 (끝 = 11/18 00:00 UTC = 한국 09:00)
        val event = createAllDayDaily()
        val occurrence = OffsetDateTime.parse("2026-11-18T00:00:00Z")

        // then: 한국 11/17 저녁은 앞 반복의 마지막 날이라 400
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateFollowing(
                event, occurrence,
                timedUpdate("2026-11-17T20:00", "2026-11-17T21:00", rrule = "FREQ=DAILY", timezone = "Asia/Seoul"),
            )
        }
        // then: 한국 11/18 08:00은 UTC로는 11/17 23:00이지만 날짜로는 겹치지 않는다
        val response = updateFollowing(
            event, occurrence,
            timedUpdate("2026-11-18T08:00", "2026-11-18T09:00", title = "아침 일정", rrule = "FREQ=DAILY", timezone = "Asia/Seoul"),
        )
        assertNotEquals(event.id, response.id)
        assertEquals(Instant.parse("2026-11-17T23:00:00Z"), response.startAt)
    }

    @Test
    fun `종일 반복을 시간 일정으로 이후 수정할 때 시작을 UTC(Z)로 보내도 timezone 기준 날짜로 비교한다`() {
        // given: 11/16 ~ 11/20 매일 종일. 11/18부터 자르면 앞 반복은 11/17까지
        val event = createAllDayDaily()

        // when: 한국 11/18 08:00을 UTC(Z)로 보낸다 (브라우저 toISOString 형식)
        val response = updateFollowing(
            event, OffsetDateTime.parse("2026-11-18T00:00:00Z"),
            EventUpdateRequest(
                categoryId = null,
                title = "아침 일정",
                description = null,
                location = null,
                startAt = OffsetDateTime.parse("2026-11-17T23:00:00Z"),
                endAt = OffsetDateTime.parse("2026-11-18T00:00:00Z"),
                timezone = "Asia/Seoul",
                rrule = "FREQ=DAILY",
                version = 0,
            ),
        )

        // then
        assertNotEquals(event.id, response.id)
        assertEquals(Instant.parse("2026-11-17T23:00:00Z"), response.startAt)
    }

    @Test
    fun `회차끼리 겹치는 반복은 고른 회차 날짜 그대로 이후 수정되고 그보다 앞이면 400이다`() {
        // given: 이틀짜리 종일 일정을 매일 반복 (11/16~17, 11/17~18, 11/18~19 …), 회차끼리 하루씩 겹친다
        val event = eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "이틀 일정",
                description = null,
                location = null,
                isAllDay = true,
                startDate = LocalDate.parse("2026-11-16"),
                endDate = LocalDate.parse("2026-11-17"),
                rrule = "FREQ=DAILY;COUNT=5",
            ),
        )
        val occurrence = OffsetDateTime.parse("2026-11-18T00:00:00Z")
        fun twoDays(start: String, end: String) = EventUpdateRequest(
            categoryId = null,
            title = "새 이름",
            description = null,
            location = null,
            isAllDay = true,
            startDate = LocalDate.parse(start),
            endDate = LocalDate.parse(end),
            rrule = "FREQ=DAILY;COUNT=3",
            version = 0,
        )

        // then: 앞 회차(11/17~18)는 11/19에 끝나지만, 고른 회차 날짜(11/18)부터는 원래 반복과 같은 모양이라 허용
        assertErrorCode(ErrorCode.INVALID_INPUT) { updateFollowing(event, occurrence, twoDays("2026-11-17", "2026-11-18")) }
        val response = updateFollowing(event, occurrence, twoDays("2026-11-18", "2026-11-19"))
        assertNotEquals(event.id, response.id)
        assertEquals("새 이름", response.title)
    }

    @Test
    fun `시간 반복을 종일로 이후 수정하면 앞 회차와 같은 날은 400이고 다음 날부터 된다`() {
        // given: 매주 월요일 한국 08:00. 11/30부터 자르면 앞 회차는 11/23
        val event = createWeekly()

        // then
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            updateFollowing(event, kst("2026-11-30T08:00"), allDayUpdate("2026-11-23", rrule = "FREQ=WEEKLY;BYDAY=MO"))
        }
        val response = updateFollowing(event, kst("2026-11-30T08:00"), allDayUpdate("2026-11-24", rrule = "FREQ=WEEKLY;BYDAY=TU"))
        assertNotEquals(event.id, response.id)
    }

    // ===== 첫 회차 =====

    @Test
    fun `첫 회차에서 내용만 이후 수정하면 전체 수정과 같고 예외가 남는다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))

        // when
        val response = updateFollowing(event, kst("2026-11-16T08:00"), timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", title = "새 이름", version = 1))

        // then: 같은 일정, 예외 유지
        assertEquals(event.id, response.id)
        assertEquals("새 이름", response.title)
        assertEquals(1, exceptionsOf(event.id).size)
        assertEquals(1, eventRepository.findAll().size)
    }

    @Test
    fun `첫 회차에서 시간을 바꿔 이후 수정하면 전체 수정과 같고 예외가 지워진다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))

        // when
        val response = updateFollowing(event, kst("2026-11-16T08:00"), timedUpdate("2026-11-16T09:00", "2026-11-16T10:00", version = 1))

        // then
        assertEquals(event.id, response.id)
        assertTrue(exceptionsOf(event.id).isEmpty())
    }

    @Test
    fun `첫 회차에서 이후 삭제하면 일정과 숨김·공유·예외가 모두 지워진다`() {
        // given
        val event = createWeekly()
        val ref = eventRepository.getReferenceById(event.id)
        deleteThis(event, kst("2026-11-23T08:00"))
        persist(EventOption.create(viewerSubscribe, ref, calendar, isVisible = false))
        val room = persist(
            SchedulingRoom.create(
                owner, calendar, "회식 날짜", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20),
                LocalTime.of(9, 0), LocalTime.of(18, 0), "Asia/Seoul",
            ),
        )
        persist(RoomSharedEvent.create(persist(RoomMember.create(room, owner)), ref))

        // when
        deleteFollowing(event, kst("2026-11-16T08:00"))

        // then
        assertFalse(eventRepository.existsById(event.id))
        assertTrue(exceptionsOf(event.id).isEmpty())
        assertTrue(eventOptionRepository.findHiddenByEventId(event.id).isEmpty())
        assertTrue(roomSharedEventRepository.findAllByEventId(event.id).isEmpty())
    }

    // ===== scope 규칙과 오류 =====

    @Test
    fun `반복 없는 일정에 THIS_AND_FOLLOWING을 보내면 scope를 무시하고 수정·삭제된다`() {
        // given
        val event = createWeekly(rrule = "")

        // when
        val response = updateFollowing(event, null, timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", title = "새 이름", rrule = null))
        deleteFollowing(event, null)

        // then
        assertEquals("새 이름", response.title)
        assertFalse(eventRepository.existsById(event.id))
    }

    @Test
    fun `이후 수정·삭제의 잘못된 요청은 기존 오류 코드로 막는다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-30T08:00"))

        // then
        assertErrorCode(ErrorCode.INVALID_INPUT) { deleteFollowing(event, null) }
        assertErrorCode(ErrorCode.OCCURRENCE_NOT_FOUND) { deleteFollowing(event, kst("2026-12-08T08:00")) }
        assertErrorCode(ErrorCode.OCCURRENCE_NOT_FOUND) { deleteFollowing(event, kst("2026-11-30T08:00")) }
        assertErrorCode(ErrorCode.FORBIDDEN) { deleteFollowing(event, kst("2026-12-07T08:00"), userId = viewer.id!!) }
        assertErrorCode(ErrorCode.FORBIDDEN) {
            updateFollowing(event, kst("2026-12-07T08:00"), timedUpdate("2026-12-07T10:00", "2026-12-07T11:00"), userId = viewer.id!!)
        }
        assertEquals("FREQ=WEEKLY;BYDAY=MO", seriesOf(event).rrule)
    }
}
