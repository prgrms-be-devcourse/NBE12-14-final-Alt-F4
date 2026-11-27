package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.event.service.EventOccurrenceReader
import com.team4.live_calendar.domain.room.dto.MySharedEventResponse
import com.team4.live_calendar.domain.room.dto.SharedEventCreateRequest
import com.team4.live_calendar.domain.room.dto.SharedEventDisplayModeRequest
import com.team4.live_calendar.domain.room.dto.SharedEventResponse
import com.team4.live_calendar.domain.room.entity.RoomMember
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.repository.RoomSharedEventRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class RoomSharedEventService(
    private val roomSharedEventRepository: RoomSharedEventRepository,
    private val eventRepository: EventRepository,
    private val subscribeRepository: SubscribeRepository,
    private val roomAccess: RoomAccess,
    private val eventOccurrenceReader: EventOccurrenceReader,
    private val publicIds: PublicIds,
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

    // 달력 화면이 보고 있는 구간 [from, to)만 본다 (일정 기간 조회와 같은 규칙, 최대 93일)
    fun findInRoom(userId: Long, roomId: String, from: OffsetDateTime, to: OffsetDateTime): List<SharedEventResponse> {
        val me = roomAccess.currentMember(roomId, userId)
        return occurrencesInRoom(me.room, from.toInstant(), to.toInstant()).map { (shared, occurrence) ->
            SharedEventResponse.of(shared, occurrence, isMine = shared.member.id == me.id, publicIds = publicIds)
        }
    }

    @Transactional
    fun changeDisplayMode(
        userId: Long,
        roomId: String,
        sharedEventId: Long,
        request: SharedEventDisplayModeRequest
    ): MySharedEventResponse {
        val member = roomAccess.currentMember(roomId, userId)
        val shared = findMine(member, sharedEventId)
        shared.changeDisplayMode(request.displayMode)
        return MySharedEventResponse.from(shared)
    }

    // 현재 참여자가 공유한 일정을, 조회 구간 [from, to)와 조율 기간이 겹치는 부분의 회차로 펼친다
    // 빈 시간 계산에서도 쓴다
    fun occurrencesInRoom(
        room: SchedulingRoom,
        from: Instant,
        to: Instant
    ): List<Pair<RoomSharedEvent, EventOccurrenceReader.Occurrence>> {
        if (!from.isBefore(to) || Duration.between(from, to) > MAX_QUERY_RANGE) {
            throw BusinessException(ErrorCode.INVALID_DATE_RANGE)
        }
        val (periodStart, periodEnd) = room.period()
        val start = maxOf(from, periodStart)
        val end = minOf(to, periodEnd)
        if (!start.isBefore(end)) return emptyList()

        val shares = roomSharedEventRepository.findAllActiveInRoom(requireNotNull(room.id))
        if (shares.isEmpty()) return emptyList()

        val zone = ZoneId.of(room.timezone)
        // 같은 일정을 두 참여자가 공유할 수 있다 (같은 캘린더를 둘 다 구독한 경우)
        val sharesByEventId = shares.groupBy { it.event.id }
        val occurrences = eventOccurrenceReader.read(shares.map { it.event }.distinctBy { it.id }, start, end, zone)
        return occurrences.flatMap { occurrence ->
            sharesByEventId[occurrence.event.id].orEmpty().map { it to occurrence }
        }
    }

    // 남이 공유한 것도 "없는 공유"로 답한다
    private fun findMine(member: RoomMember, sharedEventId: Long): RoomSharedEvent {
        val shared = roomSharedEventRepository.findByIdOrNull(sharedEventId)
        if (shared == null || shared.member.id != member.id) throw BusinessException(ErrorCode.SHARED_EVENT_NOT_FOUND)
        return shared
    }

    companion object {
        // 일정 기간 조회(EventService.MAX_RANGE)와 같은 값: 한 화면에 보이는 최대 기간(3개월)
        private val MAX_QUERY_RANGE: Duration = Duration.ofDays(93)
    }

}
