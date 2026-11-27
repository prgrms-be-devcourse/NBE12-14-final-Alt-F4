package com.team4.live_calendar.global.websocket

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.SubscribableChannel
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// 커밋 뒤에만 신호가 나가는지 본다
// 클래스에 @Transactional을 붙이지 않는다 (붙이면 TransactionTemplate이 바깥 테스트 트랜잭션에 합류해 커밋이 일어나지 않는다)
@SpringBootTest
class SyncSignalListenerTest(
    @Autowired private val publisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired @Qualifier("brokerChannel") private val brokerChannel: SubscribableChannel,
    @Autowired private val jsonMapper: JsonMapper,
) {

    // 브로커로 가는 메시지를 기록한다 (커밋한 스레드에서 동기로 기록되어 기다릴 필요가 없다)
    private val sent = CopyOnWriteArrayList<Message<*>>()
    private val recorder = MessageHandler { sent += it }

    @BeforeEach
    fun setUp() {
        brokerChannel.subscribe(recorder)
    }

    @AfterEach
    fun tearDown() {
        brokerChannel.unsubscribe(recorder)
    }

    @Test
    fun `커밋되면 그 캘린더 주소로 신호를 한 번 보낸다`() {
        // given
        val event = CalendarChangedEvent(SyncType.EVENTS_CHANGED, calendarId = CALENDAR_ID, eventId = 12)

        // when
        transactionTemplate.executeWithoutResult { publisher.publishEvent(event) }

        // then
        val messages = sentTo(CALENDAR_DESTINATION)
        assertEquals(1, messages.size)
        val body = bodyOf(messages.single())
        assertEquals("EVENTS_CHANGED", body["type"])
        assertEquals(CALENDAR_ID, (body["calendarId"] as Number).toLong())
        assertEquals(12L, (body["eventId"] as Number).toLong())
    }

    @Test
    fun `롤백되면 신호를 보내지 않는다`() {
        // when
        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(CalendarChangedEvent(SyncType.CALENDAR_UPDATED, calendarId = CALENDAR_ID))
            it.setRollbackOnly()
        }

        // then
        assertEquals(0, sentTo(CALENDAR_DESTINATION).size)
    }

    @Test
    fun `트랜잭션 밖에서 발행하면 신호를 보내지 않는다`() {
        // when
        publisher.publishEvent(CalendarChangedEvent(SyncType.CALENDAR_UPDATED, calendarId = CALENDAR_ID))

        // then
        assertEquals(0, sentTo(CALENDAR_DESTINATION).size)
    }

    @Test
    fun `표시 변경이 커밋되면 그 사람 주소로 신호를 한 번 보내고 회원 ID는 담지 않는다`() {
        // when
        transactionTemplate.executeWithoutResult { publisher.publishEvent(DisplayChangedEvent(USER_ID)) }

        // then
        val messages = sentTo("/user/$USER_ID/queue/display")
        assertEquals(1, messages.size)
        val body = bodyOf(messages.single())
        assertEquals("DISPLAY_CHANGED", body["type"])
        assertFalse(body.containsKey("userId"))
    }

    // 연결이 있으면 연결 전용 주소로 바꾼 복사본도 지나가서, 처음 보낸 주소로만 센다
    private fun sentTo(destination: String): List<Message<*>> =
        sent.filter { SimpMessageHeaderAccessor.getDestination(it.headers) == destination }

    @Suppress("UNCHECKED_CAST")
    private fun bodyOf(message: Message<*>): Map<String, Any?> =
        jsonMapper.readValue(String(message.payload as ByteArray), Map::class.java) as Map<String, Any?>

    companion object {
        // 다른 테스트 데이터와 겹치지 않을 큰 값 (DB를 쓰지 않는다)
        private const val CALENDAR_ID = 9_000_001L
        private const val USER_ID = 9_000_002L
        private const val CALENDAR_DESTINATION = "/topic/calendars/$CALENDAR_ID"
    }
}
