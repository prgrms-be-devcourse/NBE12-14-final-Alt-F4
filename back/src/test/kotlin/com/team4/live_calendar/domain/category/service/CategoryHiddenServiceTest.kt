package com.team4.live_calendar.domain.category.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.service.CalendarGroupService
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
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
import kotlin.test.assertTrue

// 카테고리 숨김·복원 (내 이용 관계 기준, 카테고리 필터 겸용)
// 테스트 트랜잭션에 합류하므로 READ_COMMITTED는 여기서 적용되지 않는다 (동작만 확인)
@SpringBootTest
@Transactional
class CategoryHiddenServiceTest(
    @Autowired private val categoryService: CategoryService,
    @Autowired private val eventService: EventService,
    @Autowired private val calendarGroupService: CalendarGroupService,
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
    private lateinit var workout: EventCategory
    private lateinit var meeting: EventCategory

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
        workout = persist(EventCategory.create(calendar, "운동", "#ef4444"))
        meeting = persist(EventCategory.create(calendar, "회의", null))
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

    private fun event(category: EventCategory?, title: String, start: String, end: String, rrule: String? = null): Event =
        persist(
            Event.create(
                calendar, category, owner, title, null, null,
                Instant.parse(start), Instant.parse(end),
                timezone = "Asia/Seoul", rrule = rrule,
            ),
        )

    private fun idsInRange(user: User): Set<Long> = eventService.findInRange(user.id!!, from, to).map { it.id }.toSet()

    // 벌크 delete는 영속성 컨텍스트를 비우지 않으므로 flush·clear 뒤 조회 쿼리로 센다
    private fun optionCount(subscribe: Subscribe, category: EventCategory): Long {
        em.flush()
        em.clear()
        return em.createQuery(
            "select count(o) from CategoryOption o where o.subscribe.id = :subscribeId and o.category.id = :categoryId",
            Long::class.javaObjectType,
        )
            .setParameter("subscribeId", subscribe.id)
            .setParameter("categoryId", category.id)
            .singleResult
    }

    private fun myCategories(user: User) =
        calendarGroupService.findMyGroups(user.id!!).flatMap { it.calendars }.single { it.id == calendar.id }.categories

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    @Test
    fun `카테고리를 숨기면 그 카테고리의 단일 일정과 회차와 옮긴 회차만 빠진다`() {
        // given
        val single = event(workout, "달리기", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val series = event(workout, "매일 운동", "2026-11-16T01:00:00Z", "2026-11-16T02:00:00Z", "FREQ=DAILY;COUNT=3")
        persist(
            EventException.create(
                series, Instant.parse("2026-11-17T01:00:00Z"), EventExceptionType.MODIFIED,
                title = "옮긴 운동", startAt = Instant.parse("2026-11-25T01:00:00Z"), endAt = Instant.parse("2026-11-25T02:00:00Z"),
            ),
        )
        val otherCategory = event(meeting, "회의", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        val noCategory = event(null, "카테고리 없음", "2026-11-18T07:00:00Z", "2026-11-18T08:00:00Z")
        val before = idsInRange(owner)

        // when
        categoryService.hide(owner.id!!, workout.id!!)

        // then
        assertEquals(setOf(single.id, series.id, otherCategory.id, noCategory.id), before)
        assertEquals(setOf(otherCategory.id, noCategory.id), idsInRange(owner))
    }

    @Test
    fun `복원하면 다시 나오고 내 설정 줄이 사라진다`() {
        // given
        val single = event(workout, "달리기", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        categoryService.hide(owner.id!!, workout.id!!)

        // when
        categoryService.show(owner.id!!, workout.id!!)

        // then
        assertEquals(setOf(single.id), idsInRange(owner))
        assertEquals(0, optionCount(ownerSubscribe, workout))
    }

    @Test
    fun `내가 숨겨도 다른 사람 화면은 그대로다`() {
        // given
        val single = event(workout, "달리기", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when
        categoryService.hide(owner.id!!, workout.id!!)

        // then
        assertEquals(setOf(single.id), idsInRange(editor))
        assertEquals(setOf(single.id), idsInRange(subscriber))
    }

    @Test
    fun `두 사람이 숨긴 뒤 한 사람이 복원하면 다른 사람은 계속 숨긴 상태다`() {
        // given
        val single = event(workout, "달리기", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")
        categoryService.hide(owner.id!!, workout.id!!)
        categoryService.hide(editor.id!!, workout.id!!)

        // when
        categoryService.show(owner.id!!, workout.id!!)

        // then
        assertEquals(setOf(single.id), idsInRange(owner))
        assertTrue(idsInRange(editor).isEmpty())
        assertEquals(0, optionCount(ownerSubscribe, workout))
        assertEquals(1, optionCount(editorSubscribe, workout))
    }

    @Test
    fun `편집 권한이 없는 보기 권한과 구독 권한도 숨길 수 있다`() {
        // given
        event(workout, "달리기", "2026-11-18T05:00:00Z", "2026-11-18T06:00:00Z")

        // when
        categoryService.hide(viewer.id!!, workout.id!!)
        categoryService.hide(subscriber.id!!, workout.id!!)

        // then
        assertTrue(idsInRange(viewer).isEmpty())
        assertTrue(idsInRange(subscriber).isEmpty())
    }

    @Test
    fun `두 번 숨겨도 설정 줄은 하나이고 숨긴 적 없는데 복원해도 예외가 없다`() {
        // when
        categoryService.hide(owner.id!!, workout.id!!)
        categoryService.hide(owner.id!!, workout.id!!)
        categoryService.show(owner.id!!, meeting.id!!)

        // then
        assertEquals(1, optionCount(ownerSubscribe, workout))
        assertEquals(0, optionCount(ownerSubscribe, meeting))
    }

    @Test
    fun `보임으로 남아 있던 설정 줄을 숨기면 줄이 늘지 않고 숨김이 된다`() {
        // given
        persist(CategoryOption.create(ownerSubscribe, workout, calendar, isVisible = true))

        // when
        categoryService.hide(owner.id!!, workout.id!!)

        // then
        assertEquals(1, optionCount(ownerSubscribe, workout))
        val option = em.createQuery("select o from CategoryOption o where o.category.id = :categoryId", CategoryOption::class.java)
            .setParameter("categoryId", workout.id)
            .singleResult
        assertFalse(option.isVisible)
    }

    @Test
    fun `없는 카테고리는 404이고 이용 관계가 없으면 403이다`() {
        assertErrorCode(ErrorCode.CATEGORY_NOT_FOUND) { categoryService.hide(owner.id!!, Long.MAX_VALUE) }
        assertErrorCode(ErrorCode.CATEGORY_NOT_FOUND) { categoryService.show(owner.id!!, Long.MAX_VALUE) }
        assertErrorCode(ErrorCode.FORBIDDEN) { categoryService.hide(stranger.id!!, workout.id!!) }
        assertErrorCode(ErrorCode.FORBIDDEN) { categoryService.show(stranger.id!!, workout.id!!) }
    }

    // ===== 그룹 조회 =====

    @Test
    fun `그룹 조회의 카테고리 isVisible은 내 설정만 따르고 color는 원본 색이다`() {
        // when
        categoryService.hide(editor.id!!, workout.id!!)
        categoryService.hide(owner.id!!, meeting.id!!)

        // then: 편집자가 숨긴 운동은 나에게 보이고, 내가 숨긴 회의만 false
        val mine = myCategories(owner).associateBy { it.id }
        assertTrue(mine.getValue(workout.id!!).isVisible)
        assertFalse(mine.getValue(meeting.id!!).isVisible)
        assertEquals("#ef4444", mine.getValue(workout.id!!).color)
        val editors = myCategories(editor).associateBy { it.id }
        assertFalse(editors.getValue(workout.id!!).isVisible)
        assertTrue(editors.getValue(meeting.id!!).isVisible)
    }
}
