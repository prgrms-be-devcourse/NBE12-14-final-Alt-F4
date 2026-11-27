package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.recurrence.RecurrenceExpander
import com.team4.live_calendar.domain.event.recurrence.ViewRange
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// 주어진 일정들을 [from, to) 기간의 실제 회차로 펼친다
// 반복 일정은 지운 회차를 빼고, 바꾼 회차는 바뀐 시간으로 넣는다 (EventService.findInRange와 같은 규칙)
@Component
class EventOccurrenceReader(
    private val eventExceptionRepository: EventExceptionRepository,
) {

    data class Occurrence(val event: Event, val startAt: Instant, val endAt: Instant)

    // 종일 일정은 zone의 날짜로 기간과 비교한다
    fun read(events: Collection<Event>, from: Instant, to: Instant, zone: ZoneId): List<Occurrence> {
        val range = ViewRange(from, to, zone)
        val singles = events
            .filter { it.rrule == null && range.overlaps(it.startAt, it.endAt, it.isAllDay) }
            .map { Occurrence(it, it.startAt, it.endAt) }

        val recurring = events.filter { it.rrule != null }
        if (recurring.isEmpty()) return singles.sortedBy { it.startAt }

        val expanded = recurring.map { it to RecurrenceExpander.expand(it, range) }
        val starts = expanded.flatMap { (_, occurrences) -> occurrences.map { it.startAt } }
        val exceptionKeys = if (starts.isEmpty()) {
            emptySet()
        } else {
            eventExceptionRepository.findAllInRecurrenceRange(
                recurring.map { requireNotNull(it.id) },
                starts.min(),
                starts.max()
            )
                .map { it.event.id to it.recurrenceId }
                .toSet()
        }
        val occurrences = expanded.flatMap { (event, list) ->
            list.filter { (event.id to it.startAt) !in exceptionKeys }
                .map { Occurrence(event, it.startAt, it.endAt) }
        }

        // 다른 날로 옮긴 회차도 나오도록 바뀐 시간으로 따로 찾는다 (종일 일정 때문에 앞뒤 하루 여유)
        val recurringIds = recurring.map { it.id }.toSet()
        val modified = eventExceptionRepository
            .findModifiedOverlapping(
                recurring.map { requireNotNull(it.calendar.id) }.distinct(),
                from.minus(DB_MARGIN),
                to.plus(DB_MARGIN)
            )
            .filter { it.event.id in recurringIds }
            .sortedBy { it.id }
            .distinctBy { it.event.id to it.recurrenceId }
            .filter { range.overlaps(requireNotNull(it.startAt), requireNotNull(it.endAt), it.event.isAllDay) }
            .map { Occurrence(it.event, requireNotNull(it.startAt), requireNotNull(it.endAt)) }

        return (singles + occurrences + modified).sortedBy { it.startAt }
    }

    companion object {
        private val DB_MARGIN: Duration = Duration.ofDays(1)
    }
}
