package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarCreateRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarDisplayRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupMoveRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarResponse
import com.team4.live_calendar.domain.calendar.dto.CalendarUpdateRequest
import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarInvitationRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.repository.CategoryOptionRepository
import com.team4.live_calendar.domain.category.repository.EventCategoryRepository
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import com.team4.live_calendar.domain.event.repository.EventOptionRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.notification.repository.NotificationRepository
import com.team4.live_calendar.domain.room.repository.RoomSharedEventRepository
import com.team4.live_calendar.domain.room.repository.SchedulingRoomRepository
import com.team4.live_calendar.domain.share.repository.ShareCalendarConnectionRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.websocket.CalendarChangedEvent
import com.team4.live_calendar.global.websocket.DisplayChangedEvent
import com.team4.live_calendar.global.websocket.SyncType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.ZoneId

// 원본 캘린더 생성·상세·수정·삭제와 내 그룹 옮기기, 내 화면 표시 설정(표시 여부·내 색)
// 누가 캘린더를 어떻게 쓰는지(역할, 내 그룹, 표시 여부, 내 색)는 이용 관계(subscribes)에 있다
// 권한 검사와 시간대 검증은 일정 API(EventService)와 같은 규칙이다. 일정 파일을 건드리지 않으려고 여기에 따로 둔다
@Service
@Transactional(readOnly = true)
class CalendarService(
    private val calendarRepository: CalendarRepository,
    private val subscribeRepository: SubscribeRepository,
    private val calendarGroupRepository: CalendarGroupRepository,
    private val calendarGroupService: CalendarGroupService,
    private val userRepository: UserRepository,
    private val schedulingRoomRepository: SchedulingRoomRepository,
    private val notificationRepository: NotificationRepository,
    private val eventExceptionRepository: EventExceptionRepository,
    private val eventOptionRepository: EventOptionRepository,
    private val roomSharedEventRepository: RoomSharedEventRepository,
    private val eventRepository: EventRepository,
    private val categoryOptionRepository: CategoryOptionRepository,
    private val eventCategoryRepository: EventCategoryRepository,
    private val calendarInvitationRepository: CalendarInvitationRepository,
    private val shareCalendarConnectionRepository: ShareCalendarConnectionRepository,
    // 바뀐 사실만 이벤트로 알리고, 커밋 뒤 SyncSignalListener가 STOMP 신호로 보낸다
    private val eventPublisher: ApplicationEventPublisher,
) {

    // 캘린더와 만든 사람의 OWNER 이용 관계를 함께 만든다
    // 그룹: 요청한 groupId(내 그룹이어야 함) → 없으면 내 첫 그룹 → 그룹이 하나도 없으면 "내 캘린더"
    @Transactional
    fun create(userId: Long, request: CalendarCreateRequest): CalendarResponse {
        // 시간대를 먼저 검증해서, 잘못된 요청으로 "내 캘린더" 그룹이 생기지 않게 한다
        val timezone = givenTimezone(request.timezone) ?: Calendar.DEFAULT_TIMEZONE
        val group = request.groupId?.let { findMyGroup(userId, it) }
            ?: calendarGroupService.findOrCreateDefaultGroup(userId)
        val calendar = calendarRepository.save(
            Calendar.create(request.name, request.description, request.color, timezone),
        )
        val owner = Subscribe.create(
            user = userRepository.getReferenceById(userId),
            group = group,
            calendar = calendar,
            role = CalendarRole.OWNER,
            color = null,
        )
        val saved = subscribeRepository.save(owner)
        // 새 캘린더는 아직 구독자가 없어 캘린더 신호는 보내지 않고, 내 다른 기기에만 알린다
        eventPublisher.publishEvent(DisplayChangedEvent(userId))
        return CalendarResponse.from(saved)
    }

    // 이용 관계가 있으면 역할과 상관없이 볼 수 있다 (SUBSCRIBER 포함)
    fun get(userId: Long, calendarId: Long): CalendarResponse =
        CalendarResponse.from(findMySubscribe(userId, findCalendar(calendarId)))

    // OWNER만. 모든 칸 보내기라 안 보낸 description, color는 비워진다
    // timezone만 없거나 비어 있으면 기존 값을 유지한다 (일정 수정과 같음)
    @Transactional
    fun update(userId: Long, calendarId: Long, request: CalendarUpdateRequest): CalendarResponse {
        val calendar = findCalendar(calendarId)
        val subscribe = checkOwner(findMySubscribe(userId, calendar))
        eventPublisher.publishEvent(CalendarChangedEvent(SyncType.CALENDAR_UPDATED, calendarId))
        val timezone = givenTimezone(request.timezone) ?: calendar.timezone
        calendar.update(request.name, request.description, request.color, timezone)
        return CalendarResponse.from(subscribe)
    }

    // 내 이용 관계의 그룹만 바꾼다. 원본·역할·다른 사람의 배치는 그대로
    @Transactional
    fun moveGroup(userId: Long, calendarId: Long, request: CalendarGroupMoveRequest): CalendarResponse {
        val subscribe = findMySubscribe(userId, findCalendar(calendarId))
        eventPublisher.publishEvent(DisplayChangedEvent(userId))
        subscribe.moveTo(findMyGroup(userId, requireNotNull(request.groupId)))
        return CalendarResponse.from(subscribe)
    }

    // 내 화면에서 이 캘린더를 보일지와 내 색 (모든 칸 보내기, color가 null이면 원본 색)
    // 역할과 상관없이 이용 관계가 있으면 바꿀 수 있다. 원본과 다른 사람의 화면은 그대로
    @Transactional
    fun changeDisplay(userId: Long, calendarId: Long, request: CalendarDisplayRequest) {
        val subscribe = findMySubscribe(userId, findCalendar(calendarId))
        eventPublisher.publishEvent(DisplayChangedEvent(userId))
        subscribe.changeDisplay(requireNotNull(request.isVisible), request.color)
    }

    // OWNER만. 캘린더와 연결 데이터를 FK 순서대로 모두 지운다 (다른 사람의 이용 관계·구독 포함)
    // 조율방이 공통 캘린더로 쓰면 409로 막고 아무것도 지우지 않는다. 알림은 남기고 캘린더 칸만 비운다
    // 엔티티를 하나씩 불러 지우지 않고 캘린더 ID로 한 번에 지워서, 일정이 많아도 쿼리 수가 같다
    // 확인과 삭제 사이에 다른 요청이 조율방을 만들거나 일정을 넣으면 FK 때문에 실패한다 (드물어서 막지 않음)
    @Transactional
    fun delete(userId: Long, calendarId: Long) {
        checkOwner(findMySubscribe(userId, findCalendar(calendarId)))
        if (schedulingRoomRepository.existsByCalendarId(calendarId)) {
            throw BusinessException(ErrorCode.CALENDAR_IN_USE_BY_ROOM)
        }
        // 409로 막힌 삭제는 알리지 않는다. 지운 뒤에도 브로커 구독은 남아 있어 구독자가 받는다
        eventPublisher.publishEvent(CalendarChangedEvent(SyncType.CALENDAR_DELETED, calendarId))
        notificationRepository.clearCalendar(calendarId)
        eventExceptionRepository.deleteAllInCalendar(calendarId)
        eventOptionRepository.deleteAllInCalendar(calendarId)
        roomSharedEventRepository.deleteAllInCalendar(calendarId)
        eventRepository.deleteAllInCalendar(calendarId)
        categoryOptionRepository.deleteAllInCalendar(calendarId)
        eventCategoryRepository.deleteAllInCalendar(calendarId)
        subscribeRepository.deleteAllInCalendar(calendarId)
        calendarInvitationRepository.deleteAllInCalendar(calendarId)
        shareCalendarConnectionRepository.deleteAllInCalendar(calendarId)
        // 벌크 삭제라 SQL이 바로 나간다 (delete(entity)는 커밋 때 나가서 테스트가 마지막 FK 오류를 못 잡는다)
        // 위에서 불러 둔 캘린더·이용 관계 엔티티는 컨텍스트에 남지만 다시 쓰지 않는다
        calendarRepository.deleteCalendar(calendarId)
    }

    private fun findCalendar(calendarId: Long): Calendar =
        calendarRepository.findByIdOrNull(calendarId) ?: throw BusinessException(ErrorCode.CALENDAR_NOT_FOUND)

    // 이용 관계가 없으면 403 (일정 API의 checkReadable과 같음)
    private fun findMySubscribe(userId: Long, calendar: Calendar): Subscribe =
        subscribeRepository.findByUserIdAndCalendarId(userId, requireNotNull(calendar.id))
            ?: throw BusinessException(ErrorCode.FORBIDDEN)

    // 캘린더 자체를 바꾸거나 지우는 건 OWNER만 (EDITOR는 일정만 편집)
    private fun checkOwner(subscribe: Subscribe): Subscribe {
        if (subscribe.role != CalendarRole.OWNER) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }
        return subscribe
    }

    // 없거나 남의 그룹이면 404 (그룹 API와 같음)
    private fun findMyGroup(userId: Long, groupId: Long): CalendarGroup =
        calendarGroupRepository.findByIdAndUserId(groupId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

    // 요청에 시간대가 있으면 검증한 이름, 없거나 비어 있으면 null (EventService.givenTimezone·validTimezone과 같은 검사·메시지)
    // ZoneId.of는 IANA 이름뿐 아니라 +09:00 같은 고정 시차도 받는다 (일정의 단일 일정과 같음)
    private fun givenTimezone(timezone: String?): String? {
        if (timezone.isNullOrBlank()) return null
        return try {
            ZoneId.of(timezone).id
        } catch (e: DateTimeException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "올바르지 않은 시간대입니다: $timezone")
        }
    }
}
