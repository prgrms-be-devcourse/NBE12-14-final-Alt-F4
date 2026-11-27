package com.team4.live_calendar.global.websocket

import org.slf4j.LoggerFactory
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 서비스가 발행한 변경 이벤트를 커밋 뒤에 STOMP 신호로 보낸다
// 롤백되면 보내지 않고, 트랜잭션 밖에서 발행하면 돌지 않는다 (fallbackExecution 기본 false)
// @Transactional을 달지 않는다 (커밋 뒤 리스너에 기본 전파의 @Transactional이 있으면 앱 시작이 실패한다)
@Component
class SyncSignalListener(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 그 캘린더를 구독한 연결 모두에게
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCalendarChanged(event: CalendarChangedEvent) {
        try {
            messagingTemplate.convertAndSend("$CALENDAR_TOPIC_PREFIX${event.calendarId}", event)
        } catch (e: MessagingException) {
            // 신호를 못 보내도 저장은 끝났다. 받는 쪽은 다음 조회 때 최신을 본다
            log.warn("캘린더 신호 전송 실패: calendarId={}, type={}", event.calendarId, event.type, e)
        }
    }

    // 그 사람의 모든 연결에게만 (다른 사람은 받지 않는다)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDisplayChanged(event: DisplayChangedEvent) {
        try {
            messagingTemplate.convertAndSendToUser(
                event.userId.toString(),
                DISPLAY_QUEUE,
                DisplaySignal(SyncType.DISPLAY_CHANGED, event.occurredAt),
            )
        } catch (e: MessagingException) {
            log.warn("표시 신호 전송 실패: userId={}", event.userId, e)
        }
    }

    companion object {
        const val CALENDAR_TOPIC_PREFIX = "/topic/calendars/"

        // 서버가 보내는 주소. 클라이언트는 앞에 /user를 붙여 구독한다 (USER_DISPLAY_QUEUE)
        const val DISPLAY_QUEUE = "/queue/display"
        const val USER_DISPLAY_QUEUE = "/user$DISPLAY_QUEUE"
    }
}
