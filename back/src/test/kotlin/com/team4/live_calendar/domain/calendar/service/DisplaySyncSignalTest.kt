package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarCreateRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarDisplayRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupMoveRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.service.CategoryService
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.websocket.CalendarChangedEvent
import com.team4.live_calendar.global.websocket.DisplayChangedEvent
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
import kotlin.test.assertEquals

// 내 화면 설정(표시·숨김·내 색·그룹)을 바꾸면 내 표시 이벤트만 정확히 한 번 발행되는지 본다
@SpringBootTest
@Transactional
@RecordApplicationEvents
class DisplaySyncSignalTest(
    @Autowired private val eventService: EventService,
    @Autowired private val calendarService: CalendarService,
    @Autowired private val categoryService: CategoryService,
    @Autowired private val calendarGroupService: CalendarGroupService,
    @Autowired private val em: EntityManager,
) {

    // 생성자 주입은 안 된다 (테스트 인스턴스를 만든 뒤에 등록된다)
    @Autowired
    lateinit var events: ApplicationEvents

    private lateinit var me: User
    private lateinit var stranger: User
    private lateinit var calendar: Calendar
    private lateinit var group: CalendarGroup
    private lateinit var emptyGroup: CalendarGroup
    private lateinit var category: EventCategory
    private lateinit var event: Event

    @BeforeEach
    fun setUp() {
        me = persist(User.signUp("me@mail.com", "{bcrypt}hash", "나"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "남"))
        calendar = persist(Calendar.create("구독 캘린더", null, null))
        group = persist(CalendarGroup.create(me, "내 캘린더", 0))
        emptyGroup = persist(CalendarGroup.create(me, "빈 그룹", 1))
        // 보기만 하는 구독자도 자기 화면은 바꿀 수 있다
        persist(Subscribe.create(me, group, calendar, CalendarRole.SUBSCRIBER, null))
        category = persist(EventCategory.create(calendar, "회의", null))
        event = persist(
            Event.create(
                calendar = calendar,
                category = null,
                user = me,
                title = "구독 일정",
                description = null,
                location = null,
                startAt = Instant.parse("2026-11-18T05:00:00Z"),
                endAt = Instant.parse("2026-11-18T06:00:00Z"),
            ),
        )
    }

    @Test
    fun `내 화면 설정을 바꾸는 10곳은 각각 내 표시 이벤트를 한 번만 발행하고 캘린더 신호는 없다`() {
        // 이름 to 동작
        val cases = listOf<Pair<String, () -> Unit>>(
            "일정 숨김" to { eventService.hide(me.id!!, event.id!!) },
            "일정 복원" to { eventService.show(me.id!!, event.id!!) },
            "카테고리 숨김" to { categoryService.hide(me.id!!, category.id!!) },
            "카테고리 복원" to { categoryService.show(me.id!!, category.id!!) },
            "캘린더 표시·내 색" to {
                calendarService.changeDisplay(me.id!!, calendar.id!!, CalendarDisplayRequest(isVisible = false, color = "#FF0000"))
            },
            "캘린더 그룹 옮기기" to {
                calendarService.moveGroup(me.id!!, calendar.id!!, CalendarGroupMoveRequest(groupId = emptyGroup.id))
            },
            "캘린더 만들기" to { calendarService.create(me.id!!, CalendarCreateRequest(name = "새 캘린더")) },
            "그룹 만들기" to { calendarGroupService.create(me.id!!, CalendarGroupRequest(name = "새 그룹")) },
            "그룹 이름 바꾸기" to { calendarGroupService.rename(me.id!!, group.id!!, CalendarGroupRequest(name = "바꾼 이름")) },
            "빈 그룹 지우기" to {
                val target = calendarGroupService.create(me.id!!, CalendarGroupRequest(name = "지울 그룹"))
                events.clear()
                calendarGroupService.delete(me.id!!, target.id)
            },
        )

        cases.forEach { (name, action) ->
            // given
            events.clear()

            // when
            action()

            // then
            assertEquals(listOf(me.id!!), displaySignals(), name)
            assertEquals(0, events.stream(CalendarChangedEvent::class.java).count(), name)
        }
    }

    @Test
    fun `이미 숨긴 일정을 또 숨겨도 한 번 발행한다`() {
        // given
        eventService.hide(me.id!!, event.id!!)
        events.clear()

        // when
        eventService.hide(me.id!!, event.id!!)

        // then
        assertEquals(listOf(me.id!!), displaySignals())
    }

    @Test
    fun `이용 관계가 없으면 내 화면 설정을 바꿀 수 없고 발행하지 않는다`() {
        // when
        assertThrows<BusinessException> { eventService.hide(stranger.id!!, event.id!!) }
        assertThrows<BusinessException> { eventService.show(stranger.id!!, event.id!!) }
        assertThrows<BusinessException> { categoryService.hide(stranger.id!!, category.id!!) }
        assertThrows<BusinessException> { categoryService.show(stranger.id!!, category.id!!) }
        assertThrows<BusinessException> {
            calendarService.changeDisplay(stranger.id!!, calendar.id!!, CalendarDisplayRequest(isVisible = false))
        }
        assertThrows<BusinessException> {
            calendarService.moveGroup(stranger.id!!, calendar.id!!, CalendarGroupMoveRequest(groupId = emptyGroup.id))
        }
        assertThrows<BusinessException> { calendarGroupService.rename(stranger.id!!, group.id!!, CalendarGroupRequest(name = "남의 것")) }

        // then
        assertEquals(emptyList(), displaySignals())
    }

    @Test
    fun `캘린더가 든 그룹은 지울 수 없고 발행하지 않는다`() {
        // when
        assertThrows<BusinessException> { calendarGroupService.delete(me.id!!, group.id!!) }

        // then
        assertEquals(emptyList(), displaySignals())
    }

    private fun displaySignals(): List<Long> =
        events.stream(DisplayChangedEvent::class.java).map { it.userId }.toList()

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }
}
