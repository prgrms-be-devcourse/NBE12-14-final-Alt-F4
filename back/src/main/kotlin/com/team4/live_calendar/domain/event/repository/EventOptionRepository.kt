package com.team4.live_calendar.domain.event.repository

import com.team4.live_calendar.domain.event.entity.EventOption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface EventOptionRepository : JpaRepository<EventOption, Long> {

    // 이용 관계(subscribe)별로 숨김 처리한 일정 ID
    @Query("select o.event.id from EventOption o where o.subscribe.id in :subscribeIds and o.isVisible = false")
    fun findHiddenEventIds(subscribeIds: Collection<Long>): List<Long>

    fun deleteAllByEventId(eventId: Long)
}
