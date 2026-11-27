package com.team4.live_calendar.domain.share.controller

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.publicid.PublicIds
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareCalendarPreviewControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val em: EntityManager,
    @Autowired private val publicIds: PublicIds,
    @Autowired private val subscribeRepository: SubscribeRepository,
) {

    @Test
    fun `구독하기 전에도 요청한 캘린더의 카테고리와 기간 내 일정 요약을 볼 수 있다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val calendar = persist(Calendar.create("교육 일정", null, null))
        val otherCalendar = persist(Calendar.create("프로젝트 일정", null, null))
        val category = persist(EventCategory.create(calendar, "특강", "#2F5BEA"))
        val otherCategory = persist(EventCategory.create(otherCalendar, "회의", null))
        val share = createShare(owner, listOf(calendar, otherCalendar), isPublic = true)

        val event = persist(Event.create(
            calendar = calendar,
            category = category,
            user = owner,
            title = "현직자 특강",
            description = "일정 본문",
            location = "특강 강의실",
            startAt = Instant.parse("2026-11-19T10:00:00Z"),
            endAt = Instant.parse("2026-11-19T11:00:00Z"),
        ))
        persist(Event.create(
            otherCalendar, otherCategory, owner, "프로젝트 회의", null, null,
            event.startAt, event.endAt,
        ))
        persist(Event.create(
            calendar, category, owner, "다음 달 특강", null, null,
            Instant.parse("2026-12-19T10:00:00Z"), Instant.parse("2026-12-19T11:00:00Z"),
        ))

        getPreview(reader, share, calendar).andExpect {
            status { isOk() }
            jsonPath("$.calendarId") { value(checkNotNull(calendar.id)) }
            jsonPath("$.categories.length()") { value(1) }
            jsonPath("$.categories[0].id") { value(checkNotNull(category.id)) }
            jsonPath("$.categories[0].name") { value("특강") }
            jsonPath("$.categories[0].color") { value("#2F5BEA") }
            jsonPath("$.events.length()") { value(1) }
            jsonPath("$.events[0].id") { value(checkNotNull(event.id)) }
            jsonPath("$.events[0].categoryId") { value(checkNotNull(category.id)) }
            jsonPath("$.events[0].title") { value("현직자 특강") }
            jsonPath("$.events[0].startAt") { value("2026-11-19T10:00:00Z") }
            jsonPath("$.events[0].occurrenceStart") { value("2026-11-19T10:00:00Z") }
            jsonPath("$.events[0].userId") { doesNotExist() }
            jsonPath("$.events[0].description") { doesNotExist() }
            jsonPath("$.events[0].location") { doesNotExist() }
        }

        assertTrue(subscribeRepository.findAllByUserId(checkNotNull(reader.id)).isEmpty())
    }

    @Test
    fun `비공개 공유 또는 해당 공유에 연결되지 않은 캘린더의 미리보기는 404를 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val publicCalendar = persist(Calendar.create("공개 공유 캘린더", null, null))
        val privateCalendar = persist(Calendar.create("비공개 공유 캘린더", null, null))
        val publicShare = createShare(owner, listOf(publicCalendar), isPublic = true)
        val privateShare = createShare(owner, listOf(privateCalendar), isPublic = false)

        getPreview(reader, publicShare, privateCalendar).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_NOT_FOUND") }
        }
        getPreview(reader, privateShare, privateCalendar).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHARE_NOT_FOUND") }
        }
    }

    @Test
    fun `일정 미리보기의 조회 시작과 종료 시각이 같으면 400을 반환한다`() {
        val user = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val calendar = persist(Calendar.create("교육 일정", null, null))
        val share = createShare(user, listOf(calendar), isPublic = true)

        getPreview(user, share, calendar, to = "2026-10-31T15:00:00Z").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_DATE_RANGE") }
        }
    }

    @Test
    fun `미리보기는 삭제한 회차를 제외하고 옮긴 회차를 변경된 시간으로 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val reader = persist(User.signUp("reader@mail.com", "{bcrypt}hash", "조회자"))
        val calendar = persist(Calendar.create("교육 일정", null, null))
        val share = createShare(owner, listOf(calendar), isPublic = true)

        val series = persist(Event.create(
            calendar = calendar,
            category = null,
            user = owner,
            title = "특강",
            description = null,
            location = null,
            startAt = Instant.parse("2026-11-19T10:00:00Z"),
            endAt = Instant.parse("2026-11-19T11:00:00Z"),
            timezone = "Asia/Seoul",
            rrule = "FREQ=DAILY;COUNT=3",
        ))

        // 11월 20일 회차는 삭제하고, 21일 회차는 1시간 뒤로 옮긴다.
        persist(EventException.create(
            event = series,
            recurrenceId = Instant.parse("2026-11-20T10:00:00Z"),
            type = EventExceptionType.DELETED,
        ))
        persist(EventException.create(
            event = series,
            recurrenceId = Instant.parse("2026-11-21T10:00:00Z"),
            type = EventExceptionType.MODIFIED,
            title = "시간을 옮긴 특강",
            startAt = Instant.parse("2026-11-21T11:00:00Z"),
            endAt = Instant.parse("2026-11-21T12:00:00Z"),
        ))

        getPreview(reader, share, calendar).andExpect {
            status { isOk() }
            jsonPath("$.events.length()") { value(2) }
            jsonPath("$.events[0].startAt") { value("2026-11-19T10:00:00Z") }
            jsonPath("$.events[1].title") { value("시간을 옮긴 특강") }
            jsonPath("$.events[1].startAt") { value("2026-11-21T11:00:00Z") }
            jsonPath("$.events[1].endAt") { value("2026-11-21T12:00:00Z") }
            jsonPath("$.events[1].occurrenceStart") { value("2026-11-21T10:00:00Z") }
        }
    }

    @Test
    fun `공개 공유의 일정 미리보기도 로그인하지 않으면 401을 반환한다`() {
        val owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "공유자"))
        val calendar = persist(Calendar.create("교육 일정", null, null))
        val share = createShare(owner, listOf(calendar), isPublic = true)

        getPreview(reader = null, share = share, calendar = calendar).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    private fun createShare(owner: User, calendars: List<Calendar>, isPublic: Boolean): ShareInfo {
        val share = persist(ShareInfo.create(owner, "교육 일정 모음", null, null, isPublic))
        calendars.forEachIndexed { index, calendar ->
            persist(ShareCalendarConnection.create(share, calendar, index + 1))
        }
        return share
    }

    private fun getPreview(
        reader: User?,
        share: ShareInfo,
        calendar: Calendar,
        to: String = "2026-11-30T15:00:00Z",
    ) = mockMvc.get(
        "/api/v1/shares/{shareId}/calendars/{calendarId}/preview",
        publicIds.share.encode(checkNotNull(share.id)),
        checkNotNull(calendar.id),
    ) {
        if (reader != null) {
            with(authentication(
                UsernamePasswordAuthenticationToken(checkNotNull(reader.id).toString(), null, emptyList()),
            ))
        }
        param("from", "2026-10-31T15:00:00Z")
        param("to", to)
        param("timezone", "Asia/Seoul")
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }
}
