package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SubscribeRepository : JpaRepository<Subscribe, Long> {

    fun findByUserIdAndCalendarId(userId: Long, calendarId: Long): Subscribe?

    fun findAllByUserId(userId: Long): List<Subscribe>

    // 내 이용 관계와 캘린더를 한 번에 (그룹별 캘린더 조회에서 N+1을 막는다)
    @Query("select s from Subscribe s join fetch s.calendar where s.user.id = :userId order by s.id")
    fun findAllWithCalendarByUserId(userId: Long): List<Subscribe>

    // 이 그룹에 둔 이용 관계가 있는지 (그룹 주인만 그룹을 쓰므로 그룹 ID로만 본다)
    fun existsByGroupId(groupId: Long): Boolean

    fun findByUserIdAndCalendarIdAndRole(userId: Long, calendarId: Long, role: CalendarRole): Subscribe?
}
