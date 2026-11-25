package com.team4.live_calendar.domain.room.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// 일정 조율방. 날짜와 시간대는 timezone 기준의 현지 날짜/시각
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "scheduling_rooms",
    // 공통 캘린더 하나는 방 하나에만 쓴다
    uniqueConstraints = [UniqueConstraint(name = "uk_scheduling_rooms_calendar", columnNames = ["calendar_id"])],
)
class SchedulingRoom protected constructor(
    owner: User,
    calendar: Calendar,
    name: String,
    startDate: LocalDate,
    endDate: LocalDate,
    dailyStartTime: LocalTime,
    dailyEndTime: LocalTime,
    timezone: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User = owner
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    var calendar: Calendar = calendar
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate = startDate
        protected set

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate = endDate
        protected set

    @Column(name = "daily_start_time", nullable = false)
    var dailyStartTime: LocalTime = dailyStartTime
        protected set

    @Column(name = "daily_end_time", nullable = false)
    var dailyEndTime: LocalTime = dailyEndTime
        protected set

    @Column(name = "timezone", nullable = false, length = 100)
    var timezone: String = timezone
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    // 비어 있으면 사용 중인 방. 삭제는 이 값만 채운다 (소프트 삭제, PR7)
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    // owner는 LAZY 프록시지만 id만 읽으면 DB를 다시 조회하지 않는다
    fun isOwner(userId: Long): Boolean = owner.id == userId

    companion object {
        fun create(
            owner: User,
            calendar: Calendar,
            name: String,
            startDate: LocalDate,
            endDate: LocalDate,
            dailyStartTime: LocalTime,
            dailyEndTime: LocalTime,
            timezone: String,
        ): SchedulingRoom =
            SchedulingRoom(
                owner = owner,
                calendar = calendar,
                name = name.trim(),
                startDate = startDate,
                endDate = endDate,
                dailyStartTime = dailyStartTime,
                dailyEndTime = dailyEndTime,
                timezone = timezone,
            )
    }
}
