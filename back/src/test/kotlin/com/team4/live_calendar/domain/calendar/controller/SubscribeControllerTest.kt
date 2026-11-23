package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubscribeControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val subscribeService: SubscribeService,
    @Autowired private val em: EntityManager,
) {

    @Test
    fun `본인의 구독을 해지하면 204와 빈 본문을 반환한다`() {
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val subscriptionId = createSubscription(subscriber)

        mockMvc.delete("/api/v1/subscriptions/$subscriptionId") {
            with(loginAs(subscriber))
        }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
    }

    @Test
    fun `다른 사용자의 구독을 해지하려 하면 404를 반환한다`() {
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val requester = persist(User.signUp("requester@mail.com", "{bcrypt}hash", "요청자"))
        val subscriptionId = createSubscription(subscriber)

        mockMvc.delete("/api/v1/subscriptions/$subscriptionId") {
            with(loginAs(requester))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SUBSCRIPTION_NOT_FOUND") }
        }
    }

    private fun createSubscription(subscriber: User): Long {
        val group = persist(CalendarGroup.create(subscriber, "구독자 그룹", null))
        val calendar = persist(Calendar.create("원본 캘린더", null, null))

        return subscribeService.subscribe(
            userId = checkNotNull(subscriber.id),
            groupId = checkNotNull(group.id),
            calendarId = checkNotNull(calendar.id),
        )
    }

    private fun loginAs(user: User) =
        authentication(
            UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
        )

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }
}