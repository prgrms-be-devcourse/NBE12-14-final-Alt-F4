package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.repository.EventCategoryRepository
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.recurrence.ViewRange
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import com.team4.live_calendar.domain.event.repository.EventOptionRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.room.repository.RoomSharedEventRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@Service
@Transactional(readOnly = true)
class EventService(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    private val eventCategoryRepository: EventCategoryRepository,
    private val subscribeRepository: SubscribeRepository,
    private val userRepository: UserRepository,
    private val eventOptionRepository: EventOptionRepository,
    private val eventExceptionRepository: EventExceptionRepository,
    private val roomSharedEventRepository: RoomSharedEventRepository,
) {

    @Transactional
    fun create(userId: Long, request: EventCreateRequest): EventResponse {
        val calendar = calendarRepository.findByIdOrNull(request.calendarId)
            ?: throw BusinessException(ErrorCode.CALENDAR_NOT_FOUND)
        checkEditable(userId, calendar)

        val (startAt, endAt) = toPeriod(request.isAllDay, request.startAt, request.endAt, request.startDate, request.endDate)
        val event = Event.create(
            calendar = calendar,
            category = findCategoryIn(calendar, request.categoryId),
            user = userRepository.getReferenceById(userId),
            title = request.title,
            description = request.description,
            location = request.location,
            startAt = startAt,
            endAt = endAt,
            isAllDay = request.isAllDay,
            timezone = validTimezone(request.timezone),
        )
        return EventResponse.from(eventRepository.saveAndFlush(event))
    }

    fun get(userId: Long, eventId: Long): EventResponse {
        val event = findEvent(eventId)
        checkReadable(userId, event.calendar)
        return EventResponse.from(event)
    }

    // [from, to) 기간과 겹치는 일정. 내가 이용하는 캘린더만, 내가 숨긴 일정은 제외
    // 종일 일정은 보는 사람 시간대(viewTimezone, 없으면 UTC)의 날짜로 비교한다
    // 반복 일정은 아직 회차로 펼치지 않는다
    fun findInRange(
        userId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime,
        calendarIds: Collection<Long>? = null,
        viewTimezone: String? = null,
    ): List<EventResponse> {
        val fromInstant = from.toInstant()
        val toInstant = to.toInstant()
        if (!fromInstant.isBefore(toInstant) || Duration.between(fromInstant, toInstant) > MAX_RANGE) {
            throw BusinessException(ErrorCode.INVALID_DATE_RANGE)
        }
        val range = ViewRange(fromInstant, toInstant, ZoneId.of(validTimezone(viewTimezone)))

        val subscribes = subscribeRepository.findAllByUserId(userId)
            .filter { calendarIds == null || it.calendar.id in calendarIds }
        if (subscribes.isEmpty()) return emptyList()

        val hiddenEventIds = eventOptionRepository.findHiddenEventIds(subscribes.map { it.idOrThrow() }).toSet()
        // 종일 일정은 보는 사람 시간대에 따라 하루까지 차이 나므로 DB에서는 앞뒤 하루씩 넉넉하게 가져와 거른다
        return eventRepository.findOverlapping(
            subscribes.map { it.calendar.idOrThrow() },
            fromInstant.minus(DB_MARGIN),
            toInstant.plus(DB_MARGIN),
        )
            .filter { range.overlaps(it.startAt, it.endAt, it.isAllDay) }
            .filter { it.id !in hiddenEventIds }
            .map(EventResponse::from)
    }

    @Transactional
    fun update(userId: Long, eventId: Long, request: EventUpdateRequest): EventResponse {
        val event = findEvent(eventId)
        checkEditable(userId, event.calendar)

        val (startAt, endAt) = toPeriod(request.isAllDay, request.startAt, request.endAt, request.startDate, request.endDate)
        event.update(
            title = request.title,
            description = request.description,
            location = request.location,
            startAt = startAt,
            endAt = endAt,
            isAllDay = request.isAllDay,
            category = findCategoryIn(event.calendar, request.categoryId),
        )
        return EventResponse.from(eventRepository.saveAndFlush(event))
    }

    @Transactional
    fun delete(userId: Long, eventId: Long) {
        val event = findEvent(eventId)
        checkEditable(userId, event.calendar)

        // 일정을 참조하는 데이터를 먼저 지운다 (FK)
        eventOptionRepository.deleteAllByEventId(eventId)
        eventExceptionRepository.deleteAllByEventId(eventId)
        roomSharedEventRepository.deleteAllByEventId(eventId)
        eventRepository.delete(event)
    }

    private fun findEvent(eventId: Long): Event =
        eventRepository.findByIdOrNull(eventId) ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

    // 이용 관계가 있으면 조회 가능 (OWNER, EDITOR, VIEWER)
    private fun checkReadable(userId: Long, calendar: Calendar): Subscribe =
        subscribeRepository.findByUserIdAndCalendarId(userId, calendar.idOrThrow())
            ?: throw BusinessException(ErrorCode.FORBIDDEN)

    // 생성·수정·삭제는 같은 편집 권한으로 확인
    private fun checkEditable(userId: Long, calendar: Calendar) {
        if (checkReadable(userId, calendar).role !in EDITABLE_ROLES) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }
    }

    // 일정과 같은 캘린더의 카테고리만 지정 가능, null이면 카테고리 없음
    private fun findCategoryIn(calendar: Calendar, categoryId: Long?): EventCategory? {
        if (categoryId == null) return null
        val category = eventCategoryRepository.findByIdOrNull(categoryId)
        if (category == null || category.calendar.id != calendar.id) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_IN_CALENDAR)
        }
        return category
    }

    // 시간 일정: startAt ~ endAt 그대로 (UTC 순간으로 저장)
    // 종일 일정: 날짜만 사용해 시작일 UTC 자정 ~ 종료일 다음 날 UTC 자정
    // (시간을 받으면 JSON 변환 과정에서 UTC로 바뀌어 날짜가 밀릴 수 있어 날짜 필드를 따로 받는다)
    private fun toPeriod(
        isAllDay: Boolean,
        startAt: OffsetDateTime?,
        endAt: OffsetDateTime?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): Pair<Instant, Instant> {
        val period = if (isAllDay) {
            if (startDate == null || endDate == null) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "종일 일정은 startDate와 endDate가 필요합니다.")
            }
            if (endDate.isBefore(startDate)) {
                throw BusinessException(ErrorCode.INVALID_EVENT_TIME)
            }
            startDate.atStartOfDay(ZoneOffset.UTC).toInstant() to
                endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        } else {
            if (startAt == null || endAt == null) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "시간 일정은 startAt과 endAt이 필요합니다.")
            }
            startAt.toInstant() to endAt.toInstant()
        }
        if (period.second.isBefore(period.first)) {
            throw BusinessException(ErrorCode.INVALID_EVENT_TIME)
        }
        return period
    }

    private fun validTimezone(timezone: String?): String {
        if (timezone.isNullOrBlank()) return Event.DEFAULT_TIMEZONE
        return try {
            ZoneId.of(timezone).id
        } catch (e: DateTimeException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "올바르지 않은 시간대입니다: $timezone")
        }
    }

    private fun Calendar.idOrThrow(): Long = requireNotNull(id)

    private fun Subscribe.idOrThrow(): Long = requireNotNull(id)

    companion object {
        private val EDITABLE_ROLES = setOf(CalendarRole.OWNER, CalendarRole.EDITOR)
        private val MAX_RANGE: Duration = Duration.ofDays(93)
        private val DB_MARGIN: Duration = Duration.ofDays(1)
    }
}
