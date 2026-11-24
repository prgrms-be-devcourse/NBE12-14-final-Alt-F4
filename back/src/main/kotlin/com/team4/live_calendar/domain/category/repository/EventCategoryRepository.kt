package com.team4.live_calendar.domain.category.repository

import com.team4.live_calendar.domain.category.entity.EventCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface EventCategoryRepository : JpaRepository<EventCategory, Long> {

    // 캘린더 삭제: 이 캘린더의 카테고리
    @Modifying
    @Query("delete from EventCategory c where c.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int
}
