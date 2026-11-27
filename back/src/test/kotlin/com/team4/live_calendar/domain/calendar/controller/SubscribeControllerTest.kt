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
import com.team4.live_calendar.global.publicid.PublicIds
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
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
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubscribeControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val subscribeService: SubscribeService,
    @Autowired private val em: EntityManager,
    @Autowired private val shareService: ShareService,
    @Autowired private val publicIds: PublicIds,
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
    fun `중복 조회 이후 구독이 먼저 저장되었으면 409를 반환한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val subscriberId = checkNotNull(group.user.id)

        postBatchSubscription(group, share.shareId, share.calendarId)
            .andExpect { status { isCreated() } }

        // 두 요청이 기존 관계를 조회할 때는 아직 구독이 없었던 상황을 재현한다.
        doReturn(null).`when`(subscribeRepository).findByUserIdAndCalendarId(subscriberId, share.calendarId)

        postBatchSubscription(group, share.shareId, share.calendarId)
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("CALENDAR_RELATION_ALREADY_EXISTS") }
            }
    }

    @Test
    fun `비공개 페이지에 연결된 캘린더는 일괄 구독할 수 없다`() {
        val share = createShare(isPublic = false)
        val group = createSubscriberGroup()

        postBatchSubscription(group, share.shareId, share.calendarId)
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
            }
    }

    @Test
    fun `그룹명의 앞뒤 공백을 제거한 길이가 100자면 일괄 구독한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val groupName = "가".repeat(100)

        postBatchSubscription(group, share.shareId, share.calendarId, groupName = "  $groupName  ")
            .andExpect { status { isCreated() } }

        val subscription = subscribeRepository.findByUserIdAndCalendarId(checkNotNull(group.user.id), share.calendarId)
        assertEquals(groupName, subscription?.group?.name)
    }

    @Test
    fun `그룹명의 앞뒤 공백을 제거한 길이가 100자를 넘으면 400을 반환한다`() {
        val share = createShare(isPublic = true)
        val group = createSubscriberGroup()
        val groupName = "가".repeat(101)

        postBatchSubscription(group, share.shareId, share.calendarId, groupName = "  $groupName  ")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value("groupName") }
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
        return ShareFixture(shareId = shareId, calendarId = calendarId)
    }

    private fun createSubscriberGroup(): CalendarGroup {
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        return persist(CalendarGroup.create(subscriber, "내 구독 그룹", null))
    }

    private fun postBatchSubscription(
        group: CalendarGroup,
        shareId: Long,
        calendarId: Long,
        groupName: String = group.name,
    ) =
        mockMvc.post("/api/v1/shares/{shareId}/subscriptions", publicIds.share.encode(shareId)) {
            with(loginAs(group.user))
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupName": "$groupName", "calendarIds": [$calendarId]}"""
        }

    private data class ShareFixture(
        val shareId: Long,
        val calendarId: Long,
    )
}
