package com.team4.live_calendar.global.websocket

import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

// 클라이언트가 보낸 STOMP 메시지를 서버가 처리하기 전에 먼저 검사하는 검문소
@Component
class StompAuthChannelInterceptor(
    private val jwtDecoder: JwtDecoder,
    private val subscribeRepository: SubscribeRepository,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.messageType) {
            // 연결 요청: 토큰을 검사하고, 통과하면 이 연결에 회원 정보를 붙인다
            SimpMessageType.CONNECT -> accessor.user = authenticate(accessor)
            // 구독 요청: 권한이 없으면 이 메시지만 버린다 (예외를 던지면 연결 전체가 끊긴다)
            SimpMessageType.SUBSCRIBE -> if (!canSubscribe(accessor)) return null
            // 보내기(SEND): 서버 코드 주소로만 보낼 수 있다 (우편함에 직접 뿌리는 가짜 신호를 막는다)
            SimpMessageType.MESSAGE -> if (!canSend(accessor)) return null
            else -> {}
        }
        return message
    }

    // HTTP API와 같은 JwtDecoder로 검사한다 (서명, 만료, 발급자)
    private fun authenticate(accessor: StompHeaderAccessor): Authentication {
        val header = accessor.getFirstNativeHeader("Authorization")
        if (header == null || !header.startsWith("Bearer ")) {
            throw MessagingException("토큰이 없습니다.")
        }

        val jwt = try {
            jwtDecoder.decode(header.removePrefix("Bearer "))
        } catch (e: JwtException) {
            throw MessagingException("토큰이 올바르지 않습니다.")
        }

        // 회원 ID가 없는 토큰은 우리가 발급한 토큰이 아니다
        val userId = jwt.subject ?: throw MessagingException("토큰이 올바르지 않습니다.")

        // HTTP와 똑같이 Authentication.name = 회원 ID
        return UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    // 내가 이용하는 캘린더의 주소만 구독할 수 있다
    private fun canSubscribe(accessor: StompHeaderAccessor): Boolean {
        val userId = accessor.user?.name?.toLongOrNull() ?: return false
        val destination = accessor.destination ?: return false
        val match = CALENDAR_TOPIC.matchEntire(destination) ?: return false
        val calendarId = match.groupValues[1].toLongOrNull() ?: return false
        return subscribeRepository.existsByUserIdAndCalendarId(userId, calendarId)
    }

    // 클라이언트는 서버 코드 주소(/app/...)로만 보낼 수 있다
    private fun canSend(accessor: StompHeaderAccessor): Boolean =
        accessor.destination?.startsWith("/app/") == true

    companion object {
        // 와일드카드(*, **)가 섞인 주소를 막으려고 숫자 ID 하나만 정확히 허용한다
        private val CALENDAR_TOPIC = Regex("/topic/calendars/(\\d+)")
    }
}