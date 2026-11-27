package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.repository.CategoryOptionRepository
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

// 표시 설정 UNIQUE 2개(V4 마이그레이션)가 DB에 실제로 있는지, 같은 값을 두 번 저장해 확인한다
// ddl-auto: validate는 컬럼만 보고 UNIQUE는 확인하지 않는다
@DataJpaTest
@Import(JpaConfig::class)
class DisplayOptionUniqueConstraintTest(
    @Autowired private val eventOptionRepository: EventOptionRepository,
    @Autowired private val categoryOptionRepository: CategoryOptionRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var calendar: Calendar
    private lateinit var subscribe: Subscribe

    @BeforeEach
    fun setUp() {
        val user = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "주인"))
        val group = persist(CalendarGroup.create(user, "내 캘린더", 0))
        calendar = persist(Calendar.create("개인", null, null, "Asia/Seoul"))
        subscribe = persist(Subscribe.create(user, group, calendar, CalendarRole.OWNER, null))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    @Test
    fun `한 이용 관계는 같은 일정에 표시 설정을 하나만 가진다`() {
        // given
        val owner = subscribe.user
        val event = persist(
            Event.create(
                calendar, null, owner, "회의", null, null,
                Instant.parse("2026-11-24T01:00:00Z"), Instant.parse("2026-11-24T02:00:00Z"),
            ),
        )
        eventOptionRepository.saveAndFlush(EventOption.create(subscribe, event, calendar, isVisible = false))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            eventOptionRepository.saveAndFlush(EventOption.create(subscribe, event, calendar, isVisible = false))
        }
    }

    @Test
    fun `한 이용 관계는 같은 카테고리에 표시 설정을 하나만 가진다`() {
        // given
        val category = persist(EventCategory.create(calendar, "운동", null))
        categoryOptionRepository.saveAndFlush(CategoryOption.create(subscribe, category, calendar, isVisible = false))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            categoryOptionRepository.saveAndFlush(CategoryOption.create(subscribe, category, calendar, isVisible = false))
        }
    }
}
