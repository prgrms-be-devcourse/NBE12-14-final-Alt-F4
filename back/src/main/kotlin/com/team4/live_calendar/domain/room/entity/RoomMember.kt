package com.team4.live_calendar.domain.room.entity

import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.*
import java.time.Instant

// 조율방 참여 관계. 나가도 행을 지우지 않는다 (공유 일정·채팅이 이 행을 참조)
@Entity
@Table(
    name = "room_members",
    uniqueConstraints = [UniqueConstraint(name = "uk_room_members_room_user", columnNames = ["room_id", "user_id"])],
)
class RoomMember protected constructor(
    room: SchedulingRoom,
    user: User,
    joinedAt: Instant,
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

    // 다시 참여하면 바뀌므로 @CreatedDate 대신 직접 넣는다
    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant = joinedAt
        protected set

    // 비어 있으면 현재 참여자
    @Column(name = "left_at")
    var leftAt: Instant? = null
        protected set

    fun isActive(): Boolean = leftAt == null

    // 나갔던 회원이 다시 참여하면 같은 행을 다시 쓴다
    fun rejoin(now: Instant) {
        leftAt = null
        joinedAt = now
    }

    companion object {
        fun create(room: SchedulingRoom, user: User, joinedAt: Instant = Instant.now()): RoomMember =
            RoomMember(room = room, user = user, joinedAt = joinedAt)
    }

}
