package com.team4.live_calendar.global.websocket

import org.junit.jupiter.api.Test
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// STOMP 연결(CONNECT) 인증과 연결 설정
class StompConnectTest : StompTestSupport() {

    @Test
    fun `토큰 없이 연결하면 거절되고 이유는 토큰이 없습니다`() {
        // when
        val reason = connectRejected(token = null)

        // then
        assertEquals("토큰이 없습니다.", reason)
    }

    @Test
    fun `틀린 토큰이나 만료된 토큰으로 연결하면 거절되고 이유는 토큰이 올바르지 않습니다`() {
        // given
        val user = newUser()

        listOf("abc", expiredTokenOf(user)).forEach { token ->
            // when
            val reason = connectRejected(token)

            // then
            assertEquals("토큰이 올바르지 않습니다.", reason)
        }
    }

    @Test
    fun `올바른 토큰으로 연결하면 연결 사용자 이름이 회원 ID다`() {
        // given
        val user = newUser()
        val handler = RecordingHandler()

        // when
        connect(tokenOf(user), handler = handler)

        // then
        assertEquals(user.id.toString(), handler.connectedHeaders?.getFirst("user-name"))
    }

    @Test
    fun `STOMP 프레임으로 토큰 없이 연결해도 거절되고 닫힘 코드는 1002다`() {
        // given
        val raw = RawSession(url)

        // when
        raw.send("STOMP\naccept-version:1.2\nhost:localhost\n\n\u0000")

        // then
        val frame = assertNotNull(raw.received.poll(5, TimeUnit.SECONDS))
        assertTrue(frame.startsWith("ERROR"))
        assertTrue(frame.contains("message:토큰이 없습니다."))
        assertEquals(CloseStatus.PROTOCOL_ERROR.code, raw.closed.get(5, TimeUnit.SECONDS).code)
    }

    @Test
    fun `서버는 10초마다 하트비트를 주고받자고 응답한다`() {
        // given
        val handler = RecordingHandler()

        // when
        connect(tokenOf(newUser()), handler = handler)

        // then
        assertEquals(listOf(10_000L, 10_000L), handler.connectedHeaders?.heartbeat?.toList())
    }

    @Test
    fun `허용한 Origin은 연결되고 다른 Origin은 거절된다`() {
        // given
        val token = tokenOf(newUser())

        // when & then
        connect(token, origin = "http://localhost:3000")
        assertFailsWith<ExecutionException> { connect(token, origin = "http://evil.com") }
    }

    @Test
    fun `8KB를 넘는 메시지를 보내면 ERROR를 받고 연결이 닫힌다`() {
        // given
        val raw = RawSession(url)

        // when
        raw.send("SEND\ndestination:/app/x\n\n${"a".repeat(10_000)}\u0000")

        // then
        val frame = assertNotNull(raw.received.poll(5, TimeUnit.SECONDS))
        assertTrue(frame.startsWith("ERROR"))
        assertTrue(frame.contains("8192"))
        assertNotNull(raw.closed.get(5, TimeUnit.SECONDS))
    }

    // Java STOMP 클라이언트로는 못 만드는 프레임(STOMP 명령, 큰 메시지)을 보내려고 원시 WebSocket을 연다
    private class RawSession(url: String) : TextWebSocketHandler() {
        val received = LinkedBlockingQueue<String>()
        val closed = CompletableFuture<CloseStatus>()
        private val session = StandardWebSocketClient().execute(this, url).get(5, TimeUnit.SECONDS)

        fun send(frame: String) = session.sendMessage(TextMessage(frame))

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            received.add(message.payload)
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            closed.complete(status)
        }
    }
}
