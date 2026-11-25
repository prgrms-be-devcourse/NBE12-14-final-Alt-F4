package com.team4.live_calendar.domain.calendar.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.domain.share.service.ShareService
import com.team4.live_calendar.domain.share.dto.ShareCreateRequest
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.java
import kotlin.test.assertEquals
import com.jayway.jsonpath.JsonPath

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubscribeControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val subscribeService: SubscribeService,
    @Autowired private val em: EntityManager,
    @Autowired private val shareService: ShareService,
) {

    @MockitoSpyBean
    private lateinit var subscribeRepository: SubscribeRepository

    @Test
    fun `본인의 구독을 해지하면 204와 빈 본문을 반환한다`() {
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val calendarId = createSubscribedCalendar(subscriber)

        mockMvc.delete("/api/v1/calendars/$calendarId/subscription") {
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
        val calendarId = createSubscribedCalendar(subscriber)

        mockMvc.delete("/api/v1/calendars/$calendarId/subscription") {
            with(loginAs(requester))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SUBSCRIPTION_NOT_FOUND") }
        }
    }

    private fun createSubscribedCalendar(subscriber: User): Long {
        val group = persist(CalendarGroup.create(subscriber, "구독자 그룹", null))
        val calendar = persist(Calendar.create("원본 캘린더", null, null))
        val calendarId = checkNotNull(calendar.id)

        subscribeService.subscribe(
            userId = checkNotNull(subscriber.id),
            groupId = checkNotNull(group.id),
            calendarId = calendarId,
        )
        return calendarId
    }

    private fun loginAs(user: User) =
        authentication(
            UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
        )

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    @Test
    fun `공개 페이지에 연결된 캘린더를 구독하면 201을 반환한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val groupId = checkNotNull(group.id)

        val response = postSubscription(group, share.shareId, share.calendarId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.subscriptionId") { exists() }
            }
            .andReturn().response

        val subscriptionId = JsonPath.read<Number>(
            response.contentAsString,
            "$.subscriptionId",
        ).toLong()

        em.flush()
        em.clear()

        val saved = checkNotNull(em.find(Subscribe::class.java, subscriptionId))
        assertEquals(CalendarRole.SUBSCRIBER, saved.role)
        assertEquals(share.calendarId, saved.calendar.id)
        assertEquals(groupId, saved.group.id)
    }

    @Test
    fun `중복 조회 이후 구독이 먼저 저장되었으면 409를 반환한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val subscriberId = checkNotNull(group.user.id)

        postSubscription(group, share.shareId, share.calendarId)
            .andExpect { status { isCreated() } }

        // 두 요청이 기존 관계를 조회할 때는 아직 구독이 없었던 상황을 재현한다.
        doReturn(null).`when`(subscribeRepository).findByUserIdAndCalendarId(subscriberId, share.calendarId)

        postSubscription(group, share.shareId, share.calendarId)
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("CALENDAR_RELATION_ALREADY_EXISTS") }
            }
    }

    @Test
    fun `비공개 페이지에 연결된 캘린더는 구독할 수 없다`() {
        val share = createShare(isPublic = false)
        val group = createSubscriberGroup()

        postSubscription(group, share.shareId, share.calendarId)
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
            }

        postSubscriptionByOriginalGroup(group.user, share)
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
            }
    }

    @Test
    fun `공개 페이지에 연결되지 않은 캘린더는 구독할 수 없다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val otherCalendar = persist(Calendar.create("연결되지 않은 캘린더", null, null))

        postSubscription(group, share.shareId, checkNotNull(otherCalendar.id))
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
            }
    }

    private fun createShare(isPublic: Boolean): ShareFixture {
        val owner = persist(User.signUp("publisher@mail.com", "{bcrypt}hash", "공유자"))
        val group = persist(CalendarGroup.create(owner, "소유자 그룹", null))
        val calendar = persist(Calendar.create("원본 캘린더", null, null))
        persist(Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null))

        val calendarId = checkNotNull(calendar.id)
        val shareId = shareService.create(
            userId = checkNotNull(owner.id),
            request = ShareCreateRequest(
                title = "공유 페이지",
                calendarIds = listOf(calendarId),
                isPublic = isPublic,
            ),
        )
        return ShareFixture(shareId = shareId, calendarId = calendarId, ownerGroupName = group.name)
    }

    private fun createSubscriberGroup(): CalendarGroup {
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        return persist(CalendarGroup.create(subscriber, "내 구독 그룹", null))
    }

    private fun postSubscription(group: CalendarGroup, shareId: Long, calendarId: Long) =
        mockMvc.post("/api/v1/calendars/$calendarId/subscription") {
            with(loginAs(group.user))
            contentType = MediaType.APPLICATION_JSON
            content = """{"shareId": $shareId, "groupId": ${checkNotNull(group.id)}}"""
        }

    private fun postSubscriptionByOriginalGroup(user: User, share: ShareFixture) =
        mockMvc.post("/api/v1/calendars/${share.calendarId}/subscription") {
            with(loginAs(user))
            contentType = MediaType.APPLICATION_JSON
            content = """{"shareId": ${share.shareId}}"""
        }

    private data class ShareFixture(
        val shareId: Long,
        val calendarId: Long,
        val ownerGroupName: String,
    )

    @Test
    fun `같은 이름의 내 그룹이 있으면 그 그룹에 구독한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val groupId = checkNotNull(group.id)

        val response = mockMvc.post("/api/v1/calendars/${share.calendarId}/subscription") {
            with(loginAs(group.user))
            contentType = MediaType.APPLICATION_JSON
            content = """{"shareId": ${share.shareId}, "groupName": "  ${group.name}  "}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.subscriptionId") { exists() }
        }.andReturn().response

        val subscriptionId = JsonPath.read<Number>(response.contentAsString, "$.subscriptionId").toLong()
        em.clear()

        val saved = checkNotNull(em.find(Subscribe::class.java, subscriptionId))
        assertEquals(groupId, saved.group.id)
    }

    @Test
    fun `같은 이름의 그룹이 다른 사용자에게만 있으면 내 그룹을 새로 만들어 구독한다`() {
        val share = createShare(isPublic = true)
        val groupName = "스터디"
        val otherUser = persist(User.signUp("other@mail.com", "{bcrypt}hash", "다른 사용자"))
        persist(CalendarGroup.create(otherUser, groupName, null))

        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val subscriberId = checkNotNull(subscriber.id)

        val response = mockMvc.post("/api/v1/calendars/${share.calendarId}/subscription") {
            with(loginAs(subscriber))
            contentType = MediaType.APPLICATION_JSON
            content = """{"shareId": ${share.shareId}, "groupName": "$groupName"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.subscriptionId") { exists() }
        }.andReturn().response

        val subscriptionId = JsonPath.read<Number>(response.contentAsString, "$.subscriptionId").toLong()
        em.clear()

        val saved = checkNotNull(em.find(Subscribe::class.java, subscriptionId))
        assertEquals(groupName, saved.group.name)
        assertEquals(subscriberId, saved.group.user.id)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `그룹 지정을 생략하면 원본 그룹명으로 내 그룹에 구독한다`(hasMatchingGroup: Boolean) {
        val share = createShare(isPublic = true)
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val subscriberId = checkNotNull(subscriber.id)
        val matchingGroup = if (hasMatchingGroup) {
            persist(CalendarGroup.create(subscriber, share.ownerGroupName, null))
        } else {
            null
        }

        val response = postSubscriptionByOriginalGroup(subscriber, share)
            .andExpect { status { isCreated() } }
            .andReturn().response

        val subscriptionId = JsonPath.read<Number>(response.contentAsString, "$.subscriptionId").toLong()
        em.clear()

        val saved = checkNotNull(em.find(Subscribe::class.java, subscriptionId))
        assertEquals(subscriberId, saved.group.user.id)
        assertEquals(share.ownerGroupName, saved.group.name)
        if (matchingGroup != null) {
            assertEquals(matchingGroup.id, saved.group.id)
        }
    }

    @Test
    fun `그룹 ID와 그룹 이름을 둘 다 보내면 400을 반환한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val groupId = checkNotNull(group.id)

        mockMvc.post("/api/v1/calendars/${share.calendarId}/subscription") {
            with(loginAs(group.user))
            contentType = MediaType.APPLICATION_JSON
            content = """{"shareId": ${share.shareId}, "groupId": $groupId, "groupName": "${group.name}"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }
}
