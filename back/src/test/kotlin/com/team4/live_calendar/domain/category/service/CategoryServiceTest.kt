package com.team4.live_calendar.domain.category.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.dto.CategoryCreateRequest
import com.team4.live_calendar.domain.category.dto.CategoryUpdateRequest
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class CategoryServiceTest(
    @Autowired private val categoryService: CategoryService,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var editor: User
    private lateinit var viewer: User
    private lateinit var subscriber: User
    private lateinit var stranger: User
    private lateinit var groups: Map<User, CalendarGroup>

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "주인"))
        editor = persist(User.signUp("editor@mail.com", "{bcrypt}hash", "편집자"))
        viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보는 사람"))
        subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "모르는 사람"))
        groups = listOf(owner, editor, viewer, subscriber).associateWith { persist(CalendarGroup.create(it, "그룹", 0)) }
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // 캘린더 하나에 네 역할의 이용 관계를 둔다 (한 사람은 같은 캘린더에 한 줄만, stranger는 이용 관계 없음)
    private inner class Shared(name: String = "스터디") {
        val calendar: Calendar = persist(Calendar.create(name, null, "#000000"))
        val ownerSubscribe = place(owner, CalendarRole.OWNER)
        val editorSubscribe = place(editor, CalendarRole.EDITOR)
        val viewerSubscribe = place(viewer, CalendarRole.VIEWER)
        val subscriberSubscribe = place(subscriber, CalendarRole.SUBSCRIBER)

        private fun place(user: User, role: CalendarRole) =
            persist(Subscribe.create(user, groups.getValue(user), calendar, role, null))

        fun category(name: String = "멘토링", color: String? = "#4285F4") = persist(EventCategory.create(calendar, name, color))
    }

    private fun createRequest(calendarId: Long?, name: String = "멘토링", color: String? = "#4285F4") =
        CategoryCreateRequest(calendarId = calendarId, name = name, color = color)

    private fun updateRequest(name: String = "1:1 멘토링", color: String? = "#34A853") =
        CategoryUpdateRequest(name = name, color = color)

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    private fun clearContext() {
        em.flush()
        em.clear()
    }

    private fun categoryCount(calendar: Calendar): Long =
        em.createQuery("select count(c) from EventCategory c where c.calendar.id = :calendarId", Long::class.javaObjectType)
            .setParameter("calendarId", calendar.id)
            .singleResult

    private fun event(calendar: Calendar, category: EventCategory?, title: String, rrule: String? = null): Event {
        val start = Instant.parse("2026-12-01T01:00:00Z")
        return persist(
            Event.create(
                calendar = calendar,
                category = category,
                user = owner,
                title = title,
                description = null,
                location = null,
                startAt = start,
                endAt = start.plus(Duration.ofHours(1)),
                rrule = rrule,
            ),
        )
    }

    // ===== 생성 =====

    @Test
    fun `OWNER와 EDITOR는 카테고리를 만들 수 있고 응답의 calendarId는 그 캘린더다`() {
        // given
        val shared = Shared()

        listOf(owner, editor).forEach { user ->
            // when
            val created = categoryService.create(user.id!!, createRequest(shared.calendar.id))
            clearContext()

            // then
            assertEquals(shared.calendar.id, created.calendarId)
            assertEquals("멘토링", created.name)
            assertEquals("#4285F4", created.color)
            val saved = em.find(EventCategory::class.java, created.id)
            assertNotNull(saved)
            assertEquals(shared.calendar.id, saved.calendar.id)
        }
    }

    @Test
    fun `VIEWER와 SUBSCRIBER와 이용 관계가 없는 사람이 만들면 403 FORBIDDEN이고 카테고리가 생기지 않는다`() {
        // given
        val shared = Shared()

        listOf(viewer, subscriber, stranger).forEach { user ->
            // when
            assertErrorCode(ErrorCode.FORBIDDEN) { categoryService.create(user.id!!, createRequest(shared.calendar.id)) }
        }

        // then
        assertEquals(0, categoryCount(shared.calendar))
    }

    @Test
    fun `없는 캘린더에 만들면 404 CALENDAR_NOT_FOUND`() {
        // when, then
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) { categoryService.create(owner.id!!, createRequest(Long.MAX_VALUE)) }
    }

    @Test
    fun `카테고리 이름의 앞뒤 공백은 지워진다`() {
        // given
        val shared = Shared()

        // when
        val created = categoryService.create(owner.id!!, createRequest(shared.calendar.id, name = "  멘토링  "))
        clearContext()

        // then
        assertEquals("멘토링", created.name)
        assertEquals("멘토링", em.find(EventCategory::class.java, created.id).name)
    }

    @Test
    fun `같은 캘린더에 같은 이름의 카테고리를 또 만들 수 있다`() {
        // given
        val shared = Shared()

        // when
        val first = categoryService.create(owner.id!!, createRequest(shared.calendar.id))
        val second = categoryService.create(owner.id!!, createRequest(shared.calendar.id))

        // then
        assertNotEquals(first.id, second.id)
        assertEquals(2, categoryCount(shared.calendar))
    }

    // ===== 수정 =====

    @Test
    fun `OWNER와 EDITOR는 이름과 색을 바꿀 수 있고 이름의 앞뒤 공백은 지워진다`() {
        // given
        val shared = Shared()

        listOf(owner, editor).forEach { user ->
            val category = shared.category()

            // when
            val updated = categoryService.update(user.id!!, category.id!!, updateRequest(name = "  1:1 멘토링 ", color = "#34A853"))
            clearContext()

            // then
            assertEquals("1:1 멘토링", updated.name)
            assertEquals("#34A853", updated.color)
            assertEquals(shared.calendar.id, updated.calendarId)
            val saved = em.find(EventCategory::class.java, category.id)
            assertEquals("1:1 멘토링", saved.name)
            assertEquals("#34A853", saved.color)
        }
    }

    @Test
    fun `수정에서 색을 보내지 않으면 색이 비워진다`() {
        // given
        val category = Shared().category(color = "#4285F4")

        // when
        val updated = categoryService.update(owner.id!!, category.id!!, updateRequest(color = null))
        clearContext()

        // then
        assertNull(updated.color)
        assertNull(em.find(EventCategory::class.java, category.id).color)
    }

    @Test
    fun `VIEWER와 SUBSCRIBER와 이용 관계가 없는 사람이 고치면 403 FORBIDDEN이고 카테고리가 그대로 남는다`() {
        // given
        val category = Shared().category(name = "멘토링", color = "#4285F4")

        listOf(viewer, subscriber, stranger).forEach { user ->
            // when
            assertErrorCode(ErrorCode.FORBIDDEN) { categoryService.update(user.id!!, category.id!!, updateRequest()) }
        }
        clearContext()

        // then
        val saved = em.find(EventCategory::class.java, category.id)
        assertEquals("멘토링", saved.name)
        assertEquals("#4285F4", saved.color)
    }

    @Test
    fun `없는 카테고리를 고치면 404 CATEGORY_NOT_FOUND`() {
        // when, then
        assertErrorCode(ErrorCode.CATEGORY_NOT_FOUND) { categoryService.update(owner.id!!, Long.MAX_VALUE, updateRequest()) }
    }

    // ===== 삭제 =====

    // 지울 카테고리와 같은 캘린더의 다른 카테고리에 일정·설정을 한 줄 이상씩 넣는다
    // (하나라도 비면 그 삭제 단계를 빼도 테스트가 통과한다)
    private inner class Filled(eventCount: Int = 2, optionCount: Int = 2) {
        val shared = Shared()
        val target = shared.category("멘토링")
        val kept = shared.category("과제")

        // 첫 일정은 반복 일정
        val targetEvents: List<Event> = (0 until eventCount).map { i ->
            event(shared.calendar, target, "멘토링 $i", rrule = if (i == 0) "FREQ=WEEKLY;COUNT=4" else null)
        }
        val keptEvent = event(shared.calendar, kept, "과제 제출")

        // 설정의 이용 관계·카테고리·캘린더는 같은 캘린더로 맞춘다
        val targetOptions: List<CategoryOption> =
            listOf(shared.ownerSubscribe, shared.viewerSubscribe, shared.subscriberSubscribe).take(optionCount).map {
                persist(CategoryOption.create(it, target, shared.calendar, isVisible = false))
            }
        val keptOption = persist(CategoryOption.create(shared.ownerSubscribe, kept, shared.calendar, isVisible = false))
    }

    private fun assertNothingDeleted(filled: Filled) {
        assertNotNull(em.find(EventCategory::class.java, filled.target.id))
        filled.targetEvents.forEach { assertEquals(filled.target.id, em.find(Event::class.java, it.id).category?.id) }
        filled.targetOptions.forEach { assertNotNull(em.find(CategoryOption::class.java, it.id)) }
        assertNotNull(em.find(EventCategory::class.java, filled.kept.id))
        assertEquals(filled.kept.id, em.find(Event::class.java, filled.keptEvent.id).category?.id)
        assertNotNull(em.find(CategoryOption::class.java, filled.keptOption.id))
    }

    @Test
    fun `카테고리를 지우면 그 카테고리를 단 일정은 남고 카테고리만 비워지며 그 카테고리의 설정이 지워진다`() {
        // given
        val filled = Filled()
        clearContext()

        // when
        categoryService.delete(owner.id!!, filled.target.id!!)
        clearContext()

        // then: 지운 카테고리와 그 설정은 없고, 일정(반복 일정 포함)은 남아 카테고리 없음
        assertNull(em.find(EventCategory::class.java, filled.target.id))
        filled.targetEvents.forEach { event ->
            val saved = em.find(Event::class.java, event.id)
            assertNotNull(saved)
            assertNull(saved.category)
        }
        assertNotNull(em.find(Event::class.java, filled.targetEvents.first().id).rrule)
        filled.targetOptions.forEach { assertNull(em.find(CategoryOption::class.java, it.id)) }

        // then: 같은 캘린더의 다른 카테고리와 그 일정·설정은 그대로
        assertNotNull(em.find(EventCategory::class.java, filled.kept.id))
        assertEquals(filled.kept.id, em.find(Event::class.java, filled.keptEvent.id).category?.id)
        assertNotNull(em.find(CategoryOption::class.java, filled.keptOption.id))
    }

    @Test
    fun `EDITOR도 카테고리를 지울 수 있다`() {
        // given
        val filled = Filled()
        clearContext()

        // when
        categoryService.delete(editor.id!!, filled.target.id!!)
        clearContext()

        // then
        assertNull(em.find(EventCategory::class.java, filled.target.id))
    }

    @Test
    fun `VIEWER와 SUBSCRIBER와 이용 관계가 없는 사람이 지우면 403 FORBIDDEN이고 아무것도 바뀌지 않는다`() {
        // given
        val filled = Filled()
        clearContext()

        listOf(viewer, subscriber, stranger).forEach { user ->
            // when
            assertErrorCode(ErrorCode.FORBIDDEN) { categoryService.delete(user.id!!, filled.target.id!!) }
        }
        clearContext()

        // then
        assertNothingDeleted(filled)
    }

    @Test
    fun `없는 카테고리를 지우면 404 CATEGORY_NOT_FOUND`() {
        // when, then
        assertErrorCode(ErrorCode.CATEGORY_NOT_FOUND) { categoryService.delete(owner.id!!, Long.MAX_VALUE) }
    }

    @Test
    fun `일정과 설정이 많아도 카테고리 삭제 쿼리 수는 같다`() {
        // given: 일정 1개·설정 1개짜리와 일정 5개·설정 3개짜리 카테고리
        val small = Filled(eventCount = 1, optionCount = 1)
        val large = Filled(eventCount = 5, optionCount = 3)
        clearContext()
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        val wasEnabled = statistics.isStatisticsEnabled
        statistics.isStatisticsEnabled = true

        try {
            // when
            statistics.clear()
            categoryService.delete(owner.id!!, small.target.id!!)
            // 벌크 쿼리는 바로 나가지만, 쿼리 메서드로 바뀌면 DELETE가 flush 때 나가므로 flush까지 해서 센다
            em.flush()
            val smallCount = statistics.prepareStatementCount
            em.clear()

            statistics.clear()
            categoryService.delete(owner.id!!, large.target.id!!)
            em.flush()
            val largeCount = statistics.prepareStatementCount

            // then: 통계가 안 모이면 0 == 0으로 통과하므로 0보다 큰지도 본다
            assertTrue(smallCount > 0)
            assertEquals(smallCount, largeCount)
        } finally {
            statistics.isStatisticsEnabled = wasEnabled
        }
    }
}
