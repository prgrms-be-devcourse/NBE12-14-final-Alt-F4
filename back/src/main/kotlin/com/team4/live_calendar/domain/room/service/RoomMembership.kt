package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.stereotype.Component
import java.time.Instant

// 방 참여와 공통 캘린더 이용 관계(subscribes)를 함께 맞춘다. 부르는 서비스의 트랜잭션 안에서 쓴다
@Component
class RoomMembership(
    private val roomMemberRepository: RoomMemberRepository,
    private val subscribeRepository: SubscribeRepository,
    private val calendarGroupRepository: CalendarGroupRepository,
) {

    // 공통 캘린더를 넣을 내 그룹. 남의 그룹은 없는 그룹과 같게 404 (#33 CalendarGroupService와 같은 규칙)
    fun myGroup(userId: Long, groupId: Long): CalendarGroup =
        calendarGroupRepository.findByIdAndUserId(groupId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

    // 이미 참여 중이면 그대로 돌려준다 (중복 수락해도 참여 행이 늘지 않는다)
    fun join(room: SchedulingRoom, user: User, group: CalendarGroup): RoomMember {
        val existing = roomMemberRepository.findByRoomIdAndUserId(requireNotNull(room.id), requireNotNull(user.id))
        if (existing != null && existing.isActive()) return existing

        val member = existing?.also { it.rejoin(Instant.now()) }
            ?: roomMemberRepository.save(RoomMember.create(room, user))
        showRoomCalendar(room, user, group)
        return member
    }

    // 확정 일정이 내 달력에 뜨도록 공통 캘린더를 내가 고른 그룹에 넣는다
    // 표시용 관계라 방장도 VIEWER: EDITOR면 /api/v1/events로 조율 범위 검사를 건너뛰고,
    // SUBSCRIBER면 구독 취소 API로 이 관계만 지워진다. 방 권한은 RoomAccess가 따로 판단한다
    private fun showRoomCalendar(room: SchedulingRoom, user: User, group: CalendarGroup) {
        val calendar = room.calendar
        if (subscribeRepository.findByUserIdAndCalendarId(
                requireNotNull(user.id),
                requireNotNull(calendar.id)
            ) != null
        ) return
        subscribeRepository.save(Subscribe.create(user, group, calendar, CalendarRole.VIEWER, color = null))
    }
}