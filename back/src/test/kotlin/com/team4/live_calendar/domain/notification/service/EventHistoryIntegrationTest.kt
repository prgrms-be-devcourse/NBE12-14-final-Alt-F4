package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.domain.notification.entity.EventHistoryActionType
import com.team4.live_calendar.domain.notification.entity.EventHistoryTargetType
import com.team4.live_calendar.domain.notification.entity.NotificationOutboxStatus
import com.team4.live_calendar.domain.notification.repository.EventHistoryRepository
import com.team4.live_calendar.domain.notification.repository.NotificationOutboxRepository
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class EventHistoryIntegrationTest(
    @Autowired private val eventService: EventService,
    @Autowired private val eventHistoryRepository: EventHistoryRepository,
    @Autowired private val outboxRepository: NotificationOutboxRepository,
    @Autowired private val entityManager: EntityManager,
) {
    private lateinit var actor: User
    private lateinit var calendar: Calendar

    @BeforeEach
    fun setUp() {
        actor = persist(User.signUp("history-actor@mail.com", "{bcrypt}hash", "작성자"))
        calendar = persist(Calendar.create("이력 테스트", null, null))
        val group = persist(CalendarGroup.create(actor, "내 캘린더", null))
        persist(Subscribe.create(actor, group, calendar, CalendarRole.OWNER, null))
    }

    @Test
    fun `일정 생성 수정 삭제마다 스냅샷과 Outbox가 함께 쌓인다`() {
        val created = eventService.create(checkNotNull(actor.id), createRequest())
        val updated = eventService.update(checkNotNull(actor.id), created.id, updateRequest(version = created.version))
        eventService.delete(checkNotNull(actor.id), updated.id)

        entityManager.flush()
        val histories = eventHistoryRepository.findAllByEventIdOrderByIdAsc(created.id)
        val outboxes = outboxRepository.findAllByEventId(created.id)

        assertEquals(
            listOf(EventHistoryActionType.CREATE, EventHistoryActionType.UPDATE, EventHistoryActionType.DELETE),
            histories.map { it.actionType },
        )
        assertEquals(listOf("회의", "변경된 회의", "변경된 회의"), histories.map { it.eventTitle })
        assertEquals("회의", histories.first().afterData?.get("title"))
        assertEquals("회의", histories[1].beforeData?.get("title"))
        assertEquals("변경된 회의", histories[1].afterData?.get("title"))
        assertEquals("변경된 회의", histories.last().beforeData?.get("title"))
        assertEquals(histories.map { it.id }, outboxes.map { it.history.id })
        assertTrue(outboxes.all { it.status == NotificationOutboxStatus.PENDING })
    }

    @Test
    fun `반복 일정의 특정 회차 변경 이력은 예외 대상으로 기록된다`() {
        val created = eventService.create(checkNotNull(actor.id), createRequest("FREQ=WEEKLY;BYDAY=MO"))
        val occurrence = OffsetDateTime.parse("2026-11-23T08:00:00+09:00")

        eventService.update(
            checkNotNull(actor.id),
            created.id,
            updateRequest("FREQ=WEEKLY;BYDAY=MO", created.version),
            RecurrenceScope.THIS,
            occurrence,
        )

        entityManager.flush()
        val histories = eventHistoryRepository.findAllByEventIdOrderByIdAsc(created.id)
        val history = histories.lastOrNull()

        assertNotNull(history)
        assertEquals(EventHistoryTargetType.EVENT_EXCEPTION, history.targetType)
        assertEquals(EventHistoryActionType.UPDATE, history.actionType)
        assertEquals(occurrence.toInstant(), history.recurrenceId)
        assertEquals("회의", history.beforeData?.get("title"))
        assertEquals("변경된 회의", history.afterData?.get("title"))
        assertEquals(2, outboxRepository.findAllByEventId(created.id).size)
    }

    private fun createRequest(rrule: String? = null) = EventCreateRequest(
        checkNotNull(calendar.id),
        null,
        "회의",
        "설명",
        "회의실",
        false,
        OffsetDateTime.parse("2026-11-16T08:00:00+09:00"),
        OffsetDateTime.parse("2026-11-16T09:00:00+09:00"),
        null,
        null,
        rrule?.let { "Asia/Seoul" },
        rrule,
    )

    private fun updateRequest(rrule: String? = null, version: Long) = EventUpdateRequest(
        null,
        "변경된 회의",
        "변경된 설명",
        "회의실 2",
        false,
        OffsetDateTime.parse("2026-11-16T10:00:00+09:00"),
        OffsetDateTime.parse("2026-11-16T11:00:00+09:00"),
        null,
        null,
        rrule?.let { "Asia/Seoul" },
        rrule,
        version,
    )

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }
}
