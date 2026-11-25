package com.team4.live_calendar.global.websocket

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.security.JwtProperties
import com.team4.live_calendar.global.security.JwtTokenProvider
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.SimpleMessageConverter
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.fail

// WebSocket 테스트 공통: 진짜 서버를 띄우고 STOMP로 연결한다
// 서버 스레드는 테스트 트랜잭션 밖이라 @Transactional 없이 커밋해서 넣고, 끝나면 FK 순서대로 지운다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class StompTestSupport {

    @LocalServerPort
    private var port: Int = 0

    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var jwtEncoder: JwtEncoder
    @Autowired private lateinit var jwtProperties: JwtProperties
    @Autowired private lateinit var messagingTemplate: SimpMessagingTemplate
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var calendarRepository: CalendarRepository
    @Autowired private lateinit var calendarGroupRepository: CalendarGroupRepository
    @Autowired private lateinit var subscribeRepository: SubscribeRepository

    // 서버가 보내는 JSON을 바이트 그대로 받는다 (StringMessageConverter는 text/plain만 받아 JSON을 조용히 버린다)
    private val stompClient = WebSocketStompClient(StandardWebSocketClient()).apply {
        messageConverter = SimpleMessageConverter()
    }

    private val sessions = mutableListOf<StompSession>()
    private val users = mutableListOf<User>()
    private val calendars = mutableListOf<Calendar>()
    private val groups = mutableListOf<CalendarGroup>()
    private val subscribes = mutableListOf<Subscribe>()

    protected val url: String
        get() = "ws://localhost:$port/ws"

    // 하나가 실패해도 나머지를 지운다 (남은 데이터는 다른 테스트를 깨뜨린다)
    @AfterEach
    fun cleanUp() {
        sessions.forEach { runCatching { it.disconnect() } }
        runCatching { subscribeRepository.deleteAll(subscribes) }
        runCatching { calendarGroupRepository.deleteAll(groups) }
        runCatching { calendarRepository.deleteAll(calendars) }
        runCatching { userRepository.deleteAll(users) }
    }

    // ===== 데이터 =====

    protected fun newUser(): User =
        userRepository.save(User.signUp("ws-${UUID.randomUUID()}@mail.com", "{bcrypt}hash", "ws"))
            .also { users += it }

    protected fun newCalendar(): Calendar =
        calendarRepository.save(Calendar.create("ws 테스트", null, null)).also { calendars += it }

    protected fun join(user: User, calendar: Calendar, role: CalendarRole) {
        val group = calendarGroupRepository.save(CalendarGroup.create(user, "그룹", 0)).also { groups += it }
        subscribes += subscribeRepository.save(Subscribe.create(user, group, calendar, role, null))
    }

    // ===== 토큰 =====

    protected fun tokenOf(user: User): String = jwtTokenProvider.createAccessToken(user.id!!)

    // JWT 검증이 시계 오차 60초를 허용해서 5분 전에 만료된 토큰을 만든다
    protected fun expiredTokenOf(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .subject(user.id.toString())
            .issuedAt(now.minus(Duration.ofHours(1)))
            .expiresAt(now.minus(Duration.ofMinutes(5)))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    // ===== 연결 =====

    protected fun connect(
        token: String?,
        origin: String? = null,
        handler: RecordingHandler = RecordingHandler(),
    ): StompSession {
        val connectHeaders = StompHeaders().apply { token?.let { add("Authorization", "Bearer $it") } }
        val handshakeHeaders = WebSocketHttpHeaders().apply { origin?.let { add("Origin", it) } }
        return stompClient.connectAsync(url, handshakeHeaders, connectHeaders, handler)
            .get(5, TimeUnit.SECONDS)
            .also { sessions += it }
    }

    // 연결이 거절되는지 확인하고, 서버가 ERROR 프레임에 담은 이유를 돌려준다
    protected fun connectRejected(token: String?): String {
        val handler = RecordingHandler()
        assertFailsWith<ExecutionException> { connect(token, handler = handler) }
        return handler.errorMessages.poll(5, TimeUnit.SECONDS) ?: fail("ERROR 프레임이 오지 않았다")
    }

    protected fun StompSession.subscribeTo(destination: String): BlockingQueue<String> {
        val received = LinkedBlockingQueue<String>()
        subscribe(destination, object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                received.add(String(payload as ByteArray))
            }
        })
        return received
    }

    // ===== 신호 =====

    // 서버 쪽에서 우편함으로 바로 보낸다 (클라이언트 검문소를 거치지 않는다)
    protected fun sendSignal(destination: String) =
        messagingTemplate.convertAndSend(destination, SIGNAL)

    // 구독 등록은 다른 스레드에서 비동기로 끝나서, 받을 때까지 신호를 다시 보낸다
    protected fun assertReceives(destination: String, received: BlockingQueue<String>) {
        await().atMost(Duration.ofSeconds(5)).until {
            sendSignal(destination)
            received.poll(100, TimeUnit.MILLISECONDS) != null
        }
    }

    protected fun assertNothingReceived(received: BlockingQueue<String>) =
        assertNull(received.poll(500, TimeUnit.MILLISECONDS))

    companion object {
        // Map을 바로 넘기면 convertAndSend(payload, headers)와 헷갈려서 Any로 둔다
        private val SIGNAL: Any = mapOf("type" to "TEST")
    }

    // 연결 응답과 서버의 ERROR 프레임을 기록한다
    protected class RecordingHandler : StompSessionHandlerAdapter() {
        val errorMessages = LinkedBlockingQueue<String>()
        var connectedHeaders: StompHeaders? = null

        override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
            this.connectedHeaders = connectedHeaders
        }

        override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java

        // 구독에 속하지 않은 프레임 = ERROR (거절 이유는 본문이 아니라 message 헤더에 있다)
        override fun handleFrame(headers: StompHeaders, payload: Any?) {
            errorMessages.add(headers.getFirst("message").orEmpty())
        }
    }
}
