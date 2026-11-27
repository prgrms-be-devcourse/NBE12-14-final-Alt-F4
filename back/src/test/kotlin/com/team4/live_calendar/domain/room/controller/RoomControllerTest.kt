package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.room.dto.RoomCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomDetailResponse
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.service.RoomMembership
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomService: RoomService,
    @Autowired private val roomMembership: RoomMembership,
    @Autowired private val subscribeRepository: SubscribeRepository,
    @Autowired private val publicIds: PublicIds,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var other: User
    private lateinit var ownerGroup: CalendarGroup
    private lateinit var otherGroup: CalendarGroup

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "방장"))
        other = persist(User.signUp("other@mail.com", "{bcrypt}hash", "참여자"))
        ownerGroup = persist(CalendarGroup.create(owner, "모임", null))
        otherGroup = persist(CalendarGroup.create(other, "모임", null))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    private fun roomJson(
        startDate: String = "2026-11-24",
        endDate: String = "2026-11-28",
        dailyStartTime: String = "09:00",
        dailyEndTime: String = "18:00",
        timezone: String = "Asia/Seoul",
        groupId: Long? = ownerGroup.id,
    ) = """
        {"name": "친구 모임", "startDate": "$startDate", "endDate": "$endDate",
         "dailyStartTime": "$dailyStartTime", "dailyEndTime": "$dailyEndTime", "timezone": "$timezone",
         "groupId": $groupId}
    """

    private fun createRoom(
        by: User = owner,
        group: CalendarGroup = ownerGroup,
        name: String = "친구 모임",
    ): RoomDetailResponse =
        roomService.create(
            requireNotNull(by.id),
            RoomCreateRequest(
                name = name,
                startDate = LocalDate.parse("2026-11-24"),
                endDate = LocalDate.parse("2026-11-28"),
                dailyStartTime = LocalTime.parse("09:00"),
                dailyEndTime = LocalTime.parse("18:00"),
                timezone = "Asia/Seoul",
                groupId = requireNotNull(group.id),
            ),
        )

    // 응답의 id는 방 코드라서 디코딩해서 엔티티를 찾는다
    private fun roomEntity(code: String): SchedulingRoom =
        em.find(SchedulingRoom::class.java, requireNotNull(publicIds.room.decode(code)))

    private fun join(roomId: String, user: User, group: CalendarGroup) {
        roomMembership.join(roomEntity(roomId), user, group)
    }

    // ===== 생성 =====

    @Test
    fun `방을 만들면 201과 방장 한 명이 참여한 방을 반환한다`() {
        // when
        val result = mockMvc.post("/api/v1/rooms") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = roomJson()
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("친구 모임") }
            jsonPath("$.dailyStartTime") { value("09:00:00") }
            jsonPath("$.timezone") { value("Asia/Seoul") }
            jsonPath("$.isOwner") { value(true) }
            jsonPath("$.members.length()") { value(1) }
            jsonPath("$.members[0].nickname") { value("방장") }
            jsonPath("$.members[0].tag") { value(publicIds.user.encode(requireNotNull(owner.id))) }
            jsonPath("$.members[0].isOwner") { value(true) }
        }
    }

    @Test
    fun `방 id는 숫자가 아니라 방 PK를 인코딩한 코드다`() {
        // when
        val room = createRoom()

        // then
        assertEquals(true, Regex("^[0-9A-HJKMNP-TV-Z]{6,}$").matches(room.id))
        assertEquals(roomEntity(room.id).id, publicIds.room.decode(room.id))
    }

    @Test
    fun `방장도 공통 캘린더를 고른 그룹에 VIEWER로 넣는다`() {
        // when
        val room = createRoom()

        // then
        val subscribe = subscribeRepository.findByUserIdAndCalendarId(requireNotNull(owner.id), room.calendarId)
        assertEquals(CalendarRole.VIEWER, subscribe?.role)
        assertEquals(ownerGroup.id, subscribe?.group?.id)
    }

    @Test
    fun `남의 그룹을 고르면 404 GROUP_NOT_FOUND`() {
        // when
        val result = mockMvc.post("/api/v1/rooms") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = roomJson(groupId = otherGroup.id)
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("GROUP_NOT_FOUND") }
        }
    }

    @Test
    fun `끝 날짜가 시작 날짜보다 빠르면 400 INVALID_ROOM_PERIOD`() {
        // when
        val result = mockMvc.post("/api/v1/rooms") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = roomJson(startDate = "2026-11-28", endDate = "2026-11-24")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ROOM_PERIOD") }
        }
    }

    @Test
    fun `자정을 넘는 시간 범위로도 방을 만들 수 있다`() {
        // when
        val result = mockMvc.post("/api/v1/rooms") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = roomJson(dailyStartTime = "22:00", dailyEndTime = "02:00")
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.dailyStartTime") { value("22:00:00") }
            jsonPath("$.dailyEndTime") { value("02:00:00") }
        }
    }

    @Test
    fun `조율 기간 길이에는 제한이 없다`() {
        // when
        val result = mockMvc.post("/api/v1/rooms") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = roomJson(startDate = "2026-11-01", endDate = "2027-10-31")
        }

        // then
        result.andExpect { status { isCreated() } }
    }

    @Test
    fun `없는 시간대 이름이면 400 INVALID_INPUT`() {
        // when
        val result = mockMvc.post("/api/v1/rooms") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = roomJson(timezone = "Mars/Base")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // ===== 목록 =====

    @Test
    fun `목록에는 내가 참여 중인 방만 나온다`() {
        // given
        val mine = createRoom(by = owner, group = ownerGroup, name = "내 방")
        createRoom(by = other, group = otherGroup, name = "남의 방")

        // when
        val result = mockMvc.get("/api/v1/rooms") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(mine.id) }
            jsonPath("$[0].isOwner") { value(true) }
            jsonPath("$[0].calendarId") { value(mine.calendarId) }
        }
    }

    // ===== 상세 =====

    @Test
    fun `참여자는 상세에서 참여자 목록을 본다`() {
        // given
        val room = createRoom()
        join(room.id, other, otherGroup)

        // when
        val result = mockMvc.get("/api/v1/rooms/${room.id}") { with(loginAs(other)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.isOwner") { value(false) }
            jsonPath("$.members.length()") { value(2) }
            jsonPath("$.members[1].nickname") { value("참여자") }
            jsonPath("$.members[1].isOwner") { value(false) }
        }
    }

    @Test
    fun `참여하지 않은 회원이 상세를 보면 없는 방과 같은 404 ROOM_NOT_FOUND`() {
        // given
        val room = createRoom()

        // when
        val result = mockMvc.get("/api/v1/rooms/${room.id}") { with(loginAs(other)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `형식은 맞지만 없는 방이면 404 ROOM_NOT_FOUND`() {
        // given
        val missing = publicIds.room.encode(999_999L)

        // when
        val result = mockMvc.get("/api/v1/rooms/$missing") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `못 읽는 코드도 400이 아니라 404 ROOM_NOT_FOUND`() {
        // when: 6자 미만, Crockford에 없는 글자(U)
        val tooShort = mockMvc.get("/api/v1/rooms/ZZ") { with(loginAs(owner)) }
        val badChar = mockMvc.get("/api/v1/rooms/UUUUUU") { with(loginAs(owner)) }

        // then
        tooShort.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ROOM_NOT_FOUND") }
        }
        badChar.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `소문자로 보낸 방 코드도 같은 방이다`() {
        // given
        val room = createRoom()

        // when
        val result = mockMvc.get("/api/v1/rooms/${room.id.lowercase()}") { with(loginAs(owner)) }

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(room.id) }
        }
    }
}
