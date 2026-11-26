package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.dto.CalendarDisplayRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.service.CalendarService
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
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
import kotlin.test.assertTrue

// 일정 숨김·복원·숨긴 목록 (내 화면에서만, 반복 일정은 시리즈 전체)
// 테스트 트랜잭션에 합류하므로 READ_COMMITTED는 여기서 적용되지 않는다 (동작만 확인)
@SpringBootTest
@Transactional
class EventHiddenServiceTest(
    @Autowired private val eventService: EventService,
    @Autowired private val calendarService: CalendarService,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var editor: User
    private lateinit var viewer: User
    private lateinit var subscriber: User
    private lateinit var stranger: User
    private lateinit var calendar: Calendar
    private lateinit var ownerSubscribe: Subscribe
    private lateinit var editorSubscribe: Subscribe

    private val from = OffsetDateTime.parse("2026-11-15T00:00:00+09:00")
    private val to = OffsetDateTime.parse("2026-12-01T00:00:00+09:00")

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        editor = persist(User.signUp("editor@mail.com", "{bcrypt}hash", "편집자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보기만"))
        subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "남"))

        calendar = persist(Calendar.create("팀 캘린더", null, null, "Asia/Seoul"))
        ownerSubscribe = subscribe(owner, calendar, CalendarRole.OWNER)
        editorSubscribe = subscribe(editor, calendar, CalendarRole.EDITOR)
        subscribe(viewer, calendar, CalendarRole.VIEWER)
        subscribe(subscriber, calendar, CalendarRole.SUBSCRIBER)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun subscribe(user: User, calendar: Calendar, role: CalendarRole): Subscribe {
        val group = persist(CalendarGroup.create(user, "내 캘린더", null))
        return persist(Subscribe.create(user, group, calendar, role, null))
    }

    private fun event(title: String, start: String, end: String, rrule: String? = null): Event =
        persist(
            Event.create(
                calendar, null, owner, title, null, null,
                Instant.parse(start), Instant.parse(end),
                timezone = "Asia/Seoul", rrule = rrule,
            ),
        )

    // 매일 10:00~11:00 (서울) 다섯 번, 18일 회차는 25일로 옮김 (반복이 끝난 뒤라 바뀐 시간으로만 나온다)
    private fun recurringWithMovedOccurrence(): Event {
        val series = event("매일 회의", "2026-11-16T01:00:00Z", "2026-11-16T02:00:00Z", "FREQ=DAILY;COUNT=5")
        persist(
            EventException.create(
                series, Instant.parse("2026-11-18T01:00:00Z"), EventExceptionType.MODIFIED,
                title = "옮긴 회의", startAt = Instant.parse("2026-11-25T01:00:00Z"), endAt = Instant.parse("2026-11-25T02:00:00Z"),
            ),
        )
        return series
    }

    private fun idsInRange(user: User): List<Long> = eventService.findInRange(user.id!!, from, to).map { it.id }

    // 벌크 delete는 영속성 컨텍스트를 비우지 않으므로 flush·clear 뒤 조회 쿼리로 센다
    private fun optionCount(subscribe: Subscribe, event: Event): Long {
        em.flush()
        em.clear()
        return em.createQuery(
            "select count(o) from EventOption o where o.subscribe.id = :subscribeId and o.event.id = :eventId",
            Long::class.javaObjectType,
        )
            .setParameter("subscribeId", subscribe.id)
            .setParameter("eventId", event.id)
            .singleResult
    }

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    // ===== 숨김·복원 =====

    @Test
    fun `숨기면 기간 조회에서 빠지고 복원하면 다시 나온다`() {
        // given
        val hidden = event("숨길 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val shown = event("그대로", "2026-11-18T07:00:00Z", "2026-11-18T08:00:00Z")

        // when
        eventService.hide(owner.id!!, hidden.id!!)
        val afterHide = idsInRange(owner)
        eventService.show(owner.id!!, hidden.id!!)
        val afterShow = idsInRange(owner)

        // then
        assertEquals(listOf(shown.id), afterHide)
        assertEquals(listOf(hidden.id, shown.id), afterShow)
    }

    @Test
    fun `내가 숨겨도 같은 캘린더의 다른 사람 기간 조회에는 계속 나온다`() {
        // given
        val hidden = event("숨길 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when
        eventService.hide(owner.id!!, hidden.id!!)

        // then
        assertEquals(listOf(hidden.id), idsInRange(editor))
        assertEquals(listOf(hidden.id), idsInRange(subscriber))
    }

    @Test
    fun `두 사람이 숨긴 뒤 한 사람이 복원하면 다른 사람은 계속 숨긴 상태다`() {
        // given
        val event = event("같이 숨긴 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        eventService.hide(owner.id!!, event.id!!)
        eventService.hide(editor.id!!, event.id!!)

        // when
        eventService.show(owner.id!!, event.id!!)

        // then
        assertEquals(listOf(event.id), idsInRange(owner))
        assertTrue(idsInRange(editor).isEmpty())
        assertEquals(0, optionCount(ownerSubscribe, event))
        assertEquals(1, optionCount(editorSubscribe, event))
    }

    @Test
    fun `보기 권한과 구독 권한도 숨길 수 있다`() {
        // given
        val event = event("숨길 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when
        eventService.hide(viewer.id!!, event.id!!)
        eventService.hide(subscriber.id!!, event.id!!)

        // then
        assertTrue(idsInRange(viewer).isEmpty())
        assertTrue(idsInRange(subscriber).isEmpty())
    }

    @Test
    fun `두 번 숨겨도 설정 줄은 하나이고 숨긴 적 없는데 복원해도 예외가 없다`() {
        // given
        val hidden = event("숨길 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val neverHidden = event("숨긴 적 없음", "2026-11-18T07:00:00Z", "2026-11-18T08:00:00Z")

        // when
        eventService.hide(owner.id!!, hidden.id!!)
        eventService.hide(owner.id!!, hidden.id!!)
        eventService.show(owner.id!!, neverHidden.id!!)

        // then
        assertEquals(1, optionCount(ownerSubscribe, hidden))
        assertEquals(0, optionCount(ownerSubscribe, neverHidden))
    }

    @Test
    fun `보임으로 남아 있던 설정 줄을 숨기면 줄이 늘지 않고 숨김이 된다`() {
        // given
        val event = event("일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        persist(EventOption.create(ownerSubscribe, event, calendar, isVisible = true))

        // when
        eventService.hide(owner.id!!, event.id!!)

        // then
        assertEquals(1, optionCount(ownerSubscribe, event))
        val option = em.createQuery("select o from EventOption o where o.event.id = :eventId", EventOption::class.java)
            .setParameter("eventId", event.id)
            .singleResult
        assertFalse(option.isVisible)
    }

    @Test
    fun `반복 일정을 숨기면 기간 안의 모든 회차와 옮긴 회차가 빠진다`() {
        // given
        val series = recurringWithMovedOccurrence()
        val other = event("다른 일정", "2026-11-20T05:00:00Z", "2026-11-20T06:00:00Z")
        val before = eventService.findInRange(owner.id!!, from, to).filter { it.id == series.id }

        // when
        eventService.hide(owner.id!!, series.id!!)

        // then
        assertEquals(5, before.size) // 회차 넷 + 옮긴 회차 하나
        assertEquals(listOf(other.id), idsInRange(owner))
    }

    @Test
    fun `없는 일정은 404이고 이용 관계가 없으면 403이며 설정 줄이 생기지 않는다`() {
        // given
        val event = event("일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when & then
        assertErrorCode(ErrorCode.EVENT_NOT_FOUND) { eventService.hide(owner.id!!, Long.MAX_VALUE) }
        assertErrorCode(ErrorCode.EVENT_NOT_FOUND) { eventService.show(owner.id!!, Long.MAX_VALUE) }
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.hide(stranger.id!!, event.id!!) }
        assertErrorCode(ErrorCode.FORBIDDEN) { eventService.show(stranger.id!!, event.id!!) }
        em.flush()
        em.clear()
        val total = em.createQuery("select count(o) from EventOption o", Long::class.javaObjectType).singleResult
        assertEquals(0, total)
    }

    // ===== 숨긴 목록 =====

    @Test
    fun `숨긴 목록은 내가 숨긴 일정만 시작 시각 순서로 보여 주고 반복 일정은 시리즈 한 줄이다`() {
        // given
        val later = event("나중 일정", "2026-11-20T05:00:00Z", "2026-11-20T06:00:00Z")
        val series = recurringWithMovedOccurrence()
        val hiddenByEditor = event("편집자가 숨김", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val visibleRow = event("보임 줄", "2026-11-19T05:00:00Z", "2026-11-19T06:00:00Z")
        persist(EventOption.create(ownerSubscribe, visibleRow, calendar, isVisible = true))
        eventService.hide(owner.id!!, later.id!!)
        eventService.hide(owner.id!!, series.id!!)
        eventService.hide(editor.id!!, hiddenByEditor.id!!)

        // when
        val result = eventService.findHidden(owner.id!!)

        // then
        assertEquals(listOf(series.id, later.id), result.map { it.id })
        assertEquals(Instant.parse("2026-11-16T01:00:00Z"), result.first().startAt)
        assertEquals("FREQ=DAILY;COUNT=5", result.first().rrule)
    }

    @Test
    fun `숨긴 캘린더의 숨긴 일정도 숨긴 목록에 나온다`() {
        // given
        val event = event("숨긴 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        eventService.hide(owner.id!!, event.id!!)
        calendarService.changeDisplay(owner.id!!, calendar.id!!, CalendarDisplayRequest(isVisible = false))

        // when
        val result = eventService.findHidden(owner.id!!)

        // then
        assertEquals(listOf(event.id), result.map { it.id })
    }

    // ===== 조율방 =====

    @Test
    fun `숨겨도 조율방에 공유한 일정은 그대로 남는다`() {
        // given
        val event = event("공유한 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val roomCalendar = persist(Calendar.create("조율방 공통", null, null, "Asia/Seoul"))
        val room = persist(
            SchedulingRoom.create(
                owner, roomCalendar, "모임", LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 28),
                LocalTime.of(9, 0), LocalTime.of(18, 0), "Asia/Seoul",
            ),
        )
        val shared = persist(RoomSharedEvent.create(persist(RoomMember.create(room, owner)), event))

        // when
        eventService.hide(owner.id!!, event.id!!)

        // then
        em.flush()
        em.clear()
        val count = em.createQuery("select count(s) from RoomSharedEvent s where s.id = :id", Long::class.javaObjectType)
            .setParameter("id", shared.id)
            .singleResult
        assertEquals(1, count)
    }
}
