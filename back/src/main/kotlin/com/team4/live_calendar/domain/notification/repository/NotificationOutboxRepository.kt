package com.team4.live_calendar.domain.notification.repository

import com.team4.live_calendar.domain.notification.entity.NotificationOutbox
import com.team4.live_calendar.domain.notification.entity.NotificationOutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface NotificationOutboxRepository : JpaRepository<NotificationOutbox, Long> {

    @Query("select o from NotificationOutbox o where o.history.eventId = :eventId order by o.id")
    fun findAllByEventId(@Param("eventId") eventId: Long): List<NotificationOutbox>

    @Query("select o.id from NotificationOutbox o where o.status = :status and (o.nextRetryAt is null or o.nextRetryAt <= :now) order by o.id")
    fun findNextPendingIds(status: NotificationOutboxStatus, now: Instant, pageable: Pageable): List<Long>

    @Modifying
    @Query("update NotificationOutbox o set o.status = :processing, o.lockedAt = :now, o.errorMessage = null where o.id = :id and o.status = :pending and (o.nextRetryAt is null or o.nextRetryAt <= :now)")
    fun claim(@Param("id") id: Long, @Param("pending") pending: NotificationOutboxStatus, @Param("processing") processing: NotificationOutboxStatus, @Param("now") now: Instant): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationOutbox o set o.status = :pending, o.lockedAt = null, o.nextRetryAt = :now where o.status = :processing and o.lockedAt < :staleBefore")
    fun recoverStale(@Param("pending") pending: NotificationOutboxStatus, @Param("processing") processing: NotificationOutboxStatus, @Param("now") now: Instant, @Param("staleBefore") staleBefore: Instant): Int
}
