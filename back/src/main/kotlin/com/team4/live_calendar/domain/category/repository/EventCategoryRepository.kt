package com.team4.live_calendar.domain.category.repository

import com.team4.live_calendar.domain.category.entity.EventCategory
import org.springframework.data.jpa.repository.JpaRepository

interface EventCategoryRepository : JpaRepository<EventCategory, Long>
