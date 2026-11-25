package com.team4.live_calendar.global.websocket

import com.team4.live_calendar.global.config.CorsProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration

// 실시간 동기화용 WebSocket(STOMP) 설정
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    private val corsProperties: CorsProperties,
    // 하트비트 시계: Spring WebSocket이 이미 만들어 둔 스케줄러를 받아 쓴다 (@Lazy로 순환 참조를 피한다)
    @Lazy @Qualifier("messageBrokerTaskScheduler") private val messageBrokerTaskScheduler: TaskScheduler,
) : WebSocketMessageBrokerConfigurer {

    // 브라우저가 처음 연결하는 입구 (허용 Origin은 HTTP API의 CORS 설정과 같다)
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
    }

    // 주소 규칙: 우편함(브로커)과 서버 코드로 가는 주소
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
            .setTaskScheduler(messageBrokerTaskScheduler)  // 하트비트 10초/10초 (스케줄러를 주면 기본값)
        registry.setApplicationDestinationPrefixes("/app")
    }

    // 클라이언트에서 들어오는 메시지 통로에 검문소를 단다
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthChannelInterceptor)
    }

    // 브라우저는 CONNECT·SUBSCRIBE만 보내므로 8KB면 충분하다 (기본값 64KB, 채팅이 생기면 조정)
    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.setMessageSizeLimit(8 * 1024)
    }
}