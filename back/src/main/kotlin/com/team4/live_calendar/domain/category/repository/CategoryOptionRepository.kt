package com.team4.live_calendar.domain.category.repository

import com.team4.live_calendar.domain.category.entity.CategoryOption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CategoryOptionRepository : JpaRepository<CategoryOption, Long> {
    fun deleteAllBySubscribeId(subscriptionId: Long)

    fun findBySubscribeIdAndCategoryId(subscribeId: Long, categoryId: Long): CategoryOption?

    // 카테고리 숨김 복원: 이 이용 관계의 줄만 (다른 사람의 숨김은 그대로). 줄이 없으면 0행
    @Modifying
    @Query("delete from CategoryOption o where o.subscribe.id = :subscribeId and o.category.id = :categoryId")
    fun deleteOfSubscribe(subscribeId: Long, categoryId: Long): Int

    // 이용 관계별로 숨긴 카테고리 ID
    @Query("select o.category.id from CategoryOption o where o.subscribe.id in :subscribeIds and o.isVisible = false")
    fun findHiddenCategoryIds(subscribeIds: Collection<Long>): List<Long>

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
