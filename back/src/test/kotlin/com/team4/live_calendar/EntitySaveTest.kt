package com.team4.live_calendar

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarInvitation
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.InvitationStatus
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.room.entity.RoomInvitation
import com.team4.live_calendar.domain.room.entity.RoomInvitationStatus
import com.team4.live_calendar.domain.room.entity.RoomInviteLink
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomMessage
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.entity.SharedEventDisplayMode
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.share.entity.ShareTagConnection
import com.team4.live_calendar.domain.share.entity.Tag
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

// 모든 엔티티를 한 번씩 저장하고 다시 읽어서, 매핑(연관관계, enum, JSON)이 실제로 동작하는지 확인
@DataJpaTest
@Import(JpaConfig::class)
class EntitySaveTest(
    @Autowired private val em: EntityManager,
) {

    private fun <T : Any> save(entity: T): T = entity.also { em.persist(it) }

    @Test
    fun `모든 엔티티를 저장하고 다시 조회할 수 있다`() {
        val start = Instant.parse("2026-11-18T10:00:00Z")
        val end = Instant.parse("2026-11-18T11:00:00Z")
        val hash = "a".repeat(64)

        val user = save(User.signUp("owner@mail.com", "{bcrypt}hash", "방장"))
        val guest = save(User.signUp("guest@mail.com", "{bcrypt}hash", "손님"))
        val calendar = save(Calendar.create("팀 캘린더", null, null))
        val group = save(CalendarGroup.create(user, "내 캘린더", null))
        val subscribe = save(Subscribe.create(user, group, calendar, CalendarRole.OWNER, null))
        save(CalendarInvitation.create(calendar, user, hash, end))
        val category = save(EventCategory.create(calendar, "회의", "#FF0000"))
        save(CategoryOption.create(subscribe, category, calendar))
        val event = save(
            Event.create(calendar, category, user, "팀 회의", null, null, start, end, rrule = "FREQ=WEEKLY"),
        )
        save(EventException.create(event, start, EventExceptionType.DELETED))
        save(EventOption.create(subscribe, event, calendar))
        val share = save(ShareInfo.create(user, "공유 페이지", null, null))
        save(ShareCalendarConnection.create(share, calendar, 1))
        save(ShareTagConnection.create(share, save(Tag.create("스터디"))))
        save(Notification.create(user, calendar, NotificationType.MEMBER_JOINED, mapOf("nickname" to "손님")))
        val room = save(
            SchedulingRoom.create(
                user, calendar, "회식 날짜", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 27),
                LocalTime.of(9, 0), LocalTime.of(18, 0), "Asia/Seoul",
            ),
        )
        val member = save(RoomMember.create(room, user))
        save(RoomInvitation.create(room, user, guest))
        save(RoomInviteLink.create(room, user, hash))
        save(RoomMessage.create(member, "언제 괜찮으세요?"))
        save(RoomSharedEvent.create(member, event))
        em.flush()
        em.clear()

        assertEquals(CalendarRole.OWNER, em.find(Subscribe::class.java, subscribe.id).role)
        val invitation = em.createQuery("select i from CalendarInvitation i", CalendarInvitation::class.java).singleResult
        assertEquals(CalendarRole.VIEWER, invitation.role)
        assertEquals(InvitationStatus.ACTIVE, invitation.status)
        val notification = em.createQuery("select n from Notification n", Notification::class.java).singleResult
        assertEquals(mapOf("nickname" to "손님"), notification.params)
        val roomInvitation = em.createQuery("select r from RoomInvitation r", RoomInvitation::class.java).singleResult
        assertEquals(RoomInvitationStatus.PENDING, roomInvitation.status)
        val shared = em.createQuery("select r from RoomSharedEvent r", RoomSharedEvent::class.java).singleResult
        assertEquals(SharedEventDisplayMode.TIME_ONLY, shared.displayMode)
        assertEquals(category.id, em.find(Event::class.java, event.id).category?.id)
    }
}
