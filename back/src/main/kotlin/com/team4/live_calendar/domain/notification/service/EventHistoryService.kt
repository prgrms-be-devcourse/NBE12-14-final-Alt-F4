
package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.EventHistoryActionType
import com.team4.live_calendar.domain.notification.entity.EventHistoryTargetType
import com.team4.live_calendar.domain.notification.repository.EventHistoryRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional(readOnly = true)
class EventHistoryService(
    private val eventHistoryRepository: EventHistoryRepository,
    private val notificationOutboxService: NotificationOutboxService,
    private val userRepository: UserRepository,
) {

    /**
     * 변경 이력 생성 및 Outbox 저장
     */
    @Transactional
    fun save(
        event: Event,
        actorId: Long,
        actionType: EventHistoryActionType,
        targetType: EventHistoryTargetType = EventHistoryTargetType.EVENT,
        recurrenceId: Instant? = null,
        beforeData: Map<String, Any?>? = null,
        afterData: Map<String, Any?>? = null,
    ): EventHistory {
        val history = eventHistoryRepository.save(
            EventHistory.create(
                calendar = event.calendar,
                eventId = event.id!!,
                actor = userRepository.getReferenceById(actorId),
                targetType = targetType,
                recurrenceId = recurrenceId,
                actionType = actionType,
                eventTitle = event.title,
                beforeData = beforeData,
                afterData = afterData,
            )
        )

        notificationOutboxService.save(history)

        return history
    }

    /**
     * 일정 생성 이력 기록
     */
    @Transactional
    fun recordCreated(
        event: Event,
        actorId: Long,
    ): EventHistory =
        save(
            event = event,
            actorId = actorId,
            actionType = EventHistoryActionType.CREATE,
            afterData = snapshot(event),
        )

    /**
     * 일정 수정 이력 기록
     */
    @Transactional
    fun recordUpdated(
        event: Event,
        actorId: Long,
        beforeData: Map<String, Any?>,
        recurrenceId: Instant? = null,
    ): EventHistory =
        save(
            event = event,
            actorId = actorId,
            actionType = EventHistoryActionType.UPDATE,
            recurrenceId = recurrenceId,
            beforeData = beforeData,
            afterData = snapshot(event),
        )

    /**
     * 일정 삭제 이력 기록
     */
    @Transactional
    fun recordDeleted(
        event: Event,
        actorId: Long,
        beforeData: Map<String, Any?>,
    ): EventHistory =
        save(
            event = event,
            actorId = actorId,
            actionType = EventHistoryActionType.DELETE,
            beforeData = beforeData,
        )

    /**
     * 반복 일정의 특정 회차 수정 이력 기록
     */
    @Transactional
    fun recordOccurrenceUpdated(
        event: Event,
        actorId: Long,
        recurrenceId: Instant,
        beforeData: Map<String, Any?>,
        exception: EventException,
    ): EventHistory =
        save(
            event = event,
            actorId = actorId,
            targetType = EventHistoryTargetType.EVENT_EXCEPTION,
            actionType = EventHistoryActionType.UPDATE,
            recurrenceId = recurrenceId,
            beforeData = beforeData,
            afterData = snapshotOccurrence(exception),
        )

    /**
     * 반복 일정의 특정 회차 삭제 이력 기록
     */
    @Transactional
    fun recordOccurrenceDeleted(
        event: Event,
        actorId: Long,
        recurrenceId: Instant,
        beforeData: Map<String, Any?>,
    ): EventHistory =
        save(
            event = event,
            actorId = actorId,
            targetType = EventHistoryTargetType.EVENT_EXCEPTION,
            actionType = EventHistoryActionType.DELETE,
            recurrenceId = recurrenceId,
            beforeData = beforeData,
            afterData = mapOf(
                "type" to "DELETED",
            ),
        )

    /**
     * 일정 변경 전 스냅샷 생성
     */
    fun captureSnapshot(event: Event): Map<String, Any?> =
        snapshot(event)

    /**
     * 반복 일정의 특정 회차 변경 전 스냅샷 생성
     */
    fun captureOccurrenceSnapshot(
        event: Event,
        recurrenceId: Instant,
        exception: EventException?,
    ): Map<String, Any?> {
        if (exception != null) {
            return snapshotOccurrence(exception)
        }

        val duration = Duration.between(event.startAt, event.endAt)

        return mapOf(
            "title" to event.title,
            "description" to event.description,
            "location" to event.location,
            "startAt" to recurrenceId.toString(),
            "endAt" to recurrenceId.plus(duration).toString(),
            "type" to "ORIGINAL",
        )
    }

    /**
     * 일정 스냅샷 생성
     */
    private fun snapshot(event: Event): Map<String, Any?> =
        mapOf(
            "title" to event.title,
            "description" to event.description,
            "location" to event.location,
            "startAt" to event.startAt.toString(),
            "endAt" to event.endAt.toString(),
            "isAllDay" to event.isAllDay,
            "timezone" to event.timezone,
            "rrule" to event.rrule,
            "recurUntil" to event.recurUntil?.toString(),
            "categoryId" to event.category?.id,
        )

    /**
     * 반복 일정의 특정 회차 스냅샷 생성
     */
    private fun snapshotOccurrence(
        exception: EventException,
    ): Map<String, Any?> =
        mapOf(
            "title" to exception.title,
            "description" to exception.description,
            "location" to exception.location,
            "startAt" to exception.startAt?.toString(),
            "endAt" to exception.endAt?.toString(),
            "type" to exception.type.name,
        )
}
