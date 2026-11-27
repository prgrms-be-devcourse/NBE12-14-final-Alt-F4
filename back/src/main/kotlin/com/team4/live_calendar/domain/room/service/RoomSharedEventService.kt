package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.room.dto.MySharedEventResponse
import com.team4.live_calendar.domain.room.dto.SharedEventCreateRequest
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.repository.RoomSharedEventRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoomSharedEventService(
    private val roomSharedEventRepository: RoomSharedEventRepository,
    private val eventRepository: EventRepository,
    private val subscribeRepository: SubscribeRepository,
    private val roomAccess: RoomAccess,
) {

    // 이미 공유한 일정은 그대로 두고 새 일정만 추가한다 (모두 "시간만" 표시로 시작)
    @Transactional
    // roomId는 방 코드(Sqids)
    fun share(userId: Long, roomId: String, request: SharedEventCreateRequest): List<MySharedEventResponse> {
        val member = roomAccess.currentMember(roomId, userId)
        val eventIds = request.eventIds.distinct()
        val events = eventRepository.findAllById(eventIds)
        if (events.size != eventIds.size) throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

        // 내가 이용하는 캘린더(역할 무관: OWNER·EDITOR·VIEWER·SUBSCRIBER)의 일정만. 볼 수 없는 일정은 없는 일정처럼 404
        val myCalendarIds = subscribeRepository.findAllByUserId(userId).map { it.calendar.id }.toSet()
        val roomCalendarId = member.room.calendar.id
        events.forEach { event ->
            if (event.calendar.id == roomCalendarId) throw BusinessException(ErrorCode.EVENT_NOT_SHAREABLE)
            if (event.calendar.id !in myCalendarIds) throw BusinessException(ErrorCode.EVENT_NOT_FOUND)
        }

        val alreadyShared =
            roomSharedEventRepository.findAllByMemberId(requireNotNull(member.id)).associateBy { it.event.id }
        val result = events.map { event ->
            alreadyShared[event.id] ?: roomSharedEventRepository.save(RoomSharedEvent.create(member, event))
        }
        return result.map(MySharedEventResponse::from)
    }

    @Transactional
    fun unshare(userId: Long, roomId: String, sharedEventId: Long) {
        val member = roomAccess.currentMember(roomId, userId)
        roomSharedEventRepository.delete(findMine(member, sharedEventId))
    }

    // 남이 공유한 것도 "없는 공유"로 답한다
    private fun findMine(member: RoomMember, sharedEventId: Long): RoomSharedEvent {
        val shared = roomSharedEventRepository.findByIdOrNull(sharedEventId)
        if (shared == null || shared.member.id != member.id) throw BusinessException(ErrorCode.SHARED_EVENT_NOT_FOUND)
        return shared
    }
}
