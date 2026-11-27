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
import com.team4.live_calendar.domain.share.entity.ShareTagConnection
import com.team4.live_calendar.domain.share.entity.Tag
import com.team4.live_calendar.domain.share.repository.ShareInfoRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val em: EntityManager,
    @Autowired private val shareInfoRepository: ShareInfoRepository,
    @Autowired private val calendarGroupRepository: CalendarGroupRepository,
    @Autowired private val subscribeRepository: SubscribeRepository,
    @Autowired private val publicIds: PublicIds,
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

        val shareId = readInternalShareId(response.contentAsString)
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

        val shareId = readInternalShareId(response.contentAsString)
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

    private fun postShare(
        user: User,
        calendarIds: List<Long>,
        isPublic: Boolean = true,
    ) = mockMvc.post("/api/v1/shares") {
        with(authentication(
            UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
        ))
        contentType = MediaType.APPLICATION_JSON
        content = """
        {
            "title": "동아리 공지",
            "calendarIds": [${calendarIds.joinToString()}],
            "isPublic": $isPublic
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
        val groupName = "운동/주말"

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
        val shareCode = JsonPath.read<String>(shareResponse.contentAsString, "$.shareId")

        val response = mockMvc.post("/api/v1/shares/{shareId}/subscriptions", shareCode) {
            with(authentication(UsernamePasswordAuthenticationToken(subscriberId.toString(), null, emptyList())))
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupName": "  $groupName  ", "calendarIds": [${calendarIds.joinToString()}]}"""
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
        val shareCode = JsonPath.read<String>(shareResponse.contentAsString, "$.shareId")

        mockMvc.post("/api/v1/shares/{shareId}/subscriptions", shareCode) {
            with(authentication(UsernamePasswordAuthenticationToken(subscriberId.toString(), null, emptyList())))
            contentType = MediaType.APPLICATION_JSON
            content = """
            {"groupName": "운동", "calendarIds": [$sharedCalendarId, $unsharedCalendarId]}
        """.trimIndent()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
        }

        assertTrue(calendarGroupRepository.findAllByUserIdOrdered(subscriberId).isEmpty())
        assertTrue(subscribeRepository.findAllWithCalendarByUserId(subscriberId).isEmpty())
    }

    @Test
    fun `다른 사용자의 공개 공유 항목을 조회하면 캘린더를 표시 순서대로 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val firstId = createOwnedCalendar(owner, "야구")
        val secondId = createOwnedCalendar(owner, "축구")

        val response = postShare(owner, listOf(secondId, firstId))
            .andExpect { status { isCreated() } }
            .andReturn().response
        val shareId = readInternalShareId(response.contentAsString)

        em.flush()
        em.clear()

        getShareDetail(reader, shareId).andExpect {
            status { isOk() }
            jsonPath("$.shareId") { value(publicIds.share.encode(shareId)) }
            jsonPath("$.title") { value("동아리 공지") }
            jsonPath("$.calendars.length()") { value(2) }
            jsonPath("$.calendars[0].calendarId") { value(secondId) }
            jsonPath("$.calendars[0].name") { value("축구") }
            jsonPath("$.calendars[1].calendarId") { value(firstId) }
            jsonPath("$.calendars[1].name") { value("야구") }
        }
    }

    @Test
    fun `비공개 공유 항목을 조회하면 404를 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val calendarId = createOwnedCalendar(owner, "비공개 캘린더")
        val response = postShare(owner, listOf(calendarId), isPublic = false)
            .andExpect { status { isCreated() } }
            .andReturn().response
        val shareId = readInternalShareId(response.contentAsString)

        getShareDetail(owner, shareId).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_NOT_FOUND") }
        }
    }

    @Test
    fun `존재하지 않는 공유 항목을 조회하면 404를 반환한다`() {
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))

        getShareDetail(reader, Long.MAX_VALUE).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_NOT_FOUND") }
        }
    }

    private fun readInternalShareId(responseBody: String): Long {
        val shareCode = JsonPath.read<String>(responseBody, "$.shareId")
        return checkNotNull(publicIds.share.decode(shareCode))
    }

    private fun getShareDetail(user: User, shareId: Long) =
        mockMvc.get("/api/v1/shares/{shareId}", publicIds.share.encode(shareId)) {
            with(authentication(
                UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
            ))
        }

    @Test
    fun `잘못된 공유 코드는 상세 조회와 구독 등록에서 404를 반환한다`() {
        val user = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val login = authentication(
            UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
        )
        val invalidShareCode = "UUUUUU"

        mockMvc.get("/api/v1/shares/{shareId}", invalidShareCode) {
            with(login)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_NOT_FOUND") }
        }

        mockMvc.post("/api/v1/shares/{shareId}/subscriptions", invalidShareCode) {
            with(login)
            contentType = MediaType.APPLICATION_JSON
            content = """{"groupName": "운동", "calendarIds": [31]}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PUBLIC_SHARE_CALENDAR_NOT_FOUND") }
        }
    }

    @Test
    fun `공유 탐색은 공개 항목만 최신순으로 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val recent = persist(ShareInfo.create(owner, "최근 공유", null, "일정 소개", isPublic = true))
        val older = persist(ShareInfo.create(owner, "이전 공유", null, null, isPublic = true))
        persist(ShareInfo.create(owner, "비공개 공유", null, null, isPublic = false))

        em.flush()
        setShareCreatedAt(recent, Instant.parse("2026-11-02T00:00:00Z"))
        setShareCreatedAt(older, Instant.parse("2026-11-01T00:00:00Z"))
        em.clear()

        getShareList(reader).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].shareId") { value(publicIds.share.encode(checkNotNull(recent.id))) }
            jsonPath("$[0].title") { value("최근 공유") }
            jsonPath("$[0].content") { value("일정 소개") }
            jsonPath("$[1].shareId") { value(publicIds.share.encode(checkNotNull(older.id))) }
            jsonPath("$[1].title") { value("이전 공유") }
        }
    }

    @Test
    fun `공개 공유 항목이 없으면 빈 배열을 반환한다`() {
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        persist(ShareInfo.create(reader, "내 비공개 공유", null, null, isPublic = false))

        getShareList(reader).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `키워드는 공개 공유 항목의 제목과 소개에서 찾는다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        persist(ShareInfo.create(owner, "MLB 경기 일정", null, null, isPublic = true))
        persist(ShareInfo.create(owner, "이번 주 경기", null, "다가오는 mlb 일정을 확인해요.", isPublic = true))
        persist(ShareInfo.create(owner, "MLB 연습 일정", null, null, isPublic = false))
        persist(ShareInfo.create(owner, "개인 일정", null, "mlb 관람 준비", isPublic = false))
        persist(ShareInfo.create(owner, "축구 일정", null, null, isPublic = true))

        val response = getShareList(reader, keyword = "  mLb  ")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
            .andReturn().response

        val titles = JsonPath.read<List<String>>(response.contentAsString, "$[*].title")
        assertEquals(setOf("MLB 경기 일정", "이번 주 경기"), titles.toSet())
    }

    @Test
    fun `태그 이름이 일치하는 공개 공유 항목을 한 번만 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val sports = persist(Tag.create("Sports"))
        val sportsNews = persist(Tag.create("SportsNews"))
        val tagged = persist(ShareInfo.create(owner, "경기 일정", null, null, isPublic = true))
        val other = persist(ShareInfo.create(owner, "스포츠 뉴스", null, null, isPublic = true))
        val privateShare = persist(ShareInfo.create(owner, "개인 운동 일정", null, null, isPublic = false))
        repeat(2) { persist(ShareTagConnection.create(tagged, sports)) }
        persist(ShareTagConnection.create(other, sportsNews))
        persist(ShareTagConnection.create(privateShare, sports))

        getShareList(reader, tag = "  sports  ").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("경기 일정") }
        }
    }

    @Test
    fun `키워드와 태그를 함께 보내면 두 조건을 만족하는 항목을 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val sports = persist(Tag.create("스포츠"))
        val both = persist(ShareInfo.create(owner, "야구 경기 일정", null, null, isPublic = true))
        val tagOnly = persist(ShareInfo.create(owner, "농구 일정", null, null, isPublic = true))
        persist(ShareInfo.create(owner, "야구 관람 모임", null, null, isPublic = true))
        persist(ShareTagConnection.create(both, sports))
        persist(ShareTagConnection.create(tagOnly, sports))

        getShareList(reader, keyword = "야구", tag = "스포츠").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("야구 경기 일정") }
        }
    }

    @Test
    fun `공백 검색 조건은 생략해 공개 목록을 반환한다`() {
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        persist(ShareInfo.create(reader, "공개 일정", null, null, isPublic = true))
        persist(ShareInfo.create(reader, "비공개 일정", null, null, isPublic = false))

        getShareList(reader, keyword = "  ", tag = "  ").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("공개 일정") }
        }
    }

    private fun getShareList(user: User, keyword: String? = null, tag: String? = null) =
        mockMvc.get("/api/v1/shares") {
            with(authentication(
                UsernamePasswordAuthenticationToken(checkNotNull(user.id).toString(), null, emptyList()),
            ))
            keyword?.let { param("keyword", it) }
            tag?.let { param("tag", it) }
        }

    private fun setShareCreatedAt(share: ShareInfo, createdAt: Instant) {
        em.createQuery("update ShareInfo s set s.createdAt = :createdAt where s.id = :shareId")
            .setParameter("createdAt", createdAt)
            .setParameter("shareId", checkNotNull(share.id))
            .executeUpdate()
    }
}
