package com.team4.live_calendar.domain.room.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.room.entity.RoomInviteLink
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// 조율방 UNIQUE 4개(V3 마이그레이션)가 DB에 실제로 있는지, 같은 값을 두 번 저장해 확인한다
// ddl-auto: validate는 컬럼만 보고 UNIQUE는 확인하지 않는다
@DataJpaTest
@Import(JpaConfig::class)
class RoomUniqueConstraintTest(
    @Autowired private val schedulingRoomRepository: SchedulingRoomRepository,
    @Autowired private val roomMemberRepository: RoomMemberRepository,
    @Autowired private val roomSharedEventRepository: RoomSharedEventRepository,
    @Autowired private val roomInviteLinkRepository: RoomInviteLinkRepository,
    @Autowired private val em: EntityManager,
) {

    private lateinit var owner: User
    private lateinit var calendar: Calendar
    private lateinit var room: SchedulingRoom

    @BeforeEach
    fun setUp() {
        owner = persist(User.signUp("owner@mail.com", "{bcrypt}hash", "방장"))
        calendar = persist(Calendar.create("친구 모임", null, null, "Asia/Seoul"))
        room = persist(newRoom(calendar))
    }

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun newRoom(calendar: Calendar): SchedulingRoom =
        SchedulingRoom.create(
            owner, calendar, "친구 모임", LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 28),
            LocalTime.of(9, 0), LocalTime.of(18, 0), "Asia/Seoul",
        )

    @Test
    fun `공통 캘린더 하나는 방 하나에만 쓸 수 있다`() {
        // when & then
        assertThrows<DataIntegrityViolationException> {
            schedulingRoomRepository.saveAndFlush(newRoom(calendar))
        }
    }

    @Test
    fun `한 회원은 한 방에 참여 행을 하나만 가진다`() {
        // given
        roomMemberRepository.saveAndFlush(RoomMember.create(room, owner))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            roomMemberRepository.saveAndFlush(RoomMember.create(room, owner))
        }
    }

    @Test
    fun `같은 참여 행이 같은 일정을 두 번 공유할 수 없다`() {
        // given
        val member = persist(RoomMember.create(room, owner))
        val event = persist(
            Event.create(
                calendar, null, owner, "회의", null, null,
                Instant.parse("2026-11-24T01:00:00Z"), Instant.parse("2026-11-24T02:00:00Z"),
            ),
        )
        roomSharedEventRepository.saveAndFlush(RoomSharedEvent.create(member, event))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            roomSharedEventRepository.saveAndFlush(RoomSharedEvent.create(member, event))
        }
    }

    @Test
    fun `초대 링크 토큰 해시는 겹칠 수 없다`() {
        // given
        val hash = "a".repeat(64)
        roomInviteLinkRepository.saveAndFlush(RoomInviteLink.create(room, owner, hash))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            roomInviteLinkRepository.saveAndFlush(RoomInviteLink.create(room, owner, hash))
        }
    }
}
