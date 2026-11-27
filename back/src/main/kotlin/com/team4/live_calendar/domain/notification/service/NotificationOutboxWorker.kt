package com.team4.live_calendar.domain.notification.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Component
@ConditionalOnProperty(
    prefix = "app.notification.outbox-worker",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class NotificationOutboxWorker(
    private val notificationOutboxService: NotificationOutboxService,
    private val notificationOutboxProcessor: NotificationOutboxProcessor,
) {

    @Scheduled(fixedDelay = 2000)
    fun processPending() {
        notificationOutboxService.recoverStale()
        repeat(100) {
            val id = notificationOutboxService.claimNext() ?: return
            try {
                notificationOutboxProcessor.process(id)
            } catch (exception: Exception) {
                logger.error("Failed to process notification outbox id={}", id, exception)
                notificationOutboxService.markRetry(id, exception.message ?: exception.javaClass.simpleName)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NotificationOutboxWorker::class.java)
    }
}
