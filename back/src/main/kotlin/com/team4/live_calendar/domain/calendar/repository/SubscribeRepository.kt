package com.team4.live_calendar.domain.calendar.repository

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface SubscribeRepository : JpaRepository<Subscribe, Long> {

    fun findByUserIdAndCalendarId(userId: Long, calendarId: Long): Subscribe?

    // 표시 설정 바꾸기: 같은 사람의 동시 요청을 차례로 처리한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscribe s where s.user.id = :userId and s.calendar.id = :calendarId")
    fun findForUpdate(userId: Long, calendarId: Long): Subscribe?

    fun findAllByUserId(userId: Long): List<Subscribe>

    // 내 이용 관계와 캘린더를 한 번에 (그룹별 캘린더 조회에서 N+1을 막는다)
    @Query("select s from Subscribe s join fetch s.calendar where s.user.id = :userId order by s.id")
    fun findAllWithCalendarByUserId(userId: Long): List<Subscribe>

    // 이 그룹에 둔 이용 관계가 있는지 (그룹 주인만 그룹을 쓰므로 그룹 ID로만 본다)
    fun existsByGroupId(groupId: Long): Boolean

    // 이 회원이 이 캘린더를 이용하는지 (WebSocket 구독 권한 검사)
    fun existsByUserIdAndCalendarId(userId: Long, calendarId: Long): Boolean

    fun findByUserIdAndCalendarIdAndRole(userId: Long, calendarId: Long, role: CalendarRole): Subscribe?

    fun countByUserIdAndCalendarIdInAndRole(userId: Long, calendarIds: Collection<Long>, role: CalendarRole): Long

    // 캘린더 삭제: 이 캘린더의 이용 관계 전부 (다른 사람 것 포함)
    @Modifying
    @Query("delete from Subscribe s where s.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int

    // 캘린더 알림 수신 대상자 조회 (알림 비활성화 및 변경자 본인 제외)
    @Query("select s from Subscribe s join fetch s.user where s.calendar.id = :calendarId and s.notificationEnabled = true and s.user.id <> :actorId")
    fun findAllNotificationEnabledByCalendarId(calendarId: Long, actorId: Long): List<Subscribe>
}
