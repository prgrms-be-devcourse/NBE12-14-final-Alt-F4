package com.team4.live_calendar.global.websocket

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.service.CalendarService
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 서비스가 커밋하면 실제 STOMP 연결로 신호가 오는지 본다 (테스트 트랜잭션 없이 불러서 커밋된다)
class CalendarSyncStompTest : StompTestSupport() {

    @Autowired private lateinit var eventService: EventService
    @Autowired private lateinit var calendarService: CalendarService

    @Test
    fun `일정을 만들면 그 캘린더 구독자와 만든 사람의 연결이 받고 다른 캘린더 구독자는 받지 않는다`() {
        // given: 편집자(OWNER)와 구독자가 X를, 다른 사람이 Y를 구독
        val editor = newUser()
        val subscriber = newUser()
        val other = newUser()
        val calendarX = newCalendar().also {
            join(editor, it, CalendarRole.OWNER)
            join(subscriber, it, CalendarRole.SUBSCRIBER)
        }
        val calendarY = newCalendar().also { join(other, it, CalendarRole.OWNER) }
        val destinationX = "/topic/calendars/${calendarX.id}"
        val destinationY = "/topic/calendars/${calendarY.id}"

        val editorReceived = connect(tokenOf(editor)).subscribeTo(destinationX)
        val subscriberReceived = connect(tokenOf(subscriber)).subscribeTo(destinationX)
        val otherReceived = connect(tokenOf(other)).subscribeTo(destinationY)
        assertReceives(destinationX, editorReceived)
        assertReceives(destinationX, subscriberReceived)
        assertReceives(destinationY, otherReceived)

        // when
        val created = eventService.create(
            editor.id!!,
            EventCreateRequest(
                calendarId = calendarX.id!!,
                categoryId = null,
                title = "팀 회의",
                description = null,
                location = null,
                startAt = OffsetDateTime.parse("2026-11-18T14:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-18T15:00:00+09:00"),
            ),
        )

        // then
        val signal = awaitSignal(subscriberReceived, SyncType.EVENTS_CHANGED)
        assertEquals(calendarX.id, (signal["calendarId"] as Number).toLong())
        assertEquals(created.id, (signal["eventId"] as Number).toLong())
        // Instant는 REST 응답과 같은 ISO 문자열이다
        assertEquals(Instant.parse("2026-11-18T05:00:00Z"), Instant.parse(signal["from"] as String))
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), Instant.parse(signal["to"] as String))
        assertNotNull(signal["occurredAt"])
        awaitSignal(editorReceived, SyncType.EVENTS_CHANGED)
        // 한 요청에 한 번만 온다
        assertNoRealSignal(subscriberReceived)
        assertNoRealSignal(editorReceived)
        assertNoRealSignal(otherReceived)
    }

    @Test
    fun `이벤트를 발행한 뒤 실패한 요청은 롤백되어 신호를 보내지 않는다`() {
        // given: 반복 일정을 만들고 그 신호까지 받아 둔다
        val owner = newUser()
        val subscriber = newUser()
        val calendar = newCalendar().also {
            join(owner, it, CalendarRole.OWNER)
            join(subscriber, it, CalendarRole.SUBSCRIBER)
        }
        val destination = "/topic/calendars/${calendar.id}"
        val received = connect(tokenOf(subscriber)).subscribeTo(destination)
        assertReceives(destination, received)
        val weekly = eventService.create(
            owner.id!!,
            EventCreateRequest(
                calendarId = calendar.id!!,
                categoryId = null,
                title = "주간 회의",
                description = null,
                location = null,
                startAt = OffsetDateTime.parse("2026-11-16T08:00:00+09:00"),
                endAt = OffsetDateTime.parse("2026-11-16T09:00:00+09:00"),
                timezone = "Asia/Seoul",
                rrule = "FREQ=WEEKLY;BYDAY=MO",
            ),
        )
        awaitSignal(received, SyncType.EVENTS_CHANGED)

        // when: 범위 없이 고치면 발행한 뒤 범위 확인에서 실패한다
        assertThrows<BusinessException> {
            eventService.update(
                owner.id!!,
                weekly.id,
                EventUpdateRequest(
                    categoryId = null,
                    title = "제목 변경",
                    description = null,
                    location = null,
                    startAt = OffsetDateTime.parse("2026-11-16T10:00:00+09:00"),
                    endAt = OffsetDateTime.parse("2026-11-16T11:00:00+09:00"),
                    timezone = null,
                    rrule = "FREQ=WEEKLY;BYDAY=MO",
                    version = weekly.version,
                ),
            )
        }

        // then
        assertNoRealSignal(received)
    }

    @Test
    fun `캘린더를 지운 뒤에도 구독자가 삭제 신호를 받고 빈 칸은 null로 온다`() {
        // given
        val owner = newUser()
        val subscriber = newUser()
        val calendar = newCalendar().also {
            join(owner, it, CalendarRole.OWNER)
            join(subscriber, it, CalendarRole.SUBSCRIBER)
        }
        val destination = "/topic/calendars/${calendar.id}"
        val received = connect(tokenOf(subscriber)).subscribeTo(destination)
        assertReceives(destination, received)

        // when
        calendarService.delete(owner.id!!, calendar.id!!)

        // then: 브로커 구독은 DB와 상관없이 남아 있어 신호가 간다
        val signal = awaitSignal(received, SyncType.CALENDAR_DELETED)
        assertEquals(calendar.id, (signal["calendarId"] as Number).toLong())
        listOf("eventId", "from", "to").forEach { key ->
            assertTrue(signal.containsKey(key), key)
            assertNull(signal[key], key)
        }
        assertNoRealSignal(received)
    }
}
