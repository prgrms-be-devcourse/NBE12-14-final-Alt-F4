package com.team4.live_calendar.domain.notification.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.EventHistoryActionType
import com.team4.live_calendar.domain.notification.entity.EventHistoryTargetType
import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.notification.repository.NotificationRepository
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val notificationRepository: NotificationRepository,
) {

    @Test
    fun `내 알림 목록은 변경 이력 스냅샷과 페이지 정보를 반환하고 읽음 처리에 반영된다`() {
        val user = persist(User.signUp("notification-user@mail.com", "{bcrypt}hash", "알림 사용자"))
        val calendar = persist(Calendar.create("알림 캘린더", null, null))
        val history = persist(
            EventHistory.create(
                calendar,
                321L,
                user,
                EventHistoryTargetType.EVENT,
                null,
                EventHistoryActionType.DELETE,
                "삭제된 일정",
                mapOf("title" to "삭제된 일정"),
                null,
            )
        )
        val notification = persist(
            Notification.create(user, calendar, NotificationType.EVENT_DELETED, mapOf("eventId" to 321L), history)
        )

        mockMvc.get("/api/v1/notifications?page=0&size=10") {
            with(loginAs(user))
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].id") { value(notification.id) }
            jsonPath("$.content[0].type") { value("EVENT_DELETED") }
            jsonPath("$.content[0].calendarName") { value("알림 캘린더") }
            jsonPath("$.content[0].eventId") { value(321) }
            jsonPath("$.content[0].eventTitle") { value("삭제된 일정") }
            jsonPath("$.content[0].params.eventId") { value(321) }
            jsonPath("$.content[0].isRead") { value(false) }
            jsonPath("$.content[0].createdAt") { exists() }
            jsonPath("$.totalElements") { value(1) }
        }

        mockMvc.get("/api/v1/notifications/unread-count") {
            with(loginAs(user))
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(1) }
        }

        mockMvc.patch("/api/v1/notifications/${notification.id}") {
            with(loginAs(user))
        }.andExpect { status { isNoContent() } }
        mockMvc.patch("/api/v1/notifications/${notification.id}") {
            with(loginAs(user))
        }.andExpect { status { isNoContent() } }

        assertEquals(0L, notificationRepository.countByUserIdAndIsReadFalse(checkNotNull(user.id)))
    }

    @Test
    fun `다른 사용자 알림은 읽을 수 없고 전체 읽음은 본인 알림에만 적용된다`() {
        val user = persist(User.signUp("notification-owner@mail.com", "{bcrypt}hash", "소유자"))
        val other = persist(User.signUp("notification-other@mail.com", "{bcrypt}hash", "다른 사용자"))
        val calendar = persist(Calendar.create("공유 캘린더", null, null))
        val ownNotification = persistNotification(user, calendar, 401L)
        val otherNotification = persistNotification(other, calendar, 402L)

        mockMvc.get("/api/v1/notifications") {
            with(loginAs(user))
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].eventId") { value(401) }
        }

        mockMvc.patch("/api/v1/notifications/${otherNotification.id}") {
            with(loginAs(user))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOTIFICATION_NOT_FOUND") }
        }

        mockMvc.patch("/api/v1/notifications") {
            with(loginAs(user))
        }.andExpect { status { isNoContent() } }

        entityManager.clear()
        assertEquals(0L, notificationRepository.countByUserIdAndIsReadFalse(checkNotNull(user.id)))
        assertEquals(1L, notificationRepository.countByUserIdAndIsReadFalse(checkNotNull(other.id)))
        assertEquals(true, notificationRepository.findById(checkNotNull(ownNotification.id)).orElseThrow().isRead)
        assertEquals(false, notificationRepository.findById(checkNotNull(otherNotification.id)).orElseThrow().isRead)
    }

    private fun persistNotification(user: User, calendar: Calendar, eventId: Long): Notification {
        val history = persist(
            EventHistory.create(
                calendar,
                eventId,
                user,
                EventHistoryTargetType.EVENT,
                null,
                EventHistoryActionType.CREATE,
                "일정 $eventId",
            )
        )
        return persist(Notification.create(user, calendar, NotificationType.EVENT_CREATED, null, history))
    }

    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()))

    private fun <T : Any> persist(entity: T): T = entity.also { entityManager.persist(it) }
}
