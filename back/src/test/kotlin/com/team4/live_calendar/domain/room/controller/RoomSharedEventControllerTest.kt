package com.team4.live_calendar.domain.room.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.service.SubscribeService
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.domain.room.dto.RoomCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomDetailResponse
import com.team4.live_calendar.domain.room.dto.SharedEventCreateRequest
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.service.RoomMembership
import com.team4.live_calendar.domain.room.service.RoomService
import com.team4.live_calendar.domain.room.service.RoomSharedEventService
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds
import jakarta.persistence.EntityManager
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomSharedEventControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomService: RoomService,
    @Autowired private val roomMembership: RoomMembership,
    @Autowired private val roomSharedEventService: RoomSharedEventService,
    @Autowired private val eventService: EventService,
    @Autowired private val subscribeService: SubscribeService,
    @Autowired private val publicIds: PublicIds,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var guest: User
    private lateinit var guestGroup: CalendarGroup
    private lateinit var guestCalendar: Calendar
    private lateinit var room: RoomDetailResponse

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "방장"))
        guest = persist(User.signUp("guest@mail.com", "{bcrypt}hash", "손님"))
        guestCalendar = persist(Calendar.create("회사", null, null))
        guestGroup = persist(CalendarGroup.create(guest, "내 캘린더", null))
        persist(Subscribe.create(guest, guestGroup, guestCalendar, CalendarRole.OWNER, null))
        val ownerGroup = persist(CalendarGroup.create(owner, "모임", null))

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
        roomMembership.join(roomEntity(room.id), guest, guestGroup)
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    // 응답의 id는 방 코드라서 디코딩해서 엔티티를 찾는다
    private fun roomEntity(code: String): SchedulingRoom =
        em.find(SchedulingRoom::class.java, requireNotNull(publicIds.room.decode(code)))

    private fun loginAs(user: User) =
        authentication(UsernamePasswordAuthenticationToken(user.id.toString(), null, emptyList()))

    private fun createEvent(
        title: String = "회의",
        start: String = "2026-11-25T10:00:00+09:00",
        end: String = "2026-11-25T11:00:00+09:00",
        calendarId: Long = requireNotNull(guestCalendar.id),
        by: User = guest,
        rrule: String? = null,
    ): Long = eventService.create(
        requireNotNull(by.id),
        EventCreateRequest(
            calendarId = calendarId,
            categoryId = null,
            title = title,
            description = null,
            location = null,
            startAt = OffsetDateTime.parse(start),
            endAt = OffsetDateTime.parse(end),
            timezone = "Asia/Seoul",
            rrule = rrule,
        ),
    ).id

    // 조율 기간(11/24~28 서울)을 덮는 조회 구간
    private fun sharedEvents(user: User) = mockMvc.get("/api/v1/rooms/${room.id}/shared-events") {
        with(loginAs(user))
        param("from", "2026-11-23T15:00:00Z")
        param("to", "2026-11-28T15:00:00Z")
    }

    private fun share(vararg eventIds: Long): List<Long> =
        roomSharedEventService.share(requireNotNull(guest.id), room.id, SharedEventCreateRequest(eventIds.toList())).map { it.id }

    // ===== 공유 =====

    @Test
    fun `일정 세 개를 공유하면 모두 시간만 표시로 공유된다`() {
        // given
        val ids = listOf(createEvent("A"), createEvent("B"), createEvent("C"))

        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/shared-events") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = """{"eventIds": $ids}"""
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[*].displayMode") { value(everyItem(equalTo("TIME_ONLY"))) }
        }
    }

    @Test
    fun `같은 일정을 다시 공유해도 공유는 하나다`() {
        // given
        val eventId = createEvent()
        share(eventId)

        // when
        share(eventId)

        // then
        sharedEvents(guest)
            .andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `이 방의 확정 일정을 공유하면 400 EVENT_NOT_SHAREABLE`() {
        // given
        // 참여자는 공통 캘린더의 VIEWER라 EventService로 만들 수 없다. 확정 API는 PR6이므로 엔티티로 직접 넣는다
        val roomCalendar = em.find(Calendar::class.java, room.calendarId)
        val confirmed = requireNotNull(
            persist(
                Event.create(
                    roomCalendar, null, owner, "확정", null, null,
                    Instant.parse("2026-11-25T01:00:00Z"), Instant.parse("2026-11-25T02:00:00Z"),
                ),
            ).id,
        )

        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/shared-events") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = """{"eventIds": [$confirmed]}"""
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("EVENT_NOT_SHAREABLE") }
        }
    }

    @Test
    fun `구독한 캘린더의 일정도 공유할 수 있다`() {
        // given
        val publicCalendar = persist(Calendar.create("야구 경기", null, null))
        val publisher = persist(User.signUp("publisher@mail.com", "{bcrypt}hash", "게시자"))
        persist(Subscribe.create(publisher, persist(CalendarGroup.create(publisher, "스포츠", null)), publicCalendar, CalendarRole.OWNER, null))
        persist(Subscribe.create(guest, persist(CalendarGroup.create(guest, "운동", null)), publicCalendar, CalendarRole.SUBSCRIBER, null))
        val game = createEvent(calendarId = requireNotNull(publicCalendar.id), by = publisher)

        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/shared-events") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = """{"eventIds": [$game]}"""
        }

        // then
        result.andExpect {
            status { isCreated() }
            jsonPath("$[0].eventId") { value(game) }
        }
    }

    @Test
    fun `공유한 사람이 그 캘린더 구독을 해지하면 방의 공유 일정에서 빠진다`() {
        // given: 구독한 야구 경기 일정을 공유한 뒤 구독 해지 (공유 행은 남지만 보이지 않아야 한다)
        val publicCalendar = persist(Calendar.create("야구 경기", null, null))
        val publisher = persist(User.signUp("publisher@mail.com", "{bcrypt}hash", "게시자"))
        persist(Subscribe.create(publisher, persist(CalendarGroup.create(publisher, "스포츠", null)), publicCalendar, CalendarRole.OWNER, null))
        persist(Subscribe.create(guest, persist(CalendarGroup.create(guest, "운동", null)), publicCalendar, CalendarRole.SUBSCRIBER, null))
        val game = createEvent(
            start = "2026-11-25T18:30:00+09:00",
            end = "2026-11-25T21:30:00+09:00",
            calendarId = requireNotNull(publicCalendar.id),
            by = publisher,
        )
        share(game)
        subscribeService.cancel(requireNotNull(guest.id), requireNotNull(publicCalendar.id))

        // when
        val result = sharedEvents(owner)

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `참여자가 아니면 공유 일정을 볼 수 없고 404 ROOM_NOT_FOUND`() {
        // given
        val stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "모르는회원"))

        // when
        val result = sharedEvents(stranger)

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `조회 구간이 93일을 넘으면 400 INVALID_DATE_RANGE`() {
        // when
        val result = mockMvc.get("/api/v1/rooms/${room.id}/shared-events") {
            with(loginAs(owner))
            param("from", "2026-11-01T00:00:00Z")
            param("to", "2027-03-01T00:00:00Z")
        }

        // then
        result.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_DATE_RANGE") }
        }
    }

    @Test
    fun `내가 볼 수 없는 일정을 공유하면 404 EVENT_NOT_FOUND`() {
        // given
        val ownerCalendar = persist(Calendar.create("방장 개인", null, null))
        persist(Subscribe.create(owner, persist(CalendarGroup.create(owner, "개인", null)), ownerCalendar, CalendarRole.OWNER, null))
        val ownerEvent = createEvent(calendarId = requireNotNull(ownerCalendar.id), by = owner)

        // when
        val result = mockMvc.post("/api/v1/rooms/${room.id}/shared-events") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = """{"eventIds": [$ownerEvent]}"""
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("EVENT_NOT_FOUND") }
        }
    }

    // ===== 조회 =====

    @Test
    fun `다른 참여자에게는 제목과 캘린더 이름 없이 시간과 닉네임만 보인다`() {
        // given
        share(createEvent("비밀 회의"))

        // when
        val result = sharedEvents(owner)

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$[0].nickname") { value("손님") }
            jsonPath("$[0].isMine") { value(false) }
            jsonPath("$[0].title") { doesNotExist() }
            jsonPath("$[0].eventId") { doesNotExist() }
            jsonPath("$[0].calendarName") { doesNotExist() }
            jsonPath("$[0].startAt") { value("2026-11-25T01:00:00Z") }
        }
    }

    @Test
    fun `내가 공유한 일정에는 제목과 일정 id가 보인다`() {
        // given
        val eventId = createEvent("비밀 회의")
        share(eventId)

        // when
        val result = sharedEvents(guest)

        // then
        result.andExpect {
            status { isOk() }
            jsonPath("$[0].isMine") { value(true) }
            jsonPath("$[0].title") { value("비밀 회의") }
            jsonPath("$[0].eventId") { value(eventId) }
        }
    }

    @Test
    fun `하나만 캘린더 이름 표시로 바꾸면 그 일정만 캘린더 이름이 보인다`() {
        // given
        val sharedIds = share(createEvent("A", "2026-11-25T10:00:00+09:00", "2026-11-25T11:00:00+09:00"),
            createEvent("B", "2026-11-26T10:00:00+09:00", "2026-11-26T11:00:00+09:00"))

        // when
        mockMvc.patch("/api/v1/rooms/${room.id}/shared-events/${sharedIds[0]}") {
            with(loginAs(guest))
            contentType = MediaType.APPLICATION_JSON
            content = """{"displayMode": "CALENDAR_NAME_AND_TIME"}"""
        }.andExpect { status { isOk() } }

        // then
        sharedEvents(owner).andExpect {
            jsonPath("$[0].calendarName") { value("회사") }
            jsonPath("$[1].calendarName") { doesNotExist() }
        }
    }

    @Test
    fun `원본 시간을 고치면 공유 일정에도 바로 반영된다`() {
        // given
        val eventId = createEvent()
        share(eventId)

        // when
        eventService.update(
            requireNotNull(guest.id),
            eventId,
            EventUpdateRequest(
                categoryId = null,
                title = "회의",
                description = null,
                location = null,
                startAt = OffsetDateTime.parse("2026-11-26T14:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-26T15:00:00+09:00"),
            ),
        )

        // then
        sharedEvents(owner)
            .andExpect { jsonPath("$[0].startAt") { value("2026-11-26T05:00:00Z") } }
    }

    @Test
    fun `새로 만든 일정은 자동으로 공유되지 않는다`() {
        // given
        share(createEvent("A"))

        // when
        createEvent("새 일정", "2026-11-27T10:00:00+09:00", "2026-11-27T11:00:00+09:00")

        // then
        sharedEvents(owner)
            .andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `반복 일정은 조율 기간 안의 회차마다 한 줄씩 나온다`() {
        // given - 11/20부터 매일, 조율 기간은 11/24~28 (5일)
        share(createEvent("매일 운동", "2026-11-20T07:00:00+09:00", "2026-11-20T08:00:00+09:00", rrule = "FREQ=DAILY"))

        // when
        val result = sharedEvents(owner)

        // then
        result.andExpect {
            jsonPath("$.length()") { value(5) }
            jsonPath("$[0].startAt") { value("2026-11-23T22:00:00Z") }
        }
    }

    @Test
    fun `남이 공유한 일정의 표시 방식을 바꾸면 404 SHARED_EVENT_NOT_FOUND`() {
        // given
        val sharedId = share(createEvent()).first()

        // when
        val result = mockMvc.patch("/api/v1/rooms/${room.id}/shared-events/$sharedId") {
            with(loginAs(owner))
            contentType = MediaType.APPLICATION_JSON
            content = """{"displayMode": "CALENDAR_NAME_AND_TIME"}"""
        }

        // then
        result.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARED_EVENT_NOT_FOUND") }
        }
    }
}
