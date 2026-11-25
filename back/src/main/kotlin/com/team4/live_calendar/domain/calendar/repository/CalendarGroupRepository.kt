package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CalendarGroupRepository : JpaRepository<CalendarGroup, Long> {

    // 내 그룹 목록. 순서가 없는(null) 그룹은 맨 뒤, 순서가 같으면 먼저 만든 그룹부터
    @Query("select g from CalendarGroup g where g.user.id = :userId order by g.displayOrder asc nulls last, g.id asc")
    fun findAllByUserIdOrdered(userId: Long): List<CalendarGroup>

    // 내 그룹 하나 (남의 그룹이면 null)
    fun findByIdAndUserId(id: Long, userId: Long): CalendarGroup?

    // 내 그룹 중 가장 큰 순서 값 (그룹이 없으면 null)
    @Query("select max(g.displayOrder) from CalendarGroup g where g.user.id = :userId")
    fun findMaxDisplayOrder(userId: Long): Int?
}
