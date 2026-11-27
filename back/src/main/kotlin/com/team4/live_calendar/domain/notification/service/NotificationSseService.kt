package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.notification.dto.NotificationResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class NotificationSseService {
    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun connect(userId: Long): SseEmitter {
        val emitter = SseEmitter(30 * 60 * 1000L)
        emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onCompletion { remove(userId, emitter) }
        emitter.onTimeout { emitter.complete() }
        emitter.onError { remove(userId, emitter) }
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"))
        } catch (exception: Exception) {
            remove(userId, emitter)
            emitter.completeWithError(exception)
        }
        return emitter
    }

    @TransactionalEventListener
    fun onNotificationCreated(event: NotificationCreatedEvent) {
        emitters[event.userId]?.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("notification")
                        .id(event.notification.id.toString())
                        .data(event.notification)
                )
            } catch (exception: Exception) {
                remove(event.userId, emitter)
                emitter.completeWithError(exception)
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 30000)
    fun sendHeartbeats() {
        emitters.entries.forEach { entry ->
            val userId = entry.key
            val userEmitters = entry.value
            userEmitters.forEach { emitter ->
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"))
                } catch (exception: Exception) {
                    remove(userId, emitter)
                    emitter.completeWithError(exception)
                }
            }
        }
    }

    private fun remove(userId: Long, emitter: SseEmitter) {
        emitters[userId]?.let { userEmitters ->
            userEmitters.remove(emitter)
            if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters)
        }
    }
}
