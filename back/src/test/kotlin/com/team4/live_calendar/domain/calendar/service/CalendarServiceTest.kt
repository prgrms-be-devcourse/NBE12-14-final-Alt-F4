package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarCreateRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupMoveRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarUpdateRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarInvitation
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class CalendarServiceTest(
    @Autowired private val calendarService: CalendarService,
    @Autowired private val calendarGroupService: CalendarGroupService,
    @Autowired private val calendarRepository: CalendarRepository,
    @Autowired private val calendarGroupRepository: CalendarGroupRepository,
    @Autowired private val subscribeRepository: SubscribeRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var me: User
    private lateinit var other: User
    private lateinit var third: User

    @BeforeEach
    fun setUp() {
        me = persist(User.signUp("me@mail.com", "{bcrypt}hash", "나"))
        other = persist(User.signUp("other@mail.com", "{bcrypt}hash", "다른 사람"))
        third = persist(User.signUp("third@mail.com", "{bcrypt}hash", "세 번째"))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun group(user: User, name: String, displayOrder: Int? = 0) = persist(CalendarGroup.create(user, name, displayOrder))

    private fun calendar(name: String, timezone: String = Calendar.DEFAULT_TIMEZONE) =
        persist(Calendar.create(name, "설명", "#FF0000", timezone))

    // 한 사람은 같은 캘린더에 이용 관계를 한 줄만 둔다 (유일 제약)
    private fun place(user: User, group: CalendarGroup, calendar: Calendar, role: CalendarRole) =
        persist(Subscribe.create(user, group, calendar, role, null))

    private fun createRequest(name: String = "야구", timezone: String? = null, groupId: Long? = null) =
        CalendarCreateRequest(name = name, description = "KBO", color = "#00FF00", timezone = timezone, groupId = groupId)

    private fun updateRequest(name: String = "프로야구", description: String? = null, color: String? = null, timezone: String? = null) =
        CalendarUpdateRequest(name = name, description = description, color = color, timezone = timezone)

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    private fun clearContext() {
        em.flush()
        em.clear()
    }

    private fun count(entity: String, where: String = "", calendarId: Long? = null): Long {
        val query = em.createQuery("select count(x) from $entity x $where", Long::class.javaObjectType)
        if (calendarId != null) query.setParameter("calendarId", calendarId)
        return query.singleResult.toLong()
    }

    // ===== 생성 =====

    @Test
    fun `groupId를 주면 그 그룹에 OWNER로 들어간다`() {
        // given
        group(me, "첫 그룹", 0)
        val sports = group(me, "운동", 1)

        // when
        val result = calendarService.create(me.id!!, createRequest(groupId = sports.id))

        // then
        assertEquals(sports.id, result.groupId)
        assertEquals("OWNER", result.role)
        val subscribe = assertNotNull(subscribeRepository.findByUserIdAndCalendarId(me.id!!, result.id))
        assertEquals(CalendarRole.OWNER, subscribe.role)
        assertEquals(sports.id, subscribe.group.id)
    }

    @Test
    fun `남의 그룹이나 없는 그룹 groupId는 404 GROUP_NOT_FOUND이고 캘린더가 생기지 않는다`() {
        // given
        val othersGroup = group(other, "남의 그룹")
        val before = calendarRepository.count()

        // when, then
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) { calendarService.create(me.id!!, createRequest(groupId = othersGroup.id)) }
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) { calendarService.create(me.id!!, createRequest(groupId = Long.MAX_VALUE)) }
        assertEquals(before, calendarRepository.count())
    }

    @Test
    fun `groupId 없이 만들면 내 첫 그룹(순서 기준)에 들어간다`() {
        // given: 만든 순서와 다르게 순서 0인 그룹이 첫 그룹, 순서가 없는 그룹은 맨 뒤
        group(me, "순서 없음", null)
        group(me, "두 번째", 1)
        val first = group(me, "첫 번째", 0)

        // when
        val result = calendarService.create(me.id!!, createRequest())

        // then
        assertEquals(first.id, result.groupId)
    }

    @Test
    fun `그룹이 없으면 내 캘린더 그룹이 생기고 거기 들어간다`() {
        // when
        val first = calendarService.create(me.id!!, createRequest(name = "야구"))
        val second = calendarService.create(me.id!!, createRequest(name = "축구"))

        // then: 그룹은 하나만 생기고 두 캘린더 모두 그 그룹에 있다
        val groups = calendarGroupRepository.findAllByUserIdOrdered(me.id!!)
        assertEquals(listOf("내 캘린더"), groups.map { it.name })
        assertEquals(0, groups.single().displayOrder)
        assertEquals(groups.single().id, first.groupId)
        assertEquals(groups.single().id, second.groupId)
    }

    @Test
    fun `시간대가 없거나 비어 있으면 UTC로 만든다`() {
        // when
        val none = calendarService.create(me.id!!, createRequest(timezone = null))
        val blank = calendarService.create(me.id!!, createRequest(timezone = " "))
        val seoul = calendarService.create(me.id!!, createRequest(timezone = "Asia/Seoul"))

        // then
        assertEquals("UTC", none.timezone)
        assertEquals("UTC", blank.timezone)
        assertEquals("Asia/Seoul", seoul.timezone)
    }

    @Test
    fun `잘못된 시간대는 400 INVALID_INPUT이고 캘린더와 그룹이 생기지 않는다`() {
        // given
        val before = calendarRepository.count()

        // when
        val e = assertThrows<BusinessException> { calendarService.create(me.id!!, createRequest(timezone = "Mars/Base")) }

        // then
        assertEquals(ErrorCode.INVALID_INPUT, e.errorCode)
        assertEquals("올바르지 않은 시간대입니다: Mars/Base", e.message)
        assertEquals(before, calendarRepository.count())
        assertEquals(emptyList(), calendarGroupRepository.findAllByUserIdOrdered(me.id!!))
    }

    @Test
    fun `이름 앞뒤 공백을 지우고 만든 뒤 내 그룹 조회에 나온다`() {
        // when
        val result = calendarService.create(me.id!!, createRequest(name = "  야구  "))

        // then
        assertEquals("야구", result.name)
        assertEquals("KBO", result.description)
        assertEquals("#00FF00", result.color)
        val calendars = calendarGroupService.findMyGroups(me.id!!).single().calendars
        assertEquals(listOf(result.id), calendars.map { it.id })
        assertEquals("OWNER", calendars.single().role)
    }

    // ===== 상세 =====

    @Test
    fun `이용 관계가 있으면 역할과 상관없이 보고, role과 groupId는 각자 자기 값이다`() {
        // given
        val baseball = calendar("야구")
        val owners = mapOf(
            CalendarRole.OWNER to group(me, "내 그룹"),
            CalendarRole.EDITOR to group(other, "편집자 그룹"),
            CalendarRole.SUBSCRIBER to group(third, "구독 그룹"),
        )
        owners.forEach { (role, g) -> place(g.user, g, baseball, role) }
        val viewer = persist(User.signUp("viewer@mail.com", "{bcrypt}hash", "보기만"))
        val viewerGroup = group(viewer, "보기 그룹")
        place(viewer, viewerGroup, baseball, CalendarRole.VIEWER)

        // when, then
        owners.forEach { (role, g) ->
            val result = calendarService.get(g.user.id!!, baseball.id!!)
            assertEquals(role.name, result.role)
            assertEquals(g.id, result.groupId)
            assertEquals("야구", result.name)
        }
        val viewed = calendarService.get(viewer.id!!, baseball.id!!)
        assertEquals("VIEWER", viewed.role)
        assertEquals(viewerGroup.id, viewed.groupId)
    }

    @Test
    fun `이용 관계가 없으면 403, 없는 캘린더는 404`() {
        // given
        val baseball = calendar("야구")
        place(other, group(other, "남의 그룹"), baseball, CalendarRole.OWNER)

        // when, then
        assertErrorCode(ErrorCode.FORBIDDEN) { calendarService.get(me.id!!, baseball.id!!) }
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) { calendarService.get(me.id!!, Long.MAX_VALUE) }
    }

    // ===== 수정 =====

    @Test
    fun `OWNER가 고치면 바뀌고, 안 보낸 설명과 색상은 비워진다`() {
        // given
        val baseball = calendar("야구", timezone = "Asia/Seoul")
        place(me, group(me, "내 그룹"), baseball, CalendarRole.OWNER)

        // when
        val result = calendarService.update(me.id!!, baseball.id!!, updateRequest(name = "  프로야구  ", timezone = "America/New_York"))

        // then
        assertEquals("프로야구", result.name)
        assertNull(result.description)
        assertNull(result.color)
        assertEquals("America/New_York", result.timezone)
        clearContext()
        val saved = assertNotNull(calendarRepository.findById(baseball.id!!).orElse(null))
        assertEquals("프로야구", saved.name)
        assertNull(saved.description)
        assertNull(saved.color)
        assertEquals("America/New_York", saved.timezone)
    }

    @Test
    fun `시간대를 안 보내거나 비워 보내면 기존 시간대가 그대로 남는다`() {
        // given
        val baseball = calendar("야구", timezone = "Asia/Seoul")
        place(me, group(me, "내 그룹"), baseball, CalendarRole.OWNER)

        // when
        val none = calendarService.update(me.id!!, baseball.id!!, updateRequest(timezone = null))
        val blank = calendarService.update(me.id!!, baseball.id!!, updateRequest(timezone = ""))

        // then
        assertEquals("Asia/Seoul", none.timezone)
        assertEquals("Asia/Seoul", blank.timezone)
        clearContext()
        assertEquals("Asia/Seoul", calendarRepository.findById(baseball.id!!).get().timezone)
    }

    @Test
    fun `OWNER가 아니거나 이용 관계가 없으면 403이고 캘린더는 그대로다`() {
        // given
        val baseball = calendar("야구")
        place(me, group(me, "내 그룹"), baseball, CalendarRole.OWNER)
        val roles = listOf(CalendarRole.EDITOR, CalendarRole.VIEWER, CalendarRole.SUBSCRIBER)
        val users = roles.mapIndexed { i, role ->
            persist(User.signUp("user$i@mail.com", "{bcrypt}hash", "사용자$i")).also { place(it, group(it, "그룹"), baseball, role) }
        }

        // when, then
        (users + other).forEach { user ->
            assertErrorCode(ErrorCode.FORBIDDEN) { calendarService.update(user.id!!, baseball.id!!, updateRequest()) }
        }
        clearContext()
        assertEquals("야구", calendarRepository.findById(baseball.id!!).get().name)
    }

    @Test
    fun `수정에서 잘못된 시간대는 400 INVALID_INPUT이고 캘린더는 그대로다`() {
        // given
        val baseball = calendar("야구")
        place(me, group(me, "내 그룹"), baseball, CalendarRole.OWNER)

        // when, then
        assertErrorCode(ErrorCode.INVALID_INPUT) {
            calendarService.update(me.id!!, baseball.id!!, updateRequest(timezone = "Mars/Base"))
        }
        clearContext()
        assertEquals("야구", calendarRepository.findById(baseball.id!!).get().name)
    }

    @Test
    fun `없는 캘린더 수정은 404`() {
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) { calendarService.update(me.id!!, Long.MAX_VALUE, updateRequest()) }
    }

    // ===== 그룹 이동 =====

    @Test
    fun `그룹을 옮기면 내 배치만 바뀌고 다른 사람의 그룹과 역할은 그대로다`() {
        // given: 게시자는 "스포츠 → 야구", 나는 "운동 → 야구"
        val baseball = calendar("야구")
        val othersGroup = group(other, "스포츠")
        place(other, othersGroup, baseball, CalendarRole.OWNER)
        place(me, group(me, "운동"), baseball, CalendarRole.VIEWER)
        val hobby = group(me, "취미", 1)

        // when
        val result = calendarService.moveGroup(me.id!!, baseball.id!!, CalendarGroupMoveRequest(hobby.id))

        // then
        assertEquals(hobby.id, result.groupId)
        assertEquals("VIEWER", result.role)
        clearContext()
        val mine = subscribeRepository.findByUserIdAndCalendarId(me.id!!, baseball.id!!)!!
        assertEquals(hobby.id, mine.group.id)
        assertEquals(CalendarRole.VIEWER, mine.role)
        val others = subscribeRepository.findByUserIdAndCalendarId(other.id!!, baseball.id!!)!!
        assertEquals(othersGroup.id, others.group.id)
        assertEquals(CalendarRole.OWNER, others.role)
    }

    @Test
    fun `남의 그룹으로는 옮길 수 없고(404) 이용 관계가 없으면 403`() {
        // given
        val baseball = calendar("야구")
        val myGroup = group(me, "운동")
        place(me, myGroup, baseball, CalendarRole.OWNER)
        val othersGroup = group(other, "남의 그룹")

        // when, then
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) {
            calendarService.moveGroup(me.id!!, baseball.id!!, CalendarGroupMoveRequest(othersGroup.id))
        }
        assertErrorCode(ErrorCode.FORBIDDEN) {
            calendarService.moveGroup(other.id!!, baseball.id!!, CalendarGroupMoveRequest(othersGroup.id))
        }
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) {
            calendarService.moveGroup(me.id!!, Long.MAX_VALUE, CalendarGroupMoveRequest(myGroup.id))
        }
        clearContext()
        assertEquals(myGroup.id, subscribeRepository.findByUserIdAndCalendarId(me.id!!, baseball.id!!)!!.group.id)
    }

    @Test
    fun `구독자도 자기 배치를 옮길 수 있다`() {
        // given
        val baseball = calendar("야구")
        place(other, group(other, "스포츠"), baseball, CalendarRole.OWNER)
        place(me, group(me, "구독"), baseball, CalendarRole.SUBSCRIBER)
        val hobby = group(me, "취미", 1)

        // when
        val result = calendarService.moveGroup(me.id!!, baseball.id!!, CalendarGroupMoveRequest(hobby.id))

        // then
        assertEquals(hobby.id, result.groupId)
        assertEquals("SUBSCRIBER", result.role)
    }

    // ===== 삭제 =====

    // 캘린더 하나에 연결 테이블마다 한 줄 이상 넣는다 (하나라도 비면 그 삭제 단계를 빼도 테스트가 통과한다)
    private inner class Filled(val calendar: Calendar, eventCount: Int = 2) {
        val ownerGroup = group(me, "내 그룹 ${calendar.name}")
        val owner = place(me, ownerGroup, calendar, CalendarRole.OWNER)
        val viewer = place(other, group(other, "남의 그룹 ${calendar.name}"), calendar, CalendarRole.VIEWER)
        val subscriber = place(third, group(third, "구독 그룹 ${calendar.name}"), calendar, CalendarRole.SUBSCRIBER)

        // 일정이 카테고리를 달아야 "일정 → 카테고리" 순서가 검증된다
        val category = persist(EventCategory.create(calendar, "경기", "#123456"))
        val start: Instant = Instant.parse("2026-12-01T01:00:00Z")
        val events: List<Event> = (0 until eventCount).map { i ->
            persist(
                Event.create(
                    calendar = calendar,
                    category = if (i == 0) category else null,
                    user = me,
                    title = "일정 $i",
                    description = null,
                    location = null,
                    startAt = start.plus(Duration.ofDays(i.toLong())),
                    endAt = start.plus(Duration.ofDays(i.toLong())).plus(Duration.ofHours(1)),
                    rrule = if (i == 1) "FREQ=DAILY;COUNT=3" else null,
                ),
            )
        }
        val exception = persist(EventException.create(events.last(), events.last().startAt.plus(Duration.ofDays(1)), EventExceptionType.DELETED))
        val eventOption = persist(EventOption.create(viewer, events.first(), calendar, isVisible = false))
        val categoryOption = persist(CategoryOption.create(viewer, category, calendar, isVisible = false))
        val invitation = persist(CalendarInvitation.create(calendar, third, calendar.name.first().toString().repeat(64), start))
        val shareConnection = persist(ShareCalendarConnection.create(persist(ShareInfo.create(me, "공유 ${calendar.name}", null, null)), calendar, 0))
        val notification = persist(Notification.create(other, calendar, NotificationType.EVENT_CREATED))
    }

    // 조율방은 다른 캘린더로 만든다 (지울 캘린더를 쓰면 삭제가 409로 막힌다)
    private fun shareInRoom(event: Event): RoomSharedEvent {
        val room = persist(
            SchedulingRoom.create(
                owner = other,
                calendar = calendar("조율방 공통 ${event.title}"),
                name = "모임",
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 7),
                dailyStartTime = LocalTime.of(9, 0),
                dailyEndTime = LocalTime.of(18, 0),
                timezone = "UTC",
            ),
        )
        return persist(RoomSharedEvent.create(persist(RoomMember.create(room, other)), event))
    }

    @Test
    fun `OWNER가 지우면 캘린더와 연결 데이터가 모두 지워지고 다른 캘린더 데이터는 남는다`() {
        // given
        val target = Filled(calendar("야구"))
        val shared = shareInRoom(target.events.first())
        val kept = Filled(calendar("축구"))
        val keptShared = shareInRoom(kept.events.first())
        val targetId = target.calendar.id!!
        clearContext()

        // when
        calendarService.delete(me.id!!, targetId)
        clearContext()

        // then: 지운 캘린더 쪽은 하나도 남지 않는다
        val byCalendar = "where x.calendar.id = :calendarId"
        val byEvent = "where x.event.calendar.id = :calendarId"
        assertNull(em.find(Calendar::class.java, targetId))
        assertEquals(0, count("Event", byCalendar, targetId))
        assertEquals(0, count("EventException", byEvent, targetId))
        assertEquals(0, count("EventOption", byCalendar, targetId))
        assertEquals(0, count("RoomSharedEvent", byEvent, targetId))
        assertNull(em.find(RoomSharedEvent::class.java, shared.id))
        assertEquals(0, count("EventCategory", byCalendar, targetId))
        assertEquals(0, count("CategoryOption", byCalendar, targetId))
        assertEquals(0, count("Subscribe", byCalendar, targetId))
        assertEquals(0, count("CalendarInvitation", byCalendar, targetId))
        assertEquals(0, count("ShareCalendarConnection", byCalendar, targetId))

        // 알림은 남고 캘린더 칸만 비워진다
        val notification = assertNotNull(em.find(Notification::class.java, target.notification.id))
        assertNull(notification.calendar)

        // 그룹, 조율방·멤버는 남는다
        assertNotNull(em.find(CalendarGroup::class.java, target.ownerGroup.id))
        assertNotNull(em.find(RoomMember::class.java, shared.member.id))
        assertNotNull(em.find(SchedulingRoom::class.java, shared.member.room.id))

        // 다른 캘린더 데이터는 그대로다
        val keptId = kept.calendar.id!!
        assertNotNull(em.find(Calendar::class.java, keptId))
        assertEquals(2, count("Event", byCalendar, keptId))
        assertNotNull(em.find(EventException::class.java, kept.exception.id))
        assertNotNull(em.find(EventOption::class.java, kept.eventOption.id))
        assertNotNull(em.find(RoomSharedEvent::class.java, keptShared.id))
        assertNotNull(em.find(EventCategory::class.java, kept.category.id))
        assertNotNull(em.find(CategoryOption::class.java, kept.categoryOption.id))
        assertEquals(3, count("Subscribe", byCalendar, keptId))
        assertNotNull(em.find(CalendarInvitation::class.java, kept.invitation.id))
        assertNotNull(em.find(ShareCalendarConnection::class.java, kept.shareConnection.id))
        assertEquals(keptId, em.find(Notification::class.java, kept.notification.id).calendar?.id)
    }

    @Test
    fun `조율방이 공통 캘린더로 쓰면 409이고 아무것도 지워지지 않는다`() {
        // given
        val target = Filled(calendar("야구"))
        persist(
            SchedulingRoom.create(
                owner = me,
                calendar = target.calendar,
                name = "모임",
                startDate = LocalDate.of(2026, 12, 1),
                endDate = LocalDate.of(2026, 12, 7),
                dailyStartTime = LocalTime.of(9, 0),
                dailyEndTime = LocalTime.of(18, 0),
                timezone = "UTC",
            ),
        )
        val targetId = target.calendar.id!!
        clearContext()

        // when, then
        assertErrorCode(ErrorCode.CALENDAR_IN_USE_BY_ROOM) { calendarService.delete(me.id!!, targetId) }
        clearContext()
        assertNotNull(em.find(Calendar::class.java, targetId))
        assertEquals(2, count("Event", "where x.calendar.id = :calendarId", targetId))
        assertEquals(3, count("Subscribe", "where x.calendar.id = :calendarId", targetId))
        assertEquals(targetId, em.find(Notification::class.java, target.notification.id).calendar?.id)
    }

    @Test
    fun `OWNER가 아니면 403, 없는 캘린더는 404이고 캘린더는 그대로다`() {
        // given
        val target = Filled(calendar("야구"))
        val targetId = target.calendar.id!!
        clearContext()

        // when, then: VIEWER, SUBSCRIBER, 이용 관계 없음
        listOf(other, third).forEach { user ->
            assertErrorCode(ErrorCode.FORBIDDEN) { calendarService.delete(user.id!!, targetId) }
        }
        val stranger = persist(User.signUp("stranger@mail.com", "{bcrypt}hash", "모르는 사람"))
        assertErrorCode(ErrorCode.FORBIDDEN) { calendarService.delete(stranger.id!!, targetId) }
        assertErrorCode(ErrorCode.CALENDAR_NOT_FOUND) { calendarService.delete(me.id!!, Long.MAX_VALUE) }
        clearContext()
        assertNotNull(em.find(Calendar::class.java, targetId))
    }

    @Test
    fun `EDITOR도 캘린더는 지울 수 없다`() {
        // given
        val baseball = calendar("야구")
        place(me, group(me, "내 그룹"), baseball, CalendarRole.OWNER)
        place(other, group(other, "편집 그룹"), baseball, CalendarRole.EDITOR)

        // when, then
        assertErrorCode(ErrorCode.FORBIDDEN) { calendarService.delete(other.id!!, baseball.id!!) }
    }

    @Test
    fun `캘린더를 지우면 그룹은 남고, 빈 그룹이 되면 그룹을 지울 수 있다`() {
        // given: 캘린더 API로 만든 캘린더가 든 그룹은 지울 수 없다
        val myGroup = group(me, "운동")
        val baseball = calendarService.create(me.id!!, createRequest(groupId = myGroup.id))
        clearContext()
        assertErrorCode(ErrorCode.GROUP_NOT_EMPTY) { calendarGroupService.delete(me.id!!, myGroup.id!!) }

        // when
        calendarService.delete(me.id!!, baseball.id)
        // 테스트가 넣은 이용 관계 엔티티가 컨텍스트에 남아 지운 그룹을 가리키지 않게 비운다 (운영에서는 요청이 따로라 문제없음)
        clearContext()

        // then
        assertNotNull(em.find(CalendarGroup::class.java, myGroup.id))
        calendarGroupService.delete(me.id!!, myGroup.id!!)
        clearContext()
        assertNull(em.find(CalendarGroup::class.java, myGroup.id))
    }

    @Test
    fun `일정이 많아도 삭제 쿼리 수는 같다`() {
        // given: 일정 1개짜리와 5개짜리 캘린더
        val small = Filled(calendar("작은 캘린더"), eventCount = 1)
        shareInRoom(small.events.first())
        val large = Filled(calendar("큰 캘린더"), eventCount = 5)
        large.events.forEach { shareInRoom(it) }
        clearContext()
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        val wasEnabled = statistics.isStatisticsEnabled
        statistics.isStatisticsEnabled = true

        try {
            // when
            statistics.clear()
            calendarService.delete(me.id!!, small.calendar.id!!)
            // 벌크 삭제는 바로 나가지만, 엔티티를 하나씩 지우는 방식으로 바뀌면 DELETE가 flush 때 나가므로 flush까지 해서 센다
            em.flush()
            val smallCount = statistics.prepareStatementCount
            em.clear()

            statistics.clear()
            calendarService.delete(me.id!!, large.calendar.id!!)
            em.flush()
            val largeCount = statistics.prepareStatementCount

            // then: 통계가 안 모이면 0 == 0으로 통과하므로 0보다 큰지도 본다
            assertTrue(smallCount > 0)
            assertEquals(smallCount, largeCount)
        } finally {
            statistics.isStatisticsEnabled = wasEnabled
        }
    }
}
