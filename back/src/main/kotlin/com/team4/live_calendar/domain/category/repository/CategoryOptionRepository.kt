package com.team4.live_calendar.domain.category.repository

import com.team4.live_calendar.domain.category.entity.CategoryOption
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryOptionRepository : JpaRepository<CategoryOption, Long> {
    fun deleteAllBySubscribeId(subscriptionId: Long)
}
