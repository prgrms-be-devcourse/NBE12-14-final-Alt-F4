package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.service.CategoryService
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.websocket.CalendarChangedEvent
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 일정 수정 충돌 감지: version이 다르면 409, 바꾸는 경로마다 version이 1씩 오른다
@SpringBootTest
@Transactional
@RecordApplicationEvents
class EventVersionServiceTest(
    @Autowired private val eventService: EventService,
    @Autowired private val categoryService: CategoryService,
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val eventExceptionRepository: EventExceptionRepository,
    @Autowired private val em: EntityManager,
) {

    @Autowired
    lateinit var events: ApplicationEvents

    private lateinit var owner: User
    private lateinit var calendar: Calendar
    private lateinit var category: EventCategory

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        category = persist(EventCategory.create(calendar, "회의", null))
        val group = persist(CalendarGroup.create(owner, "내 캘린더", null))
        persist(Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // 한국 시각 (2026-11-16은 월요일)
    private fun kst(dateTime: String) = OffsetDateTime.parse("${dateTime}:00+09:00")

    private fun createSingle(categoryId: Long? = null) = eventService.create(
        owner.id!!,
        EventCreateRequest(
            calendarId = calendar.id!!,
            categoryId = categoryId,
            title = "팀 회의",
            description = null,
            location = null,
            startAt = kst("2026-11-18T14:00"),
            endAt = kst("2026-11-18T15:00"),
        ),
    )

    // 매주 월요일 한국 08:00~09:00 반복 (11/16, 11/23, 11/30, 12/7 …)
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

    private fun update(
        start: String,
        end: String,
        version: Long?,
        title: String = "제목 변경",
        rrule: String? = "FREQ=WEEKLY;BYDAY=MO",
    ) = EventUpdateRequest(
        categoryId = null,
        title = title,
        description = null,
        location = null,
        startAt = kst(start),
        endAt = kst(end),
        rrule = rrule,
        version = version,
    )

    private fun single(version: Long?, title: String = "제목 변경") =
        update("2026-11-18T14:00", "2026-11-18T16:00", version, title, rrule = null)

    private fun all(version: Long?, title: String = "새 이름") = update("2026-11-16T08:00", "2026-11-16T09:00", version, title)

    private fun updateThis(event: EventResponse, version: Long?, title: String = "옮긴 회의") =
        eventService.update(
            owner.id!!, event.id, update("2026-11-25T08:00", "2026-11-25T09:00", version, title),
            RecurrenceScope.THIS, kst("2026-11-23T08:00"),
        )

    private fun updateFollowing(event: EventResponse, version: Long?) =
        eventService.update(
            owner.id!!, event.id, update("2026-12-07T10:00", "2026-12-07T11:00", version, "새 회의"),
            RecurrenceScope.THIS_AND_FOLLOWING, kst("2026-12-07T08:00"),
        )

    // 같은 트랜잭션의 영속성 컨텍스트가 아니라 DB 값을 읽는다
    private fun reloaded(id: Long): Event {
        em.flush()
        em.clear()
        return eventRepository.findById(id).get()
    }

    private fun exceptionCount(id: Long) = eventExceptionRepository.findAll().count { it.event.id == id }

    private fun assertConflict(block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(ErrorCode.EVENT_VERSION_CONFLICT, e.errorCode)
    }

    // ===== 409 =====

    @Test
    fun `반복 없는 일정을 옛 version으로 수정하면 409이고 일정은 그대로다`() {
        // given: 다른 곳에서 먼저 고쳐 version 1
        val event = createSingle()
        eventService.update(owner.id!!, event.id, single(version = 0, title = "먼저 고친 제목"))

        // when, then
        assertConflict { eventService.update(owner.id!!, event.id, single(version = 0, title = "늦은 제목")) }
        val saved = reloaded(event.id)
        assertEquals("먼저 고친 제목", saved.title)
        assertEquals(1, saved.version)
    }

    @Test
    fun `전체 수정을 옛 version으로 하면 409이고 반복과 예외가 그대로다`() {
        // given: 다른 곳에서 이 일정만 수정해 version 1
        val event = createWeekly()
        updateThis(event, version = 0)

        // when, then: 시간을 바꾸는 전체 수정이라 통과했다면 예외가 지워졌을 것이다
        assertConflict {
            eventService.update(owner.id!!, event.id, update("2026-11-16T09:00", "2026-11-16T10:00", version = 0), RecurrenceScope.ALL)
        }
        val saved = reloaded(event.id)
        assertEquals("주간 회의", saved.title)
        assertEquals(event.startAt, saved.startAt)
        assertEquals(1, saved.version)
        assertEquals(1, exceptionCount(event.id))
    }

    @Test
    fun `이 일정만 수정을 옛 version으로 하면 409이고 예외가 생기지 않는다`() {
        // given
        val event = createWeekly()
        eventService.update(owner.id!!, event.id, all(version = 0), RecurrenceScope.ALL)

        // when, then
        assertConflict { updateThis(event, version = 0) }
        assertEquals(0, exceptionCount(event.id))
        assertEquals(1, reloaded(event.id).version)
    }

    @Test
    fun `이 일정과 이후 수정을 옛 version으로 하면 409이고 원래 반복이 그대로고 새 일정이 없다`() {
        // given
        val event = createWeekly()
        eventService.update(owner.id!!, event.id, all(version = 0), RecurrenceScope.ALL)
        val eventCount = eventRepository.count()

        // when, then
        assertConflict { updateFollowing(event, version = 0) }
        val saved = reloaded(event.id)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", saved.rrule)
        assertNull(saved.recurUntil)
        assertEquals(1, saved.version)
        assertEquals(eventCount, eventRepository.count())
    }

    @Test
    fun `version이 달라 409면 변경 이벤트를 발행하지 않는다`() {
        // given: 반복 일정과 반복 없는 일정을 한 번씩 고쳐 둔다
        val weekly = createWeekly()
        eventService.update(owner.id!!, weekly.id, all(version = 0), RecurrenceScope.ALL)
        val plain = createSingle()
        eventService.update(owner.id!!, plain.id, single(version = 0))
        events.clear()

        // when: 범위마다 옛 version으로 고친다
        assertConflict { eventService.update(owner.id!!, weekly.id, all(version = 0), RecurrenceScope.ALL) }
        assertConflict { updateThis(weekly, version = 0) }
        assertConflict { updateFollowing(weekly, version = 0) }
        assertConflict { eventService.update(owner.id!!, plain.id, single(version = 0)) }

        // then: 발행은 version 확인 뒤다
        assertEquals(0, events.stream(CalendarChangedEvent::class.java).count())
    }

    @Test
    fun `version 없이 수정하면 서비스에서 실패한다`() {
        // given
        val event = createSingle()

        // when, then: 컨트롤러에서는 @NotNull로 400이 먼저 난다
        assertThrows<IllegalArgumentException> { eventService.update(owner.id!!, event.id, single(version = null)) }
    }

    // ===== version 올리기 =====

    @Test
    fun `반복 없는 일정을 수정하면 version이 1 오른다`() {
        // given
        val event = createSingle()

        // when
        val response = eventService.update(owner.id!!, event.id, single(version = 0))

        // then
        assertEquals(1, response.version)
        assertEquals(1, reloaded(event.id).version)
    }

    @Test
    fun `전체 수정하면 version이 1 오른다`() {
        // given
        val event = createWeekly()

        // when
        val response = eventService.update(owner.id!!, event.id, all(version = 0), RecurrenceScope.ALL)

        // then
        assertEquals(1, response.version)
        assertEquals(1, reloaded(event.id).version)
    }

    @Test
    fun `이 일정만 수정하면 시리즈 version이 1 오르고 응답에도 오른 값이 온다`() {
        // given
        val event = createWeekly()

        // when
        val response = updateThis(event, version = 0)

        // then
        assertEquals(1, response.version)
        assertEquals(1, reloaded(event.id).version)
    }

    @Test
    fun `이 일정과 이후 수정하면 원래 일정은 1 오르고 새 일정은 0이다`() {
        // given
        val event = createWeekly()

        // when
        val response = updateFollowing(event, version = 0)

        // then
        assertEquals(0, response.version)
        assertEquals(1, reloaded(event.id).version)
        assertEquals(0, reloaded(response.id).version)
    }

    @Test
    fun `이 일정만 삭제하면 version이 1 오른다`() {
        // given
        val event = createWeekly()

        // when
        eventService.delete(owner.id!!, event.id, RecurrenceScope.THIS, kst("2026-11-23T08:00"))

        // then
        assertEquals(1, reloaded(event.id).version)
    }

    @Test
    fun `이 일정과 이후 삭제하면 version이 1 오른다`() {
        // given
        val event = createWeekly()

        // when
        eventService.delete(owner.id!!, event.id, RecurrenceScope.THIS_AND_FOLLOWING, kst("2026-12-07T08:00"))

        // then
        assertEquals(1, reloaded(event.id).version)
    }

    @Test
    fun `카테고리를 지우면 그 카테고리를 단 일정의 version이 1 오른다`() {
        // given
        val withCategory = createSingle(categoryId = category.id)
        val without = createSingle()

        // when
        categoryService.delete(owner.id!!, category.id!!)

        // then: 벌크 update라 영속성 컨텍스트를 비우고 다시 읽는다
        val saved = reloaded(withCategory.id)
        assertNull(saved.category)
        assertEquals(1, saved.version)
        assertEquals(0, reloaded(without.id).version)
    }

    // ===== version 그대로 =====

    @Test
    fun `만든 일정은 version 0이고 숨김과 복원은 version을 바꾸지 않는다`() {
        // given
        val event = createSingle()
        assertEquals(0, event.version)

        // when
        eventService.hide(owner.id!!, event.id)
        eventService.show(owner.id!!, event.id)

        // then
        assertEquals(0, reloaded(event.id).version)
    }

    @Test
    fun `앞 수정 응답의 version으로 다시 수정하면 성공한다`() {
        // given
        val event = createSingle()
        val first = eventService.update(owner.id!!, event.id, single(version = 0, title = "첫 수정"))

        // when
        val second = eventService.update(owner.id!!, event.id, single(version = first.version, title = "두 번째 수정"))

        // then
        assertEquals("두 번째 수정", second.title)
        assertEquals(2, second.version)
    }

    @Test
    fun `기간 조회의 반복 회차와 바뀐 회차, 상세는 모두 시리즈 version이다`() {
        // given: 11/23 회차를 11/25로 옮겨 version 1
        val event = createWeekly()
        updateThis(event, version = 0)

        // when
        val result = eventService.findInRange(
            owner.id!!, kst("2026-11-16T00:00"), kst("2026-12-01T00:00"), viewTimezone = "Asia/Seoul",
        )

        // then: 11/16, 11/25(바뀐 회차), 11/30
        assertEquals(listOf("주간 회의", "옮긴 회의", "주간 회의"), result.map { it.title })
        assertTrue(result.all { it.version == 1L })
        assertEquals(1, eventService.get(owner.id!!, event.id).version)
    }
}
