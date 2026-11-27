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
import kotlin.test.assertNull

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
        outboxService.save(history)
        entityManager.flush()

        val claimedId = assertNotNull(outboxService.claimNext())
        assertNull(outboxService.claimNext())
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

    @Test
    fun `수정과 삭제 이력은 각각 대응하는 알림 유형으로 생성된다`() {
        val actor = persist(User.signUp("action-actor@mail.com", "{bcrypt}hash", "작성자"))
        val recipient = persist(User.signUp("action-recipient@mail.com", "{bcrypt}hash", "수신자"))
        val calendar = persist(Calendar.create("공유 캘린더", null, null))
        addSubscribe(actor, calendar, CalendarRole.OWNER, "action-actor-group")
        addSubscribe(recipient, calendar, CalendarRole.SUBSCRIBER, "action-recipient-group")

        listOf(
            EventHistoryActionType.UPDATE to NotificationType.EVENT_UPDATED,
            EventHistoryActionType.DELETE to NotificationType.EVENT_DELETED,
        ).forEachIndexed { index, (actionType, notificationType) ->
            val history = persist(
                EventHistory.create(
                    calendar,
                    80L + index,
                    actor,
                    EventHistoryTargetType.EVENT,
                    null,
                    actionType,
                    "일정 $index",
                )
            )
            outboxService.save(history)
            entityManager.flush()

            val claimedId = assertNotNull(outboxService.claimNext())
            entityManager.clear()
            outboxProcessor.process(claimedId)
            entityManager.clear()

            val notification = notificationRepository.findByUserIdAndEventHistoryId(
                checkNotNull(recipient.id),
                checkNotNull(history.id),
            )
            assertNotNull(notification)
            assertEquals(notificationType, notification.type)
        }
    }

    @Test
    fun `처리 실패는 재시도 정보를 기록하고 오래된 잠금은 대기 상태로 복구한다`() {
        val actor = persist(User.signUp("retry-actor@mail.com", "{bcrypt}hash", "작성자"))
        val calendar = persist(Calendar.create("복구 캘린더", null, null))
        val history = persist(
            EventHistory.create(
                calendar,
                90L,
                actor,
                EventHistoryTargetType.EVENT,
                null,
                EventHistoryActionType.CREATE,
                "재시도 일정",
            )
        )
        outboxService.save(history)
        entityManager.flush()

        val claimedId = assertNotNull(outboxService.claimNext())
        entityManager.clear()
        outboxService.markRetry(claimedId, "일시적 오류")
        // @Transactional 테스트에서는 markRetry가 외부 테스트 트랜잭션에 참여하므로,
        // clear 전에 변경사항을 flush해 DB 반영을 보장한다.
        entityManager.flush()
        entityManager.clear()

        val retrying = outboxRepository.findById(claimedId).orElseThrow()
        assertEquals(NotificationOutboxStatus.PENDING, retrying.status)
        assertEquals(1, retrying.retryCount)
        assertEquals("일시적 오류", retrying.errorMessage)
        assertNotNull(retrying.nextRetryAt)

        retrying.markProcessing(java.time.Instant.now().minusSeconds(600))
        entityManager.flush()
        assertEquals(1, outboxService.recoverStale())
        entityManager.clear()

        val recovered = outboxRepository.findById(claimedId).orElseThrow()
        assertEquals(NotificationOutboxStatus.PENDING, recovered.status)
        assertNull(recovered.lockedAt)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }

    private fun addSubscribe(user: User, calendar: Calendar, role: CalendarRole, groupName: String): Subscribe {
        val group = persist(CalendarGroup.create(user, groupName, null))
        return persist(Subscribe.create(user, group, calendar, role, null))
    }
}
