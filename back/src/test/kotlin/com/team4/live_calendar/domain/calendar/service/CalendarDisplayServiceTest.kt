package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarDisplayRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.service.EventService
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
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 캘린더 표시 여부와 내 색 (내 이용 관계만 바꾼다)
@SpringBootTest
@Transactional
class CalendarDisplayServiceTest(
    @Autowired private val calendarService: CalendarService,
    @Autowired private val calendarGroupService: CalendarGroupService,
    @Autowired private val eventService: EventService,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var viewer: User
    private lateinit var subscriber: User
    private lateinit var stranger: User
    private lateinit var calendar: Calendar
    private lateinit var otherCalendar: Calendar
    private lateinit var ownerSubscribe: Subscribe
    private lateinit var viewerSubscribe: Subscribe

    private val from = OffsetDateTime.parse("2026-11-15T00:00:00+09:00")
    private val to = OffsetDateTime.parse("2026-12-01T00:00:00+09:00")

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보기만"))
        subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "남"))

        calendar = persist(Calendar.create("팀 캘린더", null, "#111111", "Asia/Seoul"))
        otherCalendar = persist(Calendar.create("개인", null, "#222222", "Asia/Seoul"))
        ownerSubscribe = subscribe(owner, calendar, CalendarRole.OWNER)
        subscribe(owner, otherCalendar, CalendarRole.OWNER)
        viewerSubscribe = subscribe(viewer, calendar, CalendarRole.VIEWER)
        subscribe(subscriber, calendar, CalendarRole.SUBSCRIBER)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun subscribe(user: User, calendar: Calendar, role: CalendarRole): Subscribe {
        val group = persist(CalendarGroup.create(user, "내 캘린더", null))
        return persist(Subscribe.create(user, group, calendar, role, null))
    }

    private fun event(calendar: Calendar, title: String, start: String, end: String, rrule: String? = null): Event =
        persist(
            Event.create(
                calendar, null, owner, title, null, null,
                Instant.parse(start), Instant.parse(end),
                timezone = "Asia/Seoul", rrule = rrule,
            ),
        )

    private fun display(user: User, calendar: Calendar, isVisible: Boolean, color: String? = null) =
        calendarService.changeDisplay(user.id!!, calendar.id!!, CalendarDisplayRequest(isVisible, color))

    private fun idsInRange(user: User, calendarIds: Collection<Long>? = null): Set<Long> =
        eventService.findInRange(user.id!!, from, to, calendarIds).map { it.id }.toSet()

    private fun myCalendar(user: User, calendar: Calendar) =
        calendarGroupService.findMyGroups(user.id!!).flatMap { it.calendars }.single { it.id == calendar.id }

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    @Test
    fun `캘린더를 끄면 그 캘린더의 단일 일정과 회차와 옮긴 회차가 모두 빠지고 다시 켜면 나온다`() {
        // given
        val single = event(calendar, "단일", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val series = event(calendar, "매일", "2026-11-16T01:00:00Z", "2026-11-16T02:00:00Z", "FREQ=DAILY;COUNT=3")
        persist(
            EventException.create(
                series, Instant.parse("2026-11-17T01:00:00Z"), EventExceptionType.MODIFIED,
                title = "옮긴 회의", startAt = Instant.parse("2026-11-25T01:00:00Z"), endAt = Instant.parse("2026-11-25T02:00:00Z"),
            ),
        )
        val mine = event(otherCalendar, "개인 일정", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when
        display(owner, calendar, isVisible = false)
        val hidden = eventService.findInRange(owner.id!!, from, to)
        display(owner, calendar, isVisible = true)
        val shown = eventService.findInRange(owner.id!!, from, to)

        // then
        assertEquals(listOf(mine.id), hidden.map { it.id })
        assertEquals(5, shown.size) // 단일 1 + 회차 2 + 옮긴 회차 1 + 개인 1
        assertEquals(setOf(single.id, series.id, mine.id), shown.map { it.id }.toSet())
    }

    @Test
    fun `숨긴 캘린더는 calendarIds로 직접 골라도 기간 조회에 나오지 않는다`() {
        // given
        event(calendar, "단일", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        display(owner, calendar, isVisible = false)

        // when
        val result = idsInRange(owner, listOf(calendar.id!!))

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `모든 캘린더를 끄면 기간 조회가 빈 목록이다`() {
        // given
        event(calendar, "팀", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        event(otherCalendar, "개인", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        display(owner, calendar, isVisible = false)
        display(owner, otherCalendar, isVisible = false)

        // when & then
        assertTrue(idsInRange(owner).isEmpty())
    }

    @Test
    fun `내 표시 설정은 다른 사람의 이용 관계와 원본 색을 바꾸지 않는다`() {
        // given
        val single = event(calendar, "단일", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when
        display(owner, calendar, isVisible = false, color = "#ff0000")

        // then
        em.flush()
        em.clear()
        val viewerRow = em.find(Subscribe::class.java, viewerSubscribe.id)
        assertTrue(viewerRow.isVisible)
        assertNull(viewerRow.color)
        assertEquals("#111111", em.find(Calendar::class.java, calendar.id).color)
        assertEquals(setOf(single.id), idsInRange(viewer))
    }

    @Test
    fun `내 색을 정하면 그룹 조회의 color와 myColor가 내 색이다`() {
        // when
        display(owner, calendar, isVisible = true, color = "#ff0000")

        // then
        val response = myCalendar(owner, calendar)
        assertEquals("#ff0000", response.color)
        assertEquals("#ff0000", response.myColor)
        assertTrue(response.isVisible)
    }

    @Test
    fun `내 색을 정한 뒤 color를 빼고 보내면 내 색이 지워지고 원본 색으로 보인다`() {
        // given
        display(owner, calendar, isVisible = true, color = "#ff0000")

        // when
        display(owner, calendar, isVisible = true, color = null)

        // then
        val response = myCalendar(owner, calendar)
        assertNull(response.myColor)
        assertEquals("#111111", response.color)
    }

    @Test
    fun `숨긴 캘린더도 그룹 조회 목록에 나오고 isVisible이 false다`() {
        // when
        display(owner, calendar, isVisible = false)

        // then
        assertFalse(myCalendar(owner, calendar).isVisible)
        assertTrue(myCalendar(owner, otherCalendar).isVisible)
    }

    @Test
    fun `보기 권한과 구독 권한도 표시 설정을 바꿀 수 있다`() {
        // when
        display(viewer, calendar, isVisible = false, color = "#00ff00")
        display(subscriber, calendar, isVisible = false)

        // then
        assertFalse(myCalendar(viewer, calendar).isVisible)
        assertEquals("#00ff00", myCalendar(viewer, calendar).myColor)
        assertFalse(myCalendar(subscriber, calendar).isVisible)
    }

    @Test
    fun `없는 캘린더는 404이고 이용 관계가 없으면 403이다`() {
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) {
            calendarService.changeDisplay(owner.id!!, Long.MAX_VALUE, CalendarDisplayRequest(false, null))
        }
        assertErrorCode(ErrorCode.FORBIDDEN) { display(stranger, calendar, isVisible = false) }
    }
}
