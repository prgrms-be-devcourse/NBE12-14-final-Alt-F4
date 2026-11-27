package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.domain.notification.service.EventHistoryService
import com.team4.live_calendar.domain.notification.service.NotificationOutboxService
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.sql.SQLException
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaConfig::class, SubscribeService::class, CalendarGroupService::class, EventService::class,
    EventHistoryService::class, NotificationOutboxService::class,)
class SubscribeServiceTest(
    @Autowired private val service: SubscribeService,
    @Autowired private val em: EntityManager,
    @Autowired private val eventService: EventService,
) {
    @Test
    fun `원본 캘린더를 내 그룹에 구독하면 SUBSCRIBER 관계가 저장된다`() {
        val user = User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자")
        em.persist(user)

        val group = CalendarGroup.create(user, "내 그룹", null)
        em.persist(group)

        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(calendar)

        val userId = checkNotNull(user.id)
        val groupId = checkNotNull(group.id)
        val calendarId = checkNotNull(calendar.id)

        val subscriptionId = service.subscribe(userId, groupId, calendarId)

        em.flush()
        em.clear()

        val saved = checkNotNull(em.find(Subscribe::class.java, subscriptionId))
        assertEquals(userId, saved.user.id)
        assertEquals(groupId, saved.group.id)
        assertEquals(calendarId, saved.calendar.id)
        assertEquals(CalendarRole.SUBSCRIBER, saved.role)
    }

    @Test
    fun `다른 사용자의 그룹에 구독할 수 없다`() {
        val requester = User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자")
        val groupOwner = User.signUp("owner@mail.com", "{bcrypt}hash", "그룹 소유자")
        em.persist(requester)
        em.persist(groupOwner)

        val otherGroup = CalendarGroup.create(groupOwner, "다른 사람의 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(otherGroup)
        em.persist(calendar)

        val exception = assertFailsWith<BusinessException> {
            service.subscribe(
                checkNotNull(requester.id),
                checkNotNull(otherGroup.id),
                checkNotNull(calendar.id),
            )
        }

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
    }

    @Test
    fun `이미 소유한 캘린더는 다른 내 그룹에도 중복 구독할 수 없다`() {
        val user = User.signUp("owner@mail.com", "{bcrypt}hash", "소유자")
        em.persist(user)

        val originalGroup = CalendarGroup.create(user, "기존 그룹", null)
        val targetGroup = CalendarGroup.create(user, "다른 내 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(originalGroup)
        em.persist(targetGroup)
        em.persist(calendar)
        em.persist(Subscribe.create(user, originalGroup, calendar, CalendarRole.OWNER, null))

        val exception = assertFailsWith<BusinessException> {
            service.subscribe(
                checkNotNull(user.id),
                checkNotNull(targetGroup.id),
                checkNotNull(calendar.id),
            )
        }

        assertEquals(ErrorCode.CALENDAR_RELATION_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun `DB는 같은 사용자와 캘린더의 중복 관계를 거절한다`() {
        val user = User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자")
        em.persist(user)

        val firstGroup = CalendarGroup.create(user, "첫 번째 그룹", null)
        val secondGroup = CalendarGroup.create(user, "두 번째 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(firstGroup)
        em.persist(secondGroup)
        em.persist(calendar)

        em.persist(Subscribe.create(user, firstGroup, calendar, CalendarRole.SUBSCRIBER, null))
        em.flush()

        val exception = assertFailsWith<PersistenceException> {
            em.persist(Subscribe.create(user, secondGroup, calendar, CalendarRole.SUBSCRIBER, null))
            em.flush()
        }

        val duplicateKey = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<SQLException>()
            .firstOrNull { it.errorCode == 1062 }

        assertTrue(duplicateKey?.message.orEmpty().contains("uk_subscribes_user_calendar"))
    }

    @Test
    fun `구독자는 원본 일정을 조회할 수 있지만 수정할 수 없다`() {
        val owner = User.signUp("owner@mail.com", "{bcrypt}hash", "소유자")
        val subscriber = User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자")
        em.persist(owner)
        em.persist(subscriber)

        val ownerGroup = CalendarGroup.create(owner, "소유자 그룹", null)
        val subscriberGroup = CalendarGroup.create(subscriber, "구독자 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(ownerGroup)
        em.persist(subscriberGroup)
        em.persist(calendar)
        em.persist(Subscribe.create(owner, ownerGroup, calendar, CalendarRole.OWNER, null))

        val start = OffsetDateTime.parse("2026-11-20T10:00:00+09:00")
        val end = start.plusHours(1)
        val event = Event.create(
            calendar = calendar,
            category = null,
            user = owner,
            title = "스터디",
            description = null,
            location = null,
            startAt = start.toInstant(),
            endAt = end.toInstant(),
        )
        em.persist(event)

        val userId = checkNotNull(subscriber.id)
        val groupId = checkNotNull(subscriberGroup.id)
        val calendarId = checkNotNull(calendar.id)
        val eventId = checkNotNull(event.id)
        val from = start.minusHours(1)
        val to = end.plusHours(1)

        assertTrue(eventService.findInRange(userId, from, to).isEmpty())

        service.subscribe(userId, groupId, calendarId)
        em.flush()
        em.clear()

        assertEquals(listOf(eventId), eventService.findInRange(userId, from, to).map { it.id })

        val updateRequest = EventUpdateRequest(
            categoryId = null,
            title = "구독자가 바꾼 제목",
            description = null,
            location = null,
            startAt = start,
            endAt = end,
        )

        val exception = assertFailsWith<BusinessException> {
            eventService.update(userId, eventId, updateRequest)
        }

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
    }

    @Test
    fun `구독을 해지하면 개인 설정이 삭제되고 구독자의 일정 조회에서 빠진다`() {
        val owner = User.signUp("owner@mail.com", "{bcrypt}hash", "소유자")
        val subscriber = User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자")
        em.persist(owner)
        em.persist(subscriber)

        val ownerGroup = CalendarGroup.create(owner, "소유자 그룹", null)
        val subscriberGroup = CalendarGroup.create(subscriber, "구독자 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(ownerGroup)
        em.persist(subscriberGroup)
        em.persist(calendar)
        em.persist(Subscribe.create(owner, ownerGroup, calendar, CalendarRole.OWNER, null))

        val category = EventCategory.create(calendar, "스터디", null)
        em.persist(category)

        val start = OffsetDateTime.parse("2026-11-20T10:00:00+09:00")
        val end = start.plusHours(1)
        val event = Event.create(
            calendar = calendar,
            category = category,
            user = owner,
            title = "스터디 일정",
            description = null,
            location = null,
            startAt = start.toInstant(),
            endAt = end.toInstant(),
        )
        em.persist(event)

        val ownerId = checkNotNull(owner.id)
        val subscriberId = checkNotNull(subscriber.id)
        val eventId = checkNotNull(event.id)
        val calendarId = checkNotNull(calendar.id)
        val subscriptionId = service.subscribe(
            userId = subscriberId,
            groupId = checkNotNull(subscriberGroup.id),
            calendarId = calendarId,
        )
        val subscription = checkNotNull(em.find(Subscribe::class.java, subscriptionId))

        val eventOption = EventOption.create(subscription, event, calendar)
        val categoryOption = CategoryOption.create(subscription, category, calendar)
        em.persist(eventOption)
        em.persist(categoryOption)
        em.flush()

        val eventOptionId = checkNotNull(eventOption.id)
        val categoryOptionId = checkNotNull(categoryOption.id)
        val from = start.minusHours(1)
        val to = end.plusHours(1)

        assertEquals(listOf(eventId), eventService.findInRange(subscriberId, from, to).map { it.id })

        service.cancel(subscriberId, calendarId)
        em.flush()
        em.clear()

        assertNull(em.find(Subscribe::class.java, subscriptionId))
        assertNull(em.find(EventOption::class.java, eventOptionId))
        assertNull(em.find(CategoryOption::class.java, categoryOptionId))
        assertTrue(eventService.findInRange(subscriberId, from, to).isEmpty())
        assertEquals(listOf(eventId), eventService.findInRange(ownerId, from, to).map { it.id })
    }

    @Test
    fun `다른 사용자의 구독은 해지할 수 없다`() {
        val subscriber = User.signUp("subscriber@mail.com", "{bcrypt}hash", "구독자")
        val requester = User.signUp("requester@mail.com", "{bcrypt}hash", "요청자")
        em.persist(subscriber)
        em.persist(requester)

        val group = CalendarGroup.create(subscriber, "구독자 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(group)
        em.persist(calendar)

        val calendarId = checkNotNull(calendar.id)
        service.subscribe(
            userId = checkNotNull(subscriber.id),
            groupId = checkNotNull(group.id),
            calendarId = calendarId,
        )

        val exception = assertFailsWith<BusinessException> {
            service.cancel(
                userId = checkNotNull(requester.id),
                calendarId = calendarId,
            )
        }

        assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `본인의 OWNER 관계는 구독 해지로 삭제할 수 없다`() {
        val owner = User.signUp("owner@mail.com", "{bcrypt}hash", "소유자")
        em.persist(owner)

        val group = CalendarGroup.create(owner, "소유자 그룹", null)
        val calendar = Calendar.create("원본 캘린더", null, null)
        em.persist(group)
        em.persist(calendar)

        val ownerRelation = Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null)
        em.persist(ownerRelation)

        val exception = assertFailsWith<BusinessException> {
            service.cancel(
                userId = checkNotNull(owner.id),
                calendarId = checkNotNull(calendar.id),
            )
        }

        assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.errorCode)
    }
}
