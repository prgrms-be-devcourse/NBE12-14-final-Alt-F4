package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.EventHistoryActionType
import com.team4.live_calendar.domain.notification.entity.EventHistoryTargetType
import com.team4.live_calendar.domain.notification.entity.NotificationOutboxStatus
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.notification.repository.NotificationOutboxRepository
import com.team4.live_calendar.domain.notification.repository.NotificationRepository
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class NotificationOutboxProcessorTest(
    @Autowired private val entityManager: EntityManager,
    @Autowired private val outboxService: NotificationOutboxService,
    @Autowired private val outboxProcessor: NotificationOutboxProcessor,
    @Autowired private val outboxRepository: NotificationOutboxRepository,
    @Autowired private val notificationRepository: NotificationRepository,
) {

    @Test
    fun `처리자와 알림 비활성 구독자는 제외하고 재처리해도 중복 알림이 없다`() {
        val actor = persist(User.signUp("actor@mail.com", "{bcrypt}hash", "작성자"))
        val enabled = persist(User.signUp("enabled@mail.com", "{bcrypt}hash", "수신자"))
        val disabled = persist(User.signUp("disabled@mail.com", "{bcrypt}hash", "미수신자"))
        val calendar = persist(Calendar.create("공유 캘린더", null, null))
        addSubscribe(actor, calendar, CalendarRole.OWNER, "actor-group")
        addSubscribe(enabled, calendar, CalendarRole.SUBSCRIBER, "enabled-group")
        addSubscribe(disabled, calendar, CalendarRole.SUBSCRIBER, "disabled-group").changeNotificationEnabled(false)

        val history = persist(
            EventHistory.create(
                calendar,
                77L,
                actor,
                EventHistoryTargetType.EVENT,
                null,
                EventHistoryActionType.CREATE,
                "공유 일정",
                null,
                mapOf("title" to "공유 일정"),
            )
        )
        val outbox = outboxService.save(history)
        entityManager.flush()

        val claimedId = assertNotNull(outboxService.claimNext())
        entityManager.clear()
        outboxProcessor.process(claimedId)

        val eventHistoryId = checkNotNull(history.id)
        assertTrue(notificationRepository.existsByUserIdAndEventHistoryId(checkNotNull(enabled.id), eventHistoryId))
        assertFalse(notificationRepository.existsByUserIdAndEventHistoryId(checkNotNull(disabled.id), eventHistoryId))
        assertFalse(notificationRepository.existsByUserIdAndEventHistoryId(checkNotNull(actor.id), eventHistoryId))

        entityManager.clear()
        outboxProcessor.process(claimedId)

        val notifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(checkNotNull(enabled.id), org.springframework.data.domain.PageRequest.of(0, 10))
        assertEquals(1, notifications.totalElements)
        assertEquals(NotificationType.EVENT_CREATED, notifications.content.single().type)
        assertEquals(NotificationOutboxStatus.COMPLETED, outboxRepository.findById(claimedId).orElseThrow().status)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun addSubscribe(user: User, calendar: Calendar, role: CalendarRole, groupName: String): Subscribe {
        val group = persist(CalendarGroup.create(user, groupName, null))
        return persist(Subscribe.create(user, group, calendar, role, null))
    }
}
