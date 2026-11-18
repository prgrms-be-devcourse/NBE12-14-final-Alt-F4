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

// 조율방 초대 링크 (토큰 원문은 저장하지 않고 SHA-256 해시만 저장)
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "room_invite_links")
class RoomInviteLink protected constructor(
    room: SchedulingRoom,
    user: User,
    tokenHash: String,
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

    @Column(name = "token_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    var tokenHash: String = tokenHash
        protected set

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(room: SchedulingRoom, user: User, tokenHash: String): RoomInviteLink =
            RoomInviteLink(room = room, user = user, tokenHash = tokenHash)
    }
}
