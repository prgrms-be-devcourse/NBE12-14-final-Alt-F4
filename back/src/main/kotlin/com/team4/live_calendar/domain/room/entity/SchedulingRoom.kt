package com.team4.live_calendar.domain.room.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.*

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

    // 그날(date)의 조율 시간 범위를 UTC 순간으로
    // 끝이 시작보다 이르거나 같으면 다음 날 그 시각까지 (22:00~02:00, 00:00~00:00 = 24시간)
    fun windowOn(date: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.of(timezone)
        val endDate = if (dailyEndTime.isAfter(dailyStartTime)) date else date.plusDays(1)
        return ZonedDateTime.of(date, dailyStartTime, zone).toInstant() to
                ZonedDateTime.of(endDate, dailyEndTime, zone).toInstant()
    }

    // 조율 기간 전체를 UTC 순간으로: 시작 날짜 0시 ~ (마지막 날 다음 날 0시, 마지막 날 범위의 끝 중 늦은 쪽)
    fun period(): Pair<Instant, Instant> {
        val zone = ZoneId.of(timezone)
        val start = startDate.atStartOfDay(zone).toInstant()
        val end = maxOf(endDate.plusDays(1).atStartOfDay(zone).toInstant(), windowOn(endDate).second)
        return start to end
    }

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
