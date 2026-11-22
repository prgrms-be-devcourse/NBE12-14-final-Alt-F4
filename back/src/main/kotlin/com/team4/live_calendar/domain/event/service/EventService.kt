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
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.entity.EventOption
import com.team4.live_calendar.domain.event.recurrence.RecurrenceExpander
import com.team4.live_calendar.domain.event.recurrence.RecurrenceRule
import com.team4.live_calendar.domain.event.recurrence.ViewRange
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import com.team4.live_calendar.domain.event.repository.EventOptionRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
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

        val event = buildEvent(
            calendar, userId, request.categoryId, request.title, request.description, request.location,
            request.isAllDay, request.startAt, request.endAt, request.startDate, request.endDate,
            givenTimezone(request.timezone), request.rrule,
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
    // 반복 일정은 기간 안의 회차마다 하나씩 (같은 id, 회차의 시간). 지운 회차는 빼고, 바뀐 회차는 바뀐 내용으로
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
        val subscribedCalendarIds = subscribes.map { it.calendar.idOrThrow() }
        // 종일 일정은 보는 사람 시간대에 따라 하루까지 차이 나므로 DB에서는 앞뒤 하루씩 넉넉하게 가져와 거른다
        val dbFrom = fromInstant.minus(DB_MARGIN)
        val dbTo = toInstant.plus(DB_MARGIN)

        val singles = eventRepository.findSingleOverlapping(subscribedCalendarIds, dbFrom, dbTo)
            .filter { it.id !in hiddenEventIds }
            .filter { range.overlaps(it.startAt, it.endAt, it.isAllDay) }
            .map(EventResponse::from)
        val expanded = eventRepository.findRecurringCandidates(subscribedCalendarIds, dbFrom, dbTo)
            .filter { it.id !in hiddenEventIds }
            .map { it to RecurrenceExpander.expand(it, range) }
        // 예외(지움·바뀜)가 있는 회차는 펼친 결과에서 뺀다. 바뀐 회차는 아래에서 바뀐 시간으로 다시 넣는다
        // 예외는 펼친 회차 시작 범위 안의 것만 가져온다 (오래된 반복 일정의 지난 예외까지 읽지 않도록)
        val starts = expanded.flatMap { (_, occurrences) -> occurrences.map { it.startAt } }
        val exceptionKeys = if (starts.isEmpty()) {
            emptySet()
        } else {
            eventExceptionRepository.findAllInRecurrenceRange(expanded.map { requireNotNull(it.first.id) }, starts.min(), starts.max())
                .map { it.event.id to it.recurrenceId }
                .toSet()
        }
        val occurrences = expanded.flatMap { (event, occurrences) ->
            occurrences
                .filter { (event.id to it.startAt) !in exceptionKeys }
                .map { EventResponse.from(event, it.startAt, it.endAt) }
        }
        // 다른 날로 옮긴 회차, 반복이 끝난 뒤로 옮긴 회차도 나오도록 바뀐 시간으로 따로 찾는다
        // 같은 회차에 예외가 두 줄 생겼어도 한 번만 보이도록 먼저 만든 줄만 쓴다
        val modified = eventExceptionRepository.findModifiedOverlapping(subscribedCalendarIds, dbFrom, dbTo)
            .sortedBy { it.id }
            .distinctBy { it.event.id to it.recurrenceId }
            .filter { it.event.id !in hiddenEventIds }
            .filter { range.overlaps(requireNotNull(it.startAt), requireNotNull(it.endAt), it.event.isAllDay) }
            .map { EventResponse.from(it.event, it) }
        return (singles + occurrences + modified).sortedWith(compareBy({ it.startAt }, { it.id }))
    }

    // 반복 일정은 scope가 필요하다. THIS면 그 회차만, THIS_AND_FOLLOWING이면 그 회차부터, ALL이면 반복 전체를 수정한다
    @Transactional
    fun update(
        userId: Long,
        eventId: Long,
        request: EventUpdateRequest,
        scope: RecurrenceScope? = null,
        occurrenceStart: OffsetDateTime? = null,
    ): EventResponse {
        val event = findEvent(eventId)
        checkEditable(userId, event.calendar)
        val wasRecurring = event.rrule != null
        // 반복 없는 일정은 scope 없이 아래 기존 흐름. 반복 일정은 범위를 빠짐없이 나눈다 (값이 늘면 컴파일러가 알려준다)
        if (wasRecurring) {
            when (requireScope(scope)) {
                RecurrenceScope.THIS -> return updateOccurrence(event, request, occurrenceStart)
                RecurrenceScope.THIS_AND_FOLLOWING -> {
                    val (recurrenceId, _) = findOccurrence(event, occurrenceStart)
                    // 첫 회차에서 고르면 나눌 앞부분이 없어 전체 수정과 같다
                    if (recurrenceId != event.startAt) return updateFollowing(userId, event, request, recurrenceId)
                }
                RecurrenceScope.ALL -> Unit
            }
        }

        // 전체 수정에서 회차 일정(시간·규칙·종일·시간대)이 바뀌면 기존 예외는 원래 회차와 맞지 않아 지운다
        val scheduleBefore = event.schedule()
        val (startAt, endAt) = toPeriod(request.isAllDay, request.startAt, request.endAt, request.startDate, request.endDate)
        // 시간대를 보내지 않으면 기존 값 유지. 단, 반복 시간 일정이 아니었다면 기존 값이 기본값(UTC)일 수 있어
        // 반복 계산에는 쓰지 않는다 (시간 일정을 처음 반복으로 바꿀 때는 timezone 필요)
        val timezone = givenTimezone(request.timezone)
            ?: event.timezone.takeIf { event.rrule != null && !event.isAllDay }
        val recurrence = resolveRecurrence(request.rrule, request.isAllDay, startAt, endAt, timezone)
        event.update(
            title = request.title,
            description = request.description,
            location = request.location,
            startAt = startAt,
            endAt = endAt,
            isAllDay = request.isAllDay,
            category = findCategoryIn(event.calendar, request.categoryId),
        )
        // 반복 규칙을 보내지 않으면 반복 해제
        event.changeRecurrence(timezone ?: event.timezone, recurrence?.rrule, recurrence?.recurUntil)
        if (wasRecurring && event.schedule() != scheduleBefore) {
            eventExceptionRepository.deleteAllByEventId(eventId)
        }
        return EventResponse.from(eventRepository.saveAndFlush(event))
    }

    // 반복 일정은 scope가 필요하다. THIS면 그 회차만, THIS_AND_FOLLOWING이면 그 회차부터, ALL이면 지난 회차까지 모두 삭제한다
    @Transactional
    fun delete(userId: Long, eventId: Long, scope: RecurrenceScope? = null, occurrenceStart: OffsetDateTime? = null) {
        val event = findEvent(eventId)
        checkEditable(userId, event.calendar)
        // 반복 없는 일정은 scope 없이 아래 기존 흐름. 반복 일정은 범위를 빠짐없이 나눈다
        if (event.rrule != null) {
            when (requireScope(scope)) {
                RecurrenceScope.THIS -> {
                    deleteOccurrence(event, occurrenceStart)
                    return
                }
                RecurrenceScope.THIS_AND_FOLLOWING -> {
                    val (recurrenceId, _) = findOccurrence(event, occurrenceStart)
                    // 첫 회차에서 고르면 나눌 앞부분이 없어 전체 삭제와 같다. 아니면 그 회차 전에 반복을 끝낸다
                    if (recurrenceId != event.startAt) {
                        endRecurrenceBefore(event, recurrenceId)
                        return
                    }
                }
                RecurrenceScope.ALL -> Unit
            }
        }

        // 일정을 참조하는 데이터를 먼저 지운다 (FK)
        eventOptionRepository.deleteAllByEventId(eventId)
        eventExceptionRepository.deleteAllByEventId(eventId)
        roomSharedEventRepository.deleteAllByEventId(eventId)
        eventRepository.delete(event)
    }

    // 이 회차만 수정: 제목·설명·장소·시간만 바꿀 수 있다 (event_exceptions에 종일 여부·카테고리 칸이 없음)
    private fun updateOccurrence(event: Event, request: EventUpdateRequest, occurrenceStart: OffsetDateTime?): EventResponse {
        val (recurrenceId, exception) = findOccurrence(event, occurrenceStart)
        if (request.isAllDay != event.isAllDay) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "이 일정만 수정할 때는 종일 여부를 바꿀 수 없습니다.")
        }
        if (request.categoryId != event.category?.id) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "이 일정만 수정할 때는 카테고리를 바꿀 수 없습니다.")
        }
        val (startAt, endAt) = toPeriod(request.isAllDay, request.startAt, request.endAt, request.startDate, request.endDate)

        // 같은 회차를 다시 고치면 새 줄을 만들지 않고 기존 예외를 고친다
        val modified = exception?.apply { modify(request.title, request.description, request.location, startAt, endAt) }
            ?: eventExceptionRepository.save(
                EventException.create(
                    event = event,
                    recurrenceId = recurrenceId,
                    type = EventExceptionType.MODIFIED,
                    title = request.title,
                    description = request.description,
                    location = request.location,
                    startAt = startAt,
                    endAt = endAt,
                ),
            )
        // 바로 반영해서 응답의 updatedAt이 이번 수정 시각이 되게 한다
        return EventResponse.from(event, eventExceptionRepository.saveAndFlush(modified))
    }

    // 이 일정과 이후 수정: 원래 반복은 고른 회차 전에 끝내고, 고른 회차부터는 요청 내용으로 새 일정을 만든다
    // 새 일정을 먼저 검증해서, 실패하면 원래 일정은 바뀌지 않는다
    private fun updateFollowing(userId: Long, event: Event, request: EventUpdateRequest, recurrenceId: Instant): EventResponse {
        // 시간대를 보내지 않으면 원래 일정의 시간대 (원래 일정이 반복 시간 일정일 때만, 1차 수정 규칙과 같음)
        val timezone = givenTimezone(request.timezone) ?: event.timezone.takeIf { !event.isAllDay }
        val newEvent = buildEvent(
            event.calendar, userId, request.categoryId, request.title, request.description, request.location,
            request.isAllDay, request.startAt, request.endAt, request.startDate, request.endDate,
            timezone, request.rrule,
        )
        endRecurrenceBefore(event, recurrenceId)
        val saved = eventRepository.saveAndFlush(newEvent)
        copyHiddenAndShares(event, saved)
        return EventResponse.from(saved)
    }

    // 이 일정과 이후: 원래 반복을 recurrenceId 회차 바로 전에 끝내고, 그 회차부터의 예외는 지운다
    private fun endRecurrenceBefore(event: Event, recurrenceId: Instant) {
        val truncated = RecurrenceRule.truncate(requireNotNull(event.rrule), event.isAllDay, recurrenceId)
        val result = RecurrenceRule.resolve(truncated, event.isAllDay, event.startAt, event.endAt, event.timezone)
        event.changeRecurrence(event.timezone, result.rrule, result.recurUntil)
        eventExceptionRepository.deleteAllByEventIdAndRecurrenceIdGreaterThanEqual(requireNotNull(event.id), recurrenceId)
    }

    // 원래 일정을 숨긴 설정과 조율방 공유를 새 일정에도 둔다 (사용자에게는 같은 일정의 시간 변경)
    private fun copyHiddenAndShares(from: Event, to: Event) {
        val fromId = requireNotNull(from.id)
        eventOptionRepository.saveAll(
            eventOptionRepository.findHiddenByEventId(fromId).map { EventOption.create(it.subscribe, to, it.calendar, isVisible = false) },
        )
        roomSharedEventRepository.saveAll(
            roomSharedEventRepository.findAllByEventId(fromId).map { RoomSharedEvent.create(it.member, to, it.displayMode) },
        )
    }

    // 이 회차만 삭제: 수정했던 회차면 DELETED로 바꾼다
    private fun deleteOccurrence(event: Event, occurrenceStart: OffsetDateTime?) {
        val (recurrenceId, exception) = findOccurrence(event, occurrenceStart)
        if (exception != null) {
            exception.markDeleted()
        } else {
            eventExceptionRepository.save(EventException.create(event, recurrenceId, EventExceptionType.DELETED))
        }
    }

    // 이 일정만 수정·삭제할 회차와 그 회차의 예외. 실제 회차가 아니거나 이미 지운 회차면 404
    private fun findOccurrence(event: Event, occurrenceStart: OffsetDateTime?): Pair<Instant, EventException?> {
        val recurrenceId = occurrenceStart?.toInstant()
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "이 일정만 수정·삭제하려면 occurrenceStart가 필요합니다.")
        if (!RecurrenceExpander.isOccurrence(event, recurrenceId)) {
            throw BusinessException(ErrorCode.OCCURRENCE_NOT_FOUND)
        }
        val exception = eventExceptionRepository.findFirstByEventIdAndRecurrenceIdOrderByIdAsc(requireNotNull(event.id), recurrenceId)
        if (exception?.type == EventExceptionType.DELETED) {
            throw BusinessException(ErrorCode.OCCURRENCE_NOT_FOUND)
        }
        return recurrenceId to exception
    }

    private fun requireScope(scope: RecurrenceScope?): RecurrenceScope =
        scope ?: throw BusinessException(ErrorCode.RECURRENCE_SCOPE_REQUIRED)

    // 회차가 생기는 방식. 전체 수정에서 이 값이 바뀌면 기존 예외를 지운다
    private fun Event.schedule(): List<Any?> = listOf(startAt, endAt, isAllDay, timezone, rrule)

    // 생성과 이 일정과 이후 수정이 같이 쓰는 일정 만들기 (검증 포함, 저장은 하지 않음)
    // timezone: 검증한 시간대 또는 null (null이면 기본값 UTC로 저장)
    private fun buildEvent(
        calendar: Calendar,
        userId: Long,
        categoryId: Long?,
        title: String,
        description: String?,
        location: String?,
        isAllDay: Boolean,
        startAt: OffsetDateTime?,
        endAt: OffsetDateTime?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        timezone: String?,
        rrule: String?,
    ): Event {
        val period = toPeriod(isAllDay, startAt, endAt, startDate, endDate)
        val recurrence = resolveRecurrence(rrule, isAllDay, period.first, period.second, timezone)
        val event = Event.create(
            calendar = calendar,
            category = findCategoryIn(calendar, categoryId),
            user = userRepository.getReferenceById(userId),
            title = title,
            description = description,
            location = location,
            startAt = period.first,
            endAt = period.second,
            isAllDay = isAllDay,
        )
        event.changeRecurrence(timezone ?: Event.DEFAULT_TIMEZONE, recurrence?.rrule, recurrence?.recurUntil)
        return event
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

    // 요청에 시간대가 있으면 검증한 이름, 없으면 null
    private fun givenTimezone(timezone: String?): String? =
        if (timezone.isNullOrBlank()) null else validTimezone(timezone)

    // 반복 규칙이 없으면 null (반복 없는 일정)
    private fun resolveRecurrence(
        rrule: String?,
        isAllDay: Boolean,
        startAt: Instant,
        endAt: Instant,
        timezone: String?,
    ): RecurrenceRule.Result? =
        if (rrule.isNullOrBlank()) null else RecurrenceRule.resolve(rrule, isAllDay, startAt, endAt, timezone)

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
