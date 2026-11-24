package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarGroupRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.entity.EventCategory
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class CalendarGroupServiceTest(
    @Autowired private val calendarGroupService: CalendarGroupService,
    @Autowired private val calendarGroupRepository: CalendarGroupRepository,
    @Autowired private val subscribeRepository: SubscribeRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var me: User
    private lateinit var other: User

    @BeforeEach
    fun setUp() {
        me = persist(User.signUp("me@mail.com", "{bcrypt}hash", "나"))
        other = persist(User.signUp("other@mail.com", "{bcrypt}hash", "다른 사람"))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun group(user: User, name: String, displayOrder: Int?) = persist(CalendarGroup.create(user, name, displayOrder))

    private fun calendar(name: String, color: String? = null) = persist(Calendar.create(name, null, color))

    // 아직 캘린더·구독 API가 없어 이용 관계를 직접 저장한다 (한 사람은 같은 캘린더에 한 줄만)
    private fun place(user: User, group: CalendarGroup, calendar: Calendar, role: CalendarRole, color: String? = null) =
        persist(Subscribe.create(user, group, calendar, role, color))

    private fun request(name: String) = CalendarGroupRequest(name)

    private fun assertErrorCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(expected, e.errorCode)
    }

    // ===== 생성 =====

    @Test
    fun `첫 그룹의 순서는 0이고 다음 그룹은 1이다`() {
        // when
        val first = calendarGroupService.create(me.id!!, request("운동"))
        val second = calendarGroupService.create(me.id!!, request("공부"))

        // then
        assertEquals(0, first.displayOrder)
        assertEquals(1, second.displayOrder)
    }

    @Test
    fun `새 그룹은 남의 그룹 순서와 상관없이 내 그룹 중 가장 뒤에 붙는다`() {
        // given
        group(me, "기존", 4)
        group(other, "남의 그룹", 9)

        // when
        val created = calendarGroupService.create(me.id!!, request("새 그룹"))

        // then
        assertEquals(5, created.displayOrder)
    }

    @Test
    fun `그룹 이름의 앞뒤 공백은 지워진다`() {
        // when
        val created = calendarGroupService.create(me.id!!, request("  운동  "))

        // then
        assertEquals("운동", created.name)
        assertEquals("운동", calendarGroupRepository.findById(created.id).get().name)
    }

    @Test
    fun `같은 이름의 그룹을 여러 개 만들 수 있다`() {
        // when
        val first = calendarGroupService.create(me.id!!, request("운동"))
        val second = calendarGroupService.create(me.id!!, request("운동"))

        // then
        assertNotEquals(first.id, second.id)
    }

    // ===== 이름 수정 =====

    @Test
    fun `그룹 이름을 바꾸면 앞뒤 공백을 지우고 저장한다`() {
        // given
        val group = group(me, "운동", 0)

        // when
        val renamed = calendarGroupService.rename(me.id!!, group.id!!, request("  스포츠 "))

        // then
        assertEquals("스포츠", renamed.name)
        assertEquals(0, renamed.displayOrder)
        assertEquals("스포츠", calendarGroupRepository.findById(group.id!!).get().name)
    }

    @Test
    fun `남의 그룹이나 없는 그룹의 이름을 바꾸면 404 GROUP_NOT_FOUND`() {
        // given
        val othersGroup = group(other, "남의 그룹", 0)

        // then
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) { calendarGroupService.rename(me.id!!, othersGroup.id!!, request("내 거")) }
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) { calendarGroupService.rename(me.id!!, Long.MAX_VALUE, request("내 거")) }
        assertEquals("남의 그룹", calendarGroupRepository.findById(othersGroup.id!!).get().name)
    }

    // ===== 삭제 =====

    @Test
    fun `빈 그룹은 마지막 하나여도 지울 수 있다`() {
        // given
        val only = group(me, "하나뿐인 그룹", 0)

        // when
        calendarGroupService.delete(me.id!!, only.id!!)

        // then
        assertNull(calendarGroupRepository.findById(only.id!!).orElse(null))
        assertTrue(calendarGroupService.findMyGroups(me.id!!).isEmpty())
    }

    @Test
    fun `내 캘린더가 든 그룹을 지우면 409 GROUP_NOT_EMPTY이고 그룹과 이용 관계가 그대로 남는다`() {
        // given
        val group = group(me, "운동", 0)
        val subscribe = place(me, group, calendar("야구"), CalendarRole.OWNER)

        // when
        assertErrorCode(ErrorCode.GROUP_NOT_EMPTY) { calendarGroupService.delete(me.id!!, group.id!!) }

        // then
        assertNotNull(calendarGroupRepository.findById(group.id!!).orElse(null))
        assertNotNull(subscribeRepository.findById(subscribe.id!!).orElse(null))
    }

    @Test
    fun `남의 캘린더를 둔 그룹도 지우면 409 GROUP_NOT_EMPTY`() {
        // given: 남이 만든 캘린더를 내 그룹에 둠 (구독 API가 아직 없어 조회 역할로 저장)
        val othersCalendar = calendar("남의 일정")
        place(other, group(other, "게시자 그룹", 0), othersCalendar, CalendarRole.OWNER)
        val myGroup = group(me, "구독", 0)
        place(me, myGroup, othersCalendar, CalendarRole.VIEWER)

        // then
        assertErrorCode(ErrorCode.GROUP_NOT_EMPTY) { calendarGroupService.delete(me.id!!, myGroup.id!!) }
    }

    @Test
    fun `남의 그룹을 지우면 404 GROUP_NOT_FOUND이고 그룹이 그대로 남는다`() {
        // given
        val othersGroup = group(other, "남의 그룹", 0)

        // when
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) { calendarGroupService.delete(me.id!!, othersGroup.id!!) }

        // then
        assertNotNull(calendarGroupRepository.findById(othersGroup.id!!).orElse(null))
    }

    @Test
    fun `없는 그룹을 지우면 404 GROUP_NOT_FOUND`() {
        // then
        assertErrorCode(ErrorCode.GROUP_NOT_FOUND) { calendarGroupService.delete(me.id!!, Long.MAX_VALUE) }
    }

    // ===== 조회 =====

    @Test
    fun `그룹이 없으면 빈 목록이다`() {
        // then
        assertTrue(calendarGroupService.findMyGroups(me.id!!).isEmpty())
    }

    @Test
    fun `내 그룹만 나온다`() {
        // given
        group(me, "내 그룹", 0)
        group(other, "남의 그룹", 0)

        // then
        assertEquals(listOf("내 그룹"), calendarGroupService.findMyGroups(me.id!!).map { it.name })
    }

    @Test
    fun `그룹은 순서대로, 순서가 같으면 먼저 만든 그룹부터, 순서가 없으면 맨 뒤에 나온다`() {
        // given
        group(me, "순서 없음", null)
        group(me, "둘째", 1)
        group(me, "첫째", 0)
        group(me, "둘째와 같은 순서", 1)

        // then
        assertEquals(
            listOf("첫째", "둘째", "둘째와 같은 순서", "순서 없음"),
            calendarGroupService.findMyGroups(me.id!!).map { it.name },
        )
    }

    @Test
    fun `캘린더는 내가 둔 그룹 아래에 나오고 빈 그룹은 빈 목록이다`() {
        // given
        val sports = group(me, "운동", 0)
        val study = group(me, "공부", 1)
        group(me, "빈 그룹", 2)
        place(me, sports, calendar("야구"), CalendarRole.OWNER)
        place(me, study, calendar("토익"), CalendarRole.OWNER)
        place(me, sports, calendar("축구"), CalendarRole.OWNER)
        em.flush()
        em.clear()

        // when
        val result = calendarGroupService.findMyGroups(me.id!!)

        // then
        assertEquals(listOf("운동", "공부", "빈 그룹"), result.map { it.name })
        assertEquals(listOf("야구", "축구"), result[0].calendars.map { it.name })
        assertEquals(listOf("토익"), result[1].calendars.map { it.name })
        assertTrue(result[2].calendars.isEmpty())
    }

    @Test
    fun `그룹 안 캘린더는 캘린더를 만든 순서가 아니라 그룹에 둔 순서대로 나온다`() {
        // given: 캘린더는 야구 → 축구 순서로 만들고, 그룹에는 축구 → 야구 순서로 둠
        val group = group(me, "운동", 0)
        val baseball = calendar("야구")
        val soccer = calendar("축구")
        place(me, group, soccer, CalendarRole.OWNER)
        place(me, group, baseball, CalendarRole.OWNER)

        // then
        assertEquals(listOf("축구", "야구"), calendarGroupService.findMyGroups(me.id!!).single().calendars.map { it.name })
    }

    @Test
    fun `그룹과 캘린더와 카테고리가 많아도 조회 쿼리는 세 번이다`() {
        // given: 그룹 3개에 캘린더 2개씩, 캘린더마다 카테고리 2개씩
        repeat(3) { i ->
            val group = group(me, "그룹 $i", i)
            repeat(2) { j ->
                val calendar = calendar("캘린더 $i-$j")
                place(me, group, calendar, CalendarRole.OWNER)
                repeat(2) { k -> persist(EventCategory.create(calendar, "카테고리 $i-$j-$k", null)) }
            }
        }
        // 직접 저장한 엔티티가 영속성 컨텍스트에 남아 있으면 지연 로딩 쿼리가 안 보이므로 비운다
        em.flush()
        em.clear()
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        val wasEnabled = statistics.isStatisticsEnabled
        statistics.isStatisticsEnabled = true
        statistics.clear()

        try {
            // when
            val result = calendarGroupService.findMyGroups(me.id!!)

            // then: 그룹 한 번, 이용 관계 + 캘린더 한 번, 카테고리 한 번
            assertEquals(6, result.sumOf { it.calendars.size })
            assertEquals(12, result.sumOf { group -> group.calendars.sumOf { it.categories.size } })
            assertEquals(3, statistics.prepareStatementCount)
        } finally {
            statistics.isStatisticsEnabled = wasEnabled
        }
    }

    @Test
    fun `같은 캘린더를 다른 사람이 자기 그룹에 둬도 내 응답에는 내 그룹으로 나온다`() {
        // given: 게시자는 "스포츠 → 야구", 나는 "운동 → 야구"로 정리
        val baseball = calendar("야구")
        place(other, group(other, "스포츠", 0), baseball, CalendarRole.OWNER)
        place(me, group(me, "운동", 0), baseball, CalendarRole.VIEWER)

        // when
        val result = calendarGroupService.findMyGroups(me.id!!)

        // then
        assertEquals(listOf("운동"), result.map { it.name })
        assertEquals(listOf(baseball.id), result[0].calendars.map { it.id })
    }

    @Test
    fun `캘린더 색상은 내 색상이 있으면 그것, 없으면 원본 색상이다`() {
        // given
        val group = group(me, "운동", 0)
        place(me, group, calendar("야구", color = "#FF0000"), CalendarRole.OWNER, color = "#00FF00")
        place(me, group, calendar("축구", color = "#0000FF"), CalendarRole.OWNER)
        place(me, group, calendar("농구"), CalendarRole.OWNER)

        // when
        val calendars = calendarGroupService.findMyGroups(me.id!!).single().calendars

        // then
        assertEquals(listOf("#00FF00", "#0000FF", null), calendars.map { it.color })
    }

    @Test
    fun `캘린더의 역할은 내 이용 관계의 역할이다`() {
        // given: 같은 캘린더를 게시자는 OWNER, 나는 VIEWER로 이용
        val baseball = calendar("야구")
        place(other, group(other, "스포츠", 0), baseball, CalendarRole.OWNER)
        val myGroup = group(me, "운동", 0)
        place(me, myGroup, baseball, CalendarRole.VIEWER)
        place(me, myGroup, calendar("내 일정"), CalendarRole.EDITOR)

        // when
        val calendars = calendarGroupService.findMyGroups(me.id!!).single().calendars

        // then
        assertEquals(listOf("VIEWER", "EDITOR"), calendars.map { it.role })
    }
}
