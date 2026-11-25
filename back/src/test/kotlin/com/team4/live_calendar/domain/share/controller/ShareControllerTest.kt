package com.team4.live_calendar.domain.share.controller

import com.jayway.jsonpath.JsonPath
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.share.repository.ShareInfoRepository
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val em: EntityManager,
    @Autowired private val shareInfoRepository: ShareInfoRepository,
    @Autowired private val calendarGroupRepository: CalendarGroupRepository,
    @Autowired private val subscribeRepository: SubscribeRepository,
) {

    @Test
    fun `내 캘린더 두 개를 공유하면 201을 반환하고 요청 순서대로 연결한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        val firstId = createOwnedCalendar(owner, "정기 모임")
        val secondId = createOwnedCalendar(owner, "행사 일정")
        val ownerId = checkNotNull(owner.id)

        val response = postShare(owner, listOf(secondId, firstId))
            .andExpect {
                status { isCreated() }
                jsonPath("$.shareId") { exists() }
            }
            .andReturn().response

        val shareId = JsonPath.read<Number>(response.contentAsString, "$.shareId").toLong()
        em.flush()
        em.clear()

        val share = checkNotNull(em.find(ShareInfo::class.java, shareId))
        assertEquals(ownerId, share.user.id)
        assertEquals("동아리 공지", share.title)
        assertTrue(share.isPublic)

        val connections = findConnections(shareId)

        assertEquals(listOf(secondId, firstId), connections.map { it.calendar.id })
        assertEquals(listOf(1, 2), connections.map { it.displayOrder })
    }

    @Test
    fun `같은 캘린더를 중복 요청하면 201을 반환하고 한 번만 연결한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "소유자"))
        val calendarId = createOwnedCalendar(owner, "정기 모임")

        val response = postShare(owner, listOf(calendarId, calendarId))
            .andExpect {
                status { isCreated() }
                jsonPath("$.shareId") { exists() }
            }
            .andReturn().response

        val shareId = JsonPath.read<Number>(response.contentAsString, "$.shareId").toLong()
        em.flush()
        em.clear()

        assertEquals(listOf(calendarId), findConnections(shareId).map { it.calendar.id })
    }

    @Test
    fun `타인 캘린더가 포함되면 403을 반환하고 공유 페이지를 저장하지 않는다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "요청자"))
        val other = persist(User.signUp("other@mail.com", "{bcrypt}hash", "다른 소유자"))
        val myCalendarId = createOwnedCalendar(owner, "내 캘린더")
        val otherCalendarId = createOwnedCalendar(other, "타인 캘린더")
        val shareCountBefore = shareInfoRepository.count()

        postShare(owner, listOf(myCalendarId, otherCalendarId))
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }

        assertEquals(shareCountBefore, shareInfoRepository.count())
    }

    private fun findConnections(shareId: Long): List<ShareCalendarConnection> =
        em.createQuery(
            "select c from ShareCalendarConnection c where c.share.id = :shareId order by c.displayOrder",
            ShareCalendarConnection::class.java,
        ).setParameter("shareId", shareId).resultList

    private fun postShare(user: User, calendarIds: List<Long>) =
        mockMvc.post("/api/v1/shares") {
            with(authentication(
                UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
            ))
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "title": "동아리 공지",
                    "calendarIds": [${calendarIds.joinToString()}],
                    "isPublic": true
                }
            """.trimIndent()
        }

    private fun createOwnedCalendar(owner: User, name: String): Long {
        val group = persist(CalendarGroup.create(owner, "내 그룹", null))
        val calendar = persist(Calendar.create(name, null, null))
        persist(Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null))
        return checkNotNull(calendar.id)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `일괄 구독하면 선택한 캘린더를 같은 내 그룹에 추가한다`(hasMatchingGroup: Boolean) {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val subscriberId = checkNotNull(subscriber.id)
        val groupName = "운동"

        val existingGroupId = if (hasMatchingGroup) {
            checkNotNull(persist(CalendarGroup.create(subscriber, groupName, null)).id)
        } else {
            persist(CalendarGroup.create(owner, groupName, null))
            null
        }

        val calendarIds = listOf(
            createOwnedCalendar(owner, "야구"),
            createOwnedCalendar(owner, "축구"),
        )
        val shareResponse = postShare(owner, calendarIds)
            .andExpect { status { isCreated() } }
            .andReturn().response
        val shareId = JsonPath.read<Number>(shareResponse.contentAsString, "$.shareId").toLong()

        val response = mockMvc.post("/api/v1/groups/{groupName}/subscriptions", "  $groupName  ") {
            with(authentication(UsernamePasswordAuthenticationToken(subscriberId.toString(), null, emptyList())))
            contentType = MediaType.APPLICATION_JSON
            content = """{"shareId": $shareId, "calendarIds": [${calendarIds.joinToString()}]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.subscriptionIds.length()") { value(2) }
        }.andReturn().response

        val subscriptionIds = JsonPath.read<List<Number>>(response.contentAsString, "$.subscriptionIds")
        em.flush()
        em.clear()

        val saved = subscriptionIds.map { id ->
            checkNotNull(em.find(Subscribe::class.java, id.toLong()))
        }
        assertEquals(calendarIds, saved.map { it.calendar.id })
        assertTrue(saved.all { it.role == CalendarRole.SUBSCRIBER })
        assertTrue(saved.all { it.group.name == groupName && it.group.user.id == subscriberId })

        val savedGroupIds = saved.map { it.group.id }.distinct()
        assertEquals(1, savedGroupIds.size)
        if (existingGroupId != null) {
            assertEquals(existingGroupId, savedGroupIds.single())
        }
    }

    @Test
    fun `공유 페이지에 연결되지 않은 캘린더가 하나라도 있으면 일괄 구독을 거절한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val subscriber = persist(User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자"))
        val subscriberId = checkNotNull(subscriber.id)

        val sharedCalendarId = createOwnedCalendar(owner, "야구")
        val unsharedCalendarId = createOwnedCalendar(owner, "축구")
        val shareResponse = postShare(owner, listOf(sharedCalendarId))
            .andExpect { status { isCreated() } }
            .andReturn().response
        val shareId = JsonPath.read<Number>(shareResponse.contentAsString, "$.shareId").toLong()

        mockMvc.post("/api/v1/groups/{groupName}/subscriptions", "운동") {
            with(authentication(UsernamePasswordAuthenticationToken(subscriberId.toString(), null, emptyList())))
            contentType = MediaType.APPLICATION_JSON
            content = """
            {"shareId": $shareId, "calendarIds": [$sharedCalendarId, $unsharedCalendarId]}
        """.trimIndent()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
        }

        assertTrue(calendarGroupRepository.findAllByUserIdOrdered(subscriberId).isEmpty())
        assertTrue(subscribeRepository.findAllWithCalendarByUserId(subscriberId).isEmpty())
    }
}
