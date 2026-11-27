package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

// 회차 예외 UNIQUE(V5 마이그레이션)가 DB에 실제로 있는지, 같은 회차를 두 번 저장해 확인한다
// ddl-auto: validate는 컬럼만 보고 UNIQUE는 확인하지 않는다
@DataJpaTest
@Import(JpaConfig::class)
class EventExceptionUniqueConstraintTest(
    @Autowired private val eventExceptionRepository: EventExceptionRepository,
    @Autowired private val em: EntityManager,
) {

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    @Test
    fun `한 일정의 한 회차에는 예외를 하나만 저장할 수 있다`() {
        // given: 동시 요청으로 같은 회차에 예외를 두 번 넣으려는 상황
        val user = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "주인"))
        val calendar = persist(Calendar.create("개인", null, null, "Asia/Seoul"))
        val event = persist(
            Event.create(
                calendar, null, user, "주간 회의", null, null,
                Instant.parse("2026-11-15T23:00:00Z"), Instant.parse("2026-11-16T00:00:00Z"),
                timezone = "Asia/Seoul", rrule = "FREQ=WEEKLY;BYDAY=MO",
            ),
        )
        val recurrenceId = Instant.parse("2026-11-22T23:00:00Z")
        eventExceptionRepository.saveAndFlush(EventException.create(event, recurrenceId, EventExceptionType.DELETED))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            eventExceptionRepository.saveAndFlush(
                EventException.create(
                    event, recurrenceId, EventExceptionType.MODIFIED, "중복", null, null,
                    Instant.parse("2026-11-24T23:00:00Z"), Instant.parse("2026-11-25T00:00:00Z"),
                ),
            )
        }
    }
}
