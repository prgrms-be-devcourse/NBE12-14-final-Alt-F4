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
    ) = EventUpdateRequest(
        categoryId = null,
        title = title,
        description = null,
        location = null,
        startAt = kst(start),
        endAt = kst(end),
        rrule = rrule,
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
        eventService.update(
            owner.id!!, event.id, timedUpdate("2026-11-25T08:00", "2026-11-25T09:00", title = "옮긴 회의"),
            RecurrenceScope.THIS, kst("2026-11-23T08:00"),
        )

        // when: 옮긴 회차(원래 11/23)를 골라 이후 수정
        updateFollowing(event, kst("2026-11-23T08:00"), timedUpdate("2026-11-23T10:00", "2026-11-23T11:00", title = "새 회의"))

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
            updateFollowing(event, kst("2026-12-07T08:00"), timedUpdate("2026-12-07T10:00", "2026-12-07T11:00", rrule = "FREQ=HOURLY"))
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

    // ===== 첫 회차 =====

    @Test
    fun `첫 회차에서 내용만 이후 수정하면 전체 수정과 같고 예외가 남는다`() {
        // given
        val event = createWeekly()
        deleteThis(event, kst("2026-11-23T08:00"))

        // when
        val response = updateFollowing(event, kst("2026-11-16T08:00"), timedUpdate("2026-11-16T08:00", "2026-11-16T09:00", title = "새 이름"))

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
        val response = updateFollowing(event, kst("2026-11-16T08:00"), timedUpdate("2026-11-16T09:00", "2026-11-16T10:00"))

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
