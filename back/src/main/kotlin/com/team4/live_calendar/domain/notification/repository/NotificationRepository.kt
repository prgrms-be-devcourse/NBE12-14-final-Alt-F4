
package com.team4.live_calendar.domain.notification.repository

import com.team4.live_calendar.domain.notification.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {

    // 캘린더 삭제: 알림은 남기고 캘린더 칸만 비운다
    @Modifying
    @Query("update Notification n set n.calendar = null where n.calendar.id = :calendarId")
    fun clearCalendar(calendarId: Long): Int

    // 사용자별 알림 목록 조회
    fun findAllByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<Notification>

    // 사용자별 읽지 않은 알림 개수 조회
    fun countByUserIdAndIsReadFalse(userId: Long): Long

    // 일정 변경 이력별 알림 중복 생성 방지 확인
    fun existsByUserIdAndEventHistoryId(
        userId: Long,
        eventHistoryId: Long,
    ): Boolean

}
