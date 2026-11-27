package com.team4.live_calendar.domain.room.entity

import com.team4.live_calendar.domain.event.entity.Event
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 조율할 때 참고하라고 방에 공유한 내 일정
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "room_shared_events",
    // 같은 참여자가 같은 일정을 두 번 공유하지 않는다
    uniqueConstraints = [UniqueConstraint(
        name = "uk_room_shared_events_member_event", columnNames = ["member_id", "event_id"]
    )],
)
class RoomSharedEvent protected constructor(
    member: RoomMember,
    event: Event,
    displayMode: SharedEventDisplayMode,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    var member: RoomMember = member
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    var event: Event = event
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "display_mode", nullable = false, length = 30)
    var displayMode: SharedEventDisplayMode = displayMode
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    fun changeDisplayMode(displayMode: SharedEventDisplayMode) {
        this.displayMode = displayMode
    }

    companion object {
        fun create(
            member: RoomMember,
            event: Event,
            displayMode: SharedEventDisplayMode = SharedEventDisplayMode.TIME_ONLY,
        ): RoomSharedEvent =
            RoomSharedEvent(member = member, event = event, displayMode = displayMode)
    }
}
