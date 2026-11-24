package com.team4.live_calendar.domain.category.repository

import com.team4.live_calendar.domain.category.entity.CategoryOption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CategoryOptionRepository : JpaRepository<CategoryOption, Long> {
    fun deleteAllBySubscribeId(subscriptionId: Long)

    // 캘린더 삭제: 이 캘린더 카테고리의 사람별 설정
    // 설정의 카테고리·이용 관계·캘린더가 같은 캘린더라는 가정에 기대어 calendar_id로만 지운다 (스키마가 보장하지는 않음)
    @Modifying
    @Query("delete from CategoryOption o where o.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int

    // 카테고리 삭제: 이 카테고리의 사람별 설정 (모든 사람 것)
    @Modifying
    @Query("delete from CategoryOption o where o.category.id = :categoryId")
    fun deleteAllOfCategory(categoryId: Long): Int
}
