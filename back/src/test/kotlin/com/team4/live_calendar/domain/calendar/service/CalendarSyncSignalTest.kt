package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarUpdateRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.dto.CategoryCreateRequest
import com.team4.live_calendar.domain.category.dto.CategoryUpdateRequest
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.service.CategoryService
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.websocket.CalendarChangedEvent
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
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 캘린더·카테고리를 고치거나 지우면 그 캘린더로 변경 이벤트가 정확히 한 번 발행되는지 본다
@SpringBootTest
@Transactional
@RecordApplicationEvents
class CalendarSyncSignalTest(
    @Autowired private val calendarService: CalendarService,
    @Autowired private val categoryService: CategoryService,
    @Autowired private val em: EntityManager,
) {

    // 생성자 주입은 안 된다 (테스트 인스턴스를 만든 뒤에 등록된다)
    @Autowired
    lateinit var events: ApplicationEvents

    private lateinit var owner: User
    private lateinit var editor: User
    private lateinit var viewer: User
    private lateinit var calendar: Calendar
    private lateinit var category: EventCategory

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        editor = persist(User.signUp("editor@mail.com", "{bcrypt}hash", "편집자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보기만"))
        calendar = persist(Calendar.create("팀 캘린더", null, null))
        category = persist(EventCategory.create(calendar, "회의", null))
        subscribe(owner, CalendarRole.OWNER)
        subscribe(editor, CalendarRole.EDITOR)
        subscribe(viewer, CalendarRole.VIEWER)
    }

    // ===== 캘린더 =====

    @Test
    fun `캘린더를 고치면 전체 기간으로 한 번 알린다`() {
        // when
        calendarService.update(owner.id!!, calendar.id!!, CalendarUpdateRequest(name = "새 이름"))

        // then
        val signal = singleSignal()
        assertEquals(SyncType.CALENDAR_UPDATED, signal.type)
        assertEquals(calendar.id, signal.calendarId)
        assertNull(signal.eventId)
        assertNull(signal.from)
        assertNull(signal.to)
    }

    @Test
    fun `OWNER가 아니면 캘린더를 고치거나 지워도 알리지 않는다`() {
        // when
        assertThrows<BusinessException> {
            calendarService.update(editor.id!!, calendar.id!!, CalendarUpdateRequest(name = "새 이름"))
        }
        assertThrows<BusinessException> { calendarService.delete(editor.id!!, calendar.id!!) }

        // then
        assertEquals(0, signals().size)
    }

    @Test
    fun `캘린더를 지우면 한 번 알린다`() {
        // when
        calendarService.delete(owner.id!!, calendar.id!!)

        // then
        val signal = singleSignal()
        assertEquals(SyncType.CALENDAR_DELETED, signal.type)
        assertEquals(calendar.id, signal.calendarId)
    }

    @Test
    fun `조율방이 쓰는 캘린더라 지우지 못하면 알리지 않는다`() {
        // given
        persist(
            SchedulingRoom.create(
                owner = owner,
                calendar = calendar,
                name = "모임",
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 7),
                dailyStartTime = LocalTime.of(9, 0),
                dailyEndTime = LocalTime.of(18, 0),
                timezone = "UTC",
            ),
        )

        // when
        assertThrows<BusinessException> { calendarService.delete(owner.id!!, calendar.id!!) }

        // then
        assertEquals(0, signals().size)
    }

    // ===== 카테고리 =====

    @Test
    fun `카테고리를 만들고 고치고 지우면 그 캘린더로 각각 한 번씩 알린다`() {
        // 동작 이름 to 동작
        val cases = listOf<Pair<String, () -> Unit>>(
            "생성" to { categoryService.create(editor.id!!, CategoryCreateRequest(calendarId = calendar.id, name = "새 카테고리")) },
            "수정" to { categoryService.update(editor.id!!, category.id!!, CategoryUpdateRequest(name = "새 이름")) },
            "삭제" to { categoryService.delete(editor.id!!, category.id!!) },
        )

        cases.forEach { (name, action) ->
            // given
            events.clear()

            // when
            action()

            // then
            val signals = signals()
            assertEquals(1, signals.size, name)
            assertEquals(SyncType.CATEGORIES_CHANGED, signals.single().type, name)
            assertEquals(calendar.id, signals.single().calendarId, name)
            assertNull(signals.single().from, name)
        }
    }

    @Test
    fun `편집 권한이 없으면 카테고리를 바꿔도 알리지 않는다`() {
        // when
        assertThrows<BusinessException> {
            categoryService.create(viewer.id!!, CategoryCreateRequest(calendarId = calendar.id, name = "새 카테고리"))
        }
        assertThrows<BusinessException> {
            categoryService.update(viewer.id!!, category.id!!, CategoryUpdateRequest(name = "새 이름"))
        }
        assertThrows<BusinessException> { categoryService.delete(viewer.id!!, category.id!!) }

        // then
        assertEquals(0, signals().size)
    }

    // ===== 도우미 =====

    private fun signals(): List<CalendarChangedEvent> = events.stream(CalendarChangedEvent::class.java).toList()

    private fun singleSignal(): CalendarChangedEvent {
        val signals = signals()
        assertEquals(1, signals.size)
        return signals.single()
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun subscribe(user: User, role: CalendarRole) {
        val group = persist(CalendarGroup.create(user, "내 캘린더", null))
        persist(Subscribe.create(user, group, calendar, role, null))
    }
}
