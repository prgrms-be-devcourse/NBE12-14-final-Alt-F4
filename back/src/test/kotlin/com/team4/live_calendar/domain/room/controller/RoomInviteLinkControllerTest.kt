package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.room.dto.RoomCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomDetailResponse
import com.team4.live_calendar.domain.room.dto.RoomJoinRequest
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.domain.room.service.RoomInviteLinkService
import com.team4.live_calendar.domain.room.service.RoomService
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds
import jakarta.persistence.EntityManager
import org.hamcrest.Matchers.hasLength
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomInviteLinkControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomService: RoomService,
    @Autowired private val roomInviteLinkService: RoomInviteLinkService,
    @Autowired private val roomMemberRepository: RoomMemberRepository,
    @Autowired private val subscribeRepository: SubscribeRepository,
    @Autowired private val publicIds: PublicIds,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var guest: User
    private lateinit var guestGroup: CalendarGroup
    private lateinit var room: RoomDetailResponse

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "방장"))
        guest = persist(User.signUp("guest@mail.com", "{bcrypt}hash", "손님"))
        val ownerGroup = persist(CalendarGroup.create(owner, "모임", null))
        guestGroup = persist(CalendarGroup.create(guest, "모임", null))
        room = roomService.create(
            requireNotNull(owner.id),
            RoomCreateRequest(
                name = "친구 모임",
                startDate = LocalDate.parse("2026-11-24"),
                endDate = LocalDate.parse("2026-11-28"),
                dailyStartTime = LocalTime.parse("09:00"),
                dailyEndTime = LocalTime.parse("18:00"),
                timezone = "Asia/Seoul",
                groupId = requireNotNull(ownerGroup.id),
            ),
        )
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    private fun issueLink(): String = roomInviteLinkService.issue(requireNotNull(owner.id), room.id).token

    private fun acceptAsGuest(token: String) =
        roomInviteLinkService.accept(requireNotNull(guest.id), token, RoomJoinRequest(requireNotNull(guestGroup.id)))

    private fun joinJson(group: CalendarGroup = guestGroup) = """{"groupId": ${group.id}}"""

    // ===== 발급·폐기 =====

    @Test
    fun `방장이 링크를 발급하면 201과 43자 토큰을 받는다`() {
        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/invite-links") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.token") { value(hasLength(43)) }
        }
    }

    @Test
    fun `방장이 아닌 참여자가 링크를 발급하면 403 NOT_ROOM_OWNER`() {
        // given
        acceptAsGuest(issueLink())

        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/invite-links") { with(loginAs(guest)) }

        // then
        result.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOT_ROOM_OWNER") }
        }
    }

    @Test
    fun `참여자가 아닌 회원이 링크를 발급하면 404 ROOM_NOT_FOUND`() {
        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/invite-links") { with(loginAs(guest)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `재발급하면 이전 링크는 404 INVITE_LINK_NOT_FOUND`() {
        // given
        val oldToken = issueLink()
        issueLink()

        // when
        val result = mockMvc.get("/api/v1/invite-links/$oldToken") { with(loginAs(guest)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("INVITE_LINK_NOT_FOUND") }
        }
    }

    @Test
    fun `폐기한 링크로는 참여할 수 없다`() {
        // given
        val token = issueLink()
        mockMvc.delete("/api/v1/rooms/${room.id}/invite-links") { with(loginAs(owner)) }
            .andExpect { status { isNoContent() } }

        // when
        val result = mockMvc.post("/api/v1/invite-links/$token/accept") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = joinJson()
        }

        // then
        result.andExpect { status { isNotFound() } }
    }

    // ===== 확인·수락 =====

    @Test
    fun `링크를 확인하기만 하면 참여하지 않는다`() {
        // given
        val token = issueLink()

        // when
        val result = mockMvc.get("/api/v1/invite-links/$token") { with(loginAs(guest)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("친구 모임") }
            jsonPath("$.ownerNickname") { value("방장") }
            jsonPath("$.alreadyMember") { value(false) }
        }
        assertNull(roomMemberRepository.findCurrentMember(requireNotNull(publicIds.room.decode(room.id)), requireNotNull(guest.id)))
    }

    @Test
    fun `수락하면 참여자가 되고 공통 캘린더를 고른 그룹에 VIEWER로 본다`() {
        // given
        val token = issueLink()

        // when
        val result = mockMvc.post("/api/v1/invite-links/$token/accept") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = joinJson()
        }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.roomId") { value(room.id) }
        }
        assertNotNull(roomMemberRepository.findCurrentMember(requireNotNull(publicIds.room.decode(room.id)), requireNotNull(guest.id)))
        val subscribe = subscribeRepository.findByUserIdAndCalendarId(requireNotNull(guest.id), room.calendarId)
        assertEquals(CalendarRole.VIEWER, subscribe?.role)
        assertEquals(guestGroup.id, subscribe?.group?.id)
    }

    @Test
    fun `남의 그룹으로 수락하면 404 GROUP_NOT_FOUND이고 참여하지 않는다`() {
        // given
        val token = issueLink()
        val ownersGroup = persist(CalendarGroup.create(owner, "남의 그룹", null))

        // when
        val result = mockMvc.post("/api/v1/invite-links/$token/accept") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = joinJson(ownersGroup)
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("GROUP_NOT_FOUND") }
        }
        assertNull(roomMemberRepository.findCurrentMember(requireNotNull(publicIds.room.decode(room.id)), requireNotNull(guest.id)))
    }

    @Test
    fun `두 번 수락해도 참여 행은 하나다`() {
        // given
        val token = issueLink()
        acceptAsGuest(token)

        // when
        mockMvc.post("/api/v1/invite-links/$token/accept") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = joinJson()
        }.andExpect { status { isOk() } }

        // then
        val count = em.createQuery("select count(m) from RoomMember m where m.user.id = :userId", Long::class.javaObjectType)
            .setParameter("userId", guest.id)
            .singleResult
        assertEquals(1L, count)
    }

    @Test
    fun `삭제된 방의 링크는 404 INVITE_LINK_NOT_FOUND`() {
        // given
        val token = issueLink()
        em.createQuery("update SchedulingRoom r set r.deletedAt = :now where r.id = :id")
            .setParameter("now", Instant.now())
            .setParameter("id", publicIds.room.decode(room.id))
            .executeUpdate()
        em.clear()

        // when
        val result = mockMvc.post("/api/v1/invite-links/$token/accept") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = joinJson()
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("INVITE_LINK_NOT_FOUND") }
        }
    }
}
