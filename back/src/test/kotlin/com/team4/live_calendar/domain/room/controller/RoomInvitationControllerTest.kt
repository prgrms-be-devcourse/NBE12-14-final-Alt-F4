package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.room.dto.RoomCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomDetailResponse
import com.team4.live_calendar.domain.room.dto.RoomInvitationCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomJoinRequest
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.domain.room.service.RoomInvitationService
import com.team4.live_calendar.domain.room.service.RoomInviteLinkService
import com.team4.live_calendar.domain.room.service.RoomService
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomInvitationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomService: RoomService,
    @Autowired private val roomInvitationService: RoomInvitationService,
    @Autowired private val roomInviteLinkService: RoomInviteLinkService,
    @Autowired private val roomMemberRepository: RoomMemberRepository,
    @Autowired private val publicIds: PublicIds,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var invitee: User
    private lateinit var stranger: User
    private lateinit var inviteeGroup: CalendarGroup
    private lateinit var strangerGroup: CalendarGroup
    private lateinit var room: RoomDetailResponse

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "방장"))
        invitee = persist(User.signUp("invitee@mail.com", "{bcrypt}hash", "초대받은회원"))
        stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "모르는회원"))
        val ownerGroup = persist(CalendarGroup.create(owner, "모임", null))
        inviteeGroup = persist(CalendarGroup.create(invitee, "모임", null))
        strangerGroup = persist(CalendarGroup.create(stranger, "모임", null))
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

    private fun tagOf(user: User): String = publicIds.user.encode(requireNotNull(user.id))

    private fun invite(target: User = invitee): Long =
        roomInvitationService.invite(requireNotNull(owner.id), room.id, RoomInvitationCreateRequest(tagOf(target))).id

    private fun inviteRequest(tag: String) = mockMvc.post("/api/v1/rooms/${room.id}/invitations") {
        with(loginAs(owner))
        contentType = MediaType.APPLICATION_JSON
        content = """{"inviteeTag": "$tag"}"""
    }

    private fun inviteRequest(target: User) = inviteRequest(tagOf(target))

    private fun acceptRequest(invitationId: Long, by: User, group: CalendarGroup) =
        mockMvc.post("/api/v1/invitations/$invitationId/accept") {
            with(loginAs(by))
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupId": ${group.id}}"""
        }

    // ===== 태그로 회원 찾기 =====

    @Test
    fun `태그로 찾으면 그 회원의 태그와 닉네임만 나온다`() {
        // when
        val result = mockMvc.get("/api/v1/users/tags/${tagOf(invitee)}") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.tag") { value(tagOf(invitee)) }
            jsonPath("$.nickname") { value("초대받은회원") }
            jsonPath("$.id") { doesNotExist() }
            jsonPath("$.email") { doesNotExist() }
        }
    }

    @Test
    fun `태그는 소문자로 보내도 같은 회원이다`() {
        // when
        val result = mockMvc.get("/api/v1/users/tags/${tagOf(invitee).lowercase()}") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.tag") { value(tagOf(invitee)) }
        }
    }

    @Test
    fun `같은 닉네임 회원이 있어도 태그로 정확히 한 명을 찾는다`() {
        // given
        val twin = persist(User.signUp("twin@mail.com", "{bcrypt}hash", "초대받은회원"))

        // when
        val result = mockMvc.get("/api/v1/users/tags/${tagOf(twin)}") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.tag") { value(tagOf(twin)) }
            jsonPath("$.nickname") { value("초대받은회원") }
        }
    }

    @Test
    fun `없는 회원의 태그나 못 읽는 태그는 404 USER_NOT_FOUND`() {
        // given: 형식은 맞지만 없는 회원, 못 읽는 태그,
        // Long 범위를 넘어 음수로 읽히는 긴 태그 (테스트 회원 알파벳 기준, 500이 아니라 404여야 한다)
        val tags = listOf(publicIds.user.encode(999_999L), "!!", "1N11P8S5VPHMVP")

        tags.forEach { tag ->
            // when
            val result = mockMvc.get("/api/v1/users/tags/$tag") { with(loginAs(owner)) }

            // then
            result.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("USER_NOT_FOUND") }
            }
        }
    }

    @Test
    fun `내 태그로 찾으면 나도 나오지만 나를 초대하면 409 ALREADY_ROOM_MEMBER`() {
        // when
        val found = mockMvc.get("/api/v1/users/tags/${tagOf(owner)}") { with(loginAs(owner)) }
        val invited = inviteRequest(owner)

        // then
        found.andExpect { status { isOk() } }
        invited.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ALREADY_ROOM_MEMBER") }
        }
    }

    @Test
    fun `내 정보에 내 태그가 나온다`() {
        // when
        val result = mockMvc.get("/api/v1/users/me") { with(loginAs(invitee)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.tag") { value(tagOf(invitee)) }
        }
    }

    // ===== 초대 =====

    @Test
    fun `방장이 회원을 초대하면 201과 대기 중 초대를 받는다`() {
        // when
        val result = inviteRequest(invitee)

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.roomName") { value("친구 모임") }
            jsonPath("$.inviterNickname") { value("방장") }
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun `못 읽는 태그로 초대하면 404 USER_NOT_FOUND`() {
        // when
        val result = inviteRequest("UUUUUU")

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `이미 참여 중인 회원을 초대하면 409 ALREADY_ROOM_MEMBER`() {
        // when
        val result = inviteRequest(owner)

        // then
        result.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ALREADY_ROOM_MEMBER") }
        }
    }

    @Test
    fun `대기 중인 초대가 있는데 또 초대하면 409 DUPLICATE_INVITATION`() {
        // given
        invite()

        // when
        val result = inviteRequest(invitee)

        // then
        result.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE_INVITATION") }
        }
    }

    // ===== 받은 초대·수락 =====

    @Test
    fun `초대받은 회원은 받은 초대 목록에서 초대를 본다`() {
        // given
        invite()

        // when
        val result = mockMvc.get("/api/v1/invitations") { with(loginAs(invitee)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].roomId") { value(room.id) }
        }
    }

    @Test
    fun `수락하면 참여하고 받은 초대 목록에서 사라진다`() {
        // given
        val invitationId = invite()

        // when
        val result = acceptRequest(invitationId, invitee, inviteeGroup)

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.roomId") { value(room.id) }
        }
        assertNotNull(roomMemberRepository.findCurrentMember(requireNotNull(publicIds.room.decode(room.id)), requireNotNull(invitee.id)))
        mockMvc.get("/api/v1/invitations") { with(loginAs(invitee)) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `초대받은 뒤 링크로 먼저 참여하면 받은 초대 목록에서 빠지고 수락해도 200이다`() {
        // given: 지정 초대(대기 중) → 수락 전에 초대 링크로 참여
        val invitationId = invite()
        val token = roomInviteLinkService.issue(requireNotNull(owner.id), room.id).token
        roomInviteLinkService.accept(requireNotNull(invitee.id), token, RoomJoinRequest(requireNotNull(inviteeGroup.id)))

        // when
        val list = mockMvc.get("/api/v1/invitations") { with(loginAs(invitee)) }
        val accepted = acceptRequest(invitationId, invitee, inviteeGroup)

        // then
        list.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        accepted.andExpect { status { isOk() } }
        val count = em.createQuery("select count(m) from RoomMember m where m.user.id = :userId", Long::class.javaObjectType)
            .setParameter("userId", invitee.id)
            .singleResult
        assertEquals(1L, count)
    }

    @Test
    fun `두 번 수락해도 200이다`() {
        // given
        val invitationId = invite()
        roomInvitationService.accept(requireNotNull(invitee.id), invitationId, RoomJoinRequest(requireNotNull(inviteeGroup.id)))

        // when
        val result = acceptRequest(invitationId, invitee, inviteeGroup)

        // then
        result.andExpect { status { isOk() } }
    }

    @Test
    fun `남의 그룹으로 수락하면 404 GROUP_NOT_FOUND이고 초대는 대기 중으로 남는다`() {
        // given
        val invitationId = invite()

        // when
        val result = acceptRequest(invitationId, invitee, strangerGroup)

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("GROUP_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/invitations") { with(loginAs(invitee)) }
            .andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `남에게 온 초대를 수락하면 404 INVITATION_NOT_FOUND`() {
        // given
        val invitationId = invite()

        // when
        val result = acceptRequest(invitationId, stranger, strangerGroup)

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("INVITATION_NOT_FOUND") }
        }
    }
}
