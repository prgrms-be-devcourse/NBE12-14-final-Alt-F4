package com.team4.live_calendar.domain.room.entity

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

// 조율방 채팅 메시지
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "room_messages")
class RoomMessage protected constructor(
    member: RoomMember,
    content: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    var member: RoomMember = member
        protected set

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String = content
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(member: RoomMember, content: String): RoomMessage =
            RoomMessage(member = member, content = content)
    }
}
