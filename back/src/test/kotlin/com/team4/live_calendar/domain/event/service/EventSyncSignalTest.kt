package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.websocket.CalendarChangedEvent
import com.team4.live_calendar.global.websocket.DisplayChangedEvent
import com.team4.live_calendar.global.websocket.SyncType
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 일정을 만들고 고치고 지우면 변경 이벤트가 정확히 한 번 발행되는지 본다 (실제 전송은 커밋 뒤라 여기서는 이벤트만 본다)
@SpringBootTest
@Transactional
@RecordApplicationEvents
class EventSyncSignalTest(
    @Autowired private val eventService: EventService,
    @Autowired private val em: EntityManager,
) {

    // 생성자 주입은 안 된다 (테스트 인스턴스를 만든 뒤에 등록된다)
    @Autowired
    lateinit var events: ApplicationEvents

    private lateinit var owner: User
    private lateinit var stranger: User
    private lateinit var calendar: Calendar

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "남"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        val group = persist(CalendarGroup.create(owner, "내 캘린더", null))
        persist(Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null))
    }

    // ===== 생성 =====

    @Test
    fun `시간 일정을 만들면 그 기간으로 한 번 알린다`() {
        // when
        val created = createTimed()

        // then
        val signal = singleSignal()
        assertEquals(SyncType.EVENTS_CHANGED, signal.type)
        assertEquals(calendar.id, signal.calendarId)
        assertEquals(created.id, signal.eventId)
        assertEquals(Instant.parse("2026-11-18T05:00:00Z"), signal.from)
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), signal.to)
    }

    @Test
    fun `종일 일정을 만들면 앞뒤로 하루씩 넓힌 기간으로 알린다`() {
        // when: 11/18 하루 (저장은 11/18 00:00Z ~ 11/19 00:00Z)
        createAllDay("2026-11-18")

        // then
        val signal = singleSignal()
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), signal.from)
        assertEquals(Instant.parse("2026-11-20T00:00:00Z"), signal.to)
    }

    @Test
    fun `반복 일정을 만들면 기간 없이 전체로 알린다`() {
        // when
        val created = createWeekly()

        // then
        val signal = singleSignal()
        assertEquals(created.id, signal.eventId)
        assertNull(signal.from)
        assertNull(signal.to)
    }

    // ===== 반복 없는 일정 수정 =====

    @Test
    fun `반복 없는 일정을 옮기면 옮기기 전과 후를 합친 기간으로 한 번 알린다`() {
        // given: 한국 14~15시
        val event = createTimed()
        events.clear()

        // when: 한국 16~17시로
        eventService.update(owner.id!!, event.id, timedUpdate("2026-11-18T16:00", "2026-11-18T17:00", rrule = null))

        // then
        val signal = singleSignal()
        assertEquals(event.id, signal.eventId)
        assertEquals(Instant.parse("2026-11-18T05:00:00Z"), signal.from)
        assertEquals(Instant.parse("2026-11-18T08:00:00Z"), signal.to)
    }

    @Test
    fun `시간 일정을 종일 일정으로 바꾸면 종일 쪽을 넓혀서 합친다`() {
        // given: 11/18 한국 14~15시
        val event = createTimed()
        events.clear()

        // when: 11/20 종일 (저장은 11/20 00:00Z ~ 11/21 00:00Z, 넓히면 11/19 ~ 11/22)
        eventService.update(owner.id!!, event.id, allDayUpdate("2026-11-20", rrule = null))

        // then
        val signal = singleSignal()
        assertEquals(Instant.parse("2026-11-18T05:00:00Z"), signal.from)
        assertEquals(Instant.parse("2026-11-22T00:00:00Z"), signal.to)
    }

    @Test
    fun `반복 없는 일정을 반복으로 바꾸면 전체로 알린다`() {
        // given
        val event = createTimed()
        events.clear()

        // when
        eventService.update(
            owner.id!!,
            event.id,
            timedUpdate("2026-11-18T14:00", "2026-11-18T15:00", rrule = "FREQ=DAILY;COUNT=3", timezone = "Asia/Seoul"),
        )

        // then
        val signal = singleSignal()
        assertNull(signal.from)
        assertNull(signal.to)
    }

    // ===== 반복 일정 수정 =====

    @Test
    fun `반복 일정은 어느 범위로 고쳐도 원래 일정 id와 전체 기간으로 정확히 한 번 알린다`() {
        // 범위 이름 to 수정 동작 (11/16 첫 회차, 11/23 두 번째 회차)
        val cases = listOf<Pair<String, (EventResponse) -> Unit>>(
            "이 일정만" to { e ->
                eventService.update(owner.id!!, e.id, timedUpdate("2026-11-23T10:00", "2026-11-23T11:00"), RecurrenceScope.THIS, kst("2026-11-23T08:00"))
            },
            "두 번째 회차부터" to { e ->
                eventService.update(owner.id!!, e.id, timedUpdate("2026-11-23T10:00", "2026-11-23T11:00"), RecurrenceScope.THIS_AND_FOLLOWING, kst("2026-11-23T08:00"))
            },
            "첫 회차부터" to { e ->
                eventService.update(owner.id!!, e.id, timedUpdate("2026-11-16T10:00", "2026-11-16T11:00"), RecurrenceScope.THIS_AND_FOLLOWING, kst("2026-11-16T08:00"))
            },
            "모든 일정" to { e ->
                eventService.update(owner.id!!, e.id, timedUpdate("2026-11-16T10:00", "2026-11-16T11:00"), RecurrenceScope.ALL)
            },
        )

        cases.forEach { (name, update) ->
            // given
            val event = createWeekly()
            events.clear()

            // when
            update(event)

            // then
            val signals = signals()
            assertEquals(1, signals.size, name)
            assertEquals(event.id, signals.single().eventId, name)
            assertNull(signals.single().from, name)
            assertNull(signals.single().to, name)
        }
    }

    @Test
    fun `반복 일정을 범위 없이 고치면 이벤트를 발행한 뒤 실패한다`() {
        // given
        val event = createWeekly()
        events.clear()

        // when
        assertThrows<BusinessException> {
            eventService.update(owner.id!!, event.id, timedUpdate("2026-11-16T10:00", "2026-11-16T11:00"))
        }

        // then: 발행 위치가 범위 확인보다 앞이라 이벤트는 기록된다
        // 롤백되어 실제로 보내지 않는 것은 CalendarSyncStompTest에서 본다 (여기는 테스트 트랜잭션이라 커밋하지 않는다)
        assertEquals(1, signals().size)
    }

    // ===== 삭제 =====

    @Test
    fun `반복 없는 일정을 지우면 그 기간으로 한 번 알린다`() {
        // given
        val event = createTimed()
        events.clear()

        // when
        eventService.delete(owner.id!!, event.id)

        // then
        val signal = singleSignal()
        assertEquals(event.id, signal.eventId)
        assertEquals(Instant.parse("2026-11-18T05:00:00Z"), signal.from)
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), signal.to)
    }

    @Test
    fun `종일 일정을 지우면 앞뒤로 하루씩 넓힌 기간으로 알린다`() {
        // given
        val event = createAllDay("2026-11-18")
        events.clear()

        // when
        eventService.delete(owner.id!!, event.id)

        // then
        val signal = singleSignal()
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), signal.from)
        assertEquals(Instant.parse("2026-11-20T00:00:00Z"), signal.to)
    }

    @Test
    fun `반복 일정은 어느 범위로 지워도 전체 기간으로 정확히 한 번 알린다`() {
        // 범위 이름 to (범위, 회차)
        val cases = listOf(
            "이 일정만" to (RecurrenceScope.THIS to kst("2026-11-23T08:00")),
            "두 번째 회차부터" to (RecurrenceScope.THIS_AND_FOLLOWING to kst("2026-11-23T08:00")),
            "첫 회차부터" to (RecurrenceScope.THIS_AND_FOLLOWING to kst("2026-11-16T08:00")),
            "모든 일정" to (RecurrenceScope.ALL to null),
        )

        cases.forEach { (name, scopeAndOccurrence) ->
            // given
            val event = createWeekly()
            events.clear()

            // when
            eventService.delete(owner.id!!, event.id, scopeAndOccurrence.first, scopeAndOccurrence.second)

            // then
            val signals = signals()
            assertEquals(1, signals.size, name)
            assertEquals(event.id, signals.single().eventId, name)
            assertNull(signals.single().from, name)
            assertNull(signals.single().to, name)
        }
    }

    // ===== 실패 =====

    @Test
    fun `권한이 없거나 캘린더가 없으면 알리지 않는다`() {
        // given
        val event = createTimed()
        events.clear()

        // when
        assertThrows<BusinessException> { eventService.create(stranger.id!!, timedCreate()) }
        assertThrows<BusinessException> {
            eventService.update(stranger.id!!, event.id, timedUpdate("2026-11-18T16:00", "2026-11-18T17:00", rrule = null))
        }
        assertThrows<BusinessException> { eventService.delete(stranger.id!!, event.id) }
        assertThrows<BusinessException> { eventService.create(owner.id!!, timedCreate(calendarId = Long.MAX_VALUE)) }

        // then
        assertEquals(0, signals().size)
    }

    @Test
    fun `일정 숨김과 복원은 캘린더 신호 대신 내 표시 신호를 보낸다`() {
        // given
        val event = createTimed()
        events.clear()

        // when
        eventService.hide(owner.id!!, event.id)
        eventService.show(owner.id!!, event.id)

        // then: 내 화면만 바뀌므로 다른 사람에게 가는 캘린더 신호는 없다
        assertEquals(0, signals().size)
        assertEquals(
            listOf(owner.id!!, owner.id!!),
            events.stream(DisplayChangedEvent::class.java).map { it.userId }.toList(),
        )
    }

    // ===== 도우미 =====

    private fun signals(): List<CalendarChangedEvent> = events.stream(CalendarChangedEvent::class.java).toList()

    private fun singleSignal(): CalendarChangedEvent {
        val signals = signals()
        assertEquals(1, signals.size)
        return signals.single()
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // 한국 시각 (2026-11-16은 월요일)
    private fun kst(dateTime: String) = OffsetDateTime.parse("${dateTime}:00+09:00")

    private fun timedCreate(calendarId: Long = calendar.id!!) = EventCreateRequest(
        calendarId = calendarId,
        categoryId = null,
        title = "팀 회의",
        description = null,
        location = null,
        startAt = kst("2026-11-18T14:00"),
        endAt = kst("2026-11-18T15:00"),
    )

    // 11/18 한국 14~15시 (UTC 05~06시)
    private fun createTimed() = eventService.create(owner.id!!, timedCreate())

    private fun createAllDay(date: String) = eventService.create(
        owner.id!!,
        EventCreateRequest(
            calendarId = calendar.id!!,
            categoryId = null,
            title = "워크숍",
            description = null,
            location = null,
            isAllDay = true,
            startDate = LocalDate.parse(date),
            endDate = LocalDate.parse(date),
        ),
    )

    // 매주 월요일 한국 08:00~09:00 반복 (11/16, 11/23, …)
    private fun createWeekly() = eventService.create(
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
            rrule = "FREQ=WEEKLY;BYDAY=MO",
        ),
    )

    private fun timedUpdate(
        start: String,
        end: String,
        rrule: String? = "FREQ=WEEKLY;BYDAY=MO",
        timezone: String? = null,
    ) = EventUpdateRequest(
        categoryId = null,
        title = "제목 변경",
        description = null,
        location = null,
        startAt = kst(start),
        endAt = kst(end),
        timezone = timezone,
        rrule = rrule,
    )

    private fun allDayUpdate(date: String, rrule: String?) = EventUpdateRequest(
        categoryId = null,
        title = "종일로",
        description = null,
        location = null,
        isAllDay = true,
        startDate = LocalDate.parse(date),
        endDate = LocalDate.parse(date),
        rrule = rrule,
    )
}
