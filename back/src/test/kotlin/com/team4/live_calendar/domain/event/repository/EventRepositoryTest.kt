package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@Import(JpaConfig::class)
class EventRepositoryTest(
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val calendarRepository: CalendarRepository,
    @Autowired private val em: EntityManager,
) {

    private val user by lazy { userRepository.save(User.signUp("event@mail.com", "{bcrypt}hash", "작성자")) }
    private val calendar by lazy { calendarRepository.save(Calendar.create("팀 캘린더", null, null)) }

    private fun createEvent(title: String = "팀 회의", rrule: String? = null) = Event.create(
        calendar = calendar,
        category = null,
        user = user,
        title = title,
        description = null,
        location = null,
        startAt = Instant.parse("2026-11-18T10:00:00Z"),
        endAt = Instant.parse("2026-11-18T11:00:00Z"),
        rrule = rrule,
    )

    @Test
    fun `일정을 저장하면 id, uid, 생성 수정 일시가 채워진다`() {
        val saved = eventRepository.saveAndFlush(createEvent())

        assertNotNull(saved.id)
        assertTrue(saved.uid.isNotBlank())
        assertNotNull(saved.createdAt)
        assertNotNull(saved.updatedAt)
    }

    @Test
    fun `값을 안 넣으면 기본값으로 저장된다`() {
        val saved = eventRepository.saveAndFlush(createEvent())
        em.clear()
        val found = eventRepository.findById(saved.id!!).get()

        assertEquals(user.id, found.user.id)
        assertEquals(calendar.id, found.calendar.id)
        assertFalse(found.isAllDay)
        assertEquals("UTC", found.timezone)
        assertNull(found.rrule)
        assertNull(found.recurUntil)
    }

    @Test
    fun `일정마다 uid가 다르게 만들어진다`() {
        val first = eventRepository.save(createEvent("A"))
        val second = eventRepository.save(createEvent("B"))

        assertNotEquals(first.uid, second.uid)
    }

    @Test
    fun `반복 규칙을 저장하고 그대로 조회한다`() {
        val rrule = "FREQ=WEEKLY;BYDAY=WE;COUNT=3"
        val saved = eventRepository.saveAndFlush(createEvent(rrule = rrule))
        em.clear()

        val found = eventRepository.findById(saved.id!!).get()

        assertEquals(rrule, found.rrule)
    }

    @Test
    fun `수정하면 생성일시는 그대로이고 수정일시는 갱신된다`() {
        val saved = eventRepository.saveAndFlush(createEvent())
        em.clear()
        val before = eventRepository.findById(saved.id!!).get()
        val createdAtBefore = before.createdAt
        val updatedAtBefore = before.updatedAt

        before.update(
            title = "제목 변경",
            description = before.description,
            location = before.location,
            startAt = before.startAt,
            endAt = before.endAt,
            isAllDay = before.isAllDay,
            category = before.category,
        )
        eventRepository.saveAndFlush(before)
        em.clear()
        val after = eventRepository.findById(saved.id!!).get()

        assertEquals("제목 변경", after.title)
        assertEquals(createdAtBefore, after.createdAt)
        assertFalse(after.updatedAt.isBefore(updatedAtBefore))
    }
}
