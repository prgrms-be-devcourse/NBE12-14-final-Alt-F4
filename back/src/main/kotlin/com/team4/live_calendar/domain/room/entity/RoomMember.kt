package com.team4.live_calendar.domain.room.entity

import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 조율방 참여 관계
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "room_members")
class RoomMember protected constructor(
    room: SchedulingRoom,
    user: User,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    var room: SchedulingRoom = room
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @CreatedDate
    @Column(name = "joined_at", nullable = false, updatable = false)
    lateinit var joinedAt: Instant
        protected set

    companion object {
        fun create(room: SchedulingRoom, user: User): RoomMember =
            RoomMember(room = room, user = user)
    }
}
