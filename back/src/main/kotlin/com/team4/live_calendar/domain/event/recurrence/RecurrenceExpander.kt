package com.team4.live_calendar.domain.event.recurrence

import com.team4.live_calendar.domain.event.entity.Event
import net.fortuna.ical4j.model.Recur
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// 반복 일정을 조회 범위 안의 회차로 펼친다
object RecurrenceExpander {

    data class Occurrence(val startAt: Instant, val endAt: Instant)

    // 범위와 겹치는 회차를 시작 시간 순으로 반환
    fun expand(event: Event, range: ViewRange): List<Occurrence> {
        val rrule = requireNotNull(event.rrule)
        val length = Duration.between(event.startAt, event.endAt)

        // 범위 시작 전에 시작해서 걸쳐 있는 회차도 포함하도록 일정 길이만큼 앞에서부터 계산한다
        val starts = if (event.isAllDay) {
            // 종일 일정: 날짜로 계산
            val seed = event.startAt.utcDate()
            val days = ChronoUnit.DAYS.between(seed, event.endAt.utcDate())
            Recur<LocalDate>(rrule)
                .getDates(seed, range.fromDate.minusDays(days), range.toDateExclusive)
                .map { it.atStartOfDay(ZoneOffset.UTC).toInstant() }
        } else {
            // 시간 일정: 일정 시간대의 현지 시각으로 계산 (서머타임이 있어도 현지 시각 유지)
            val zone = ZoneId.of(event.timezone)
            Recur<ZonedDateTime>(rrule)
                .getDates(event.startAt.atZone(zone), range.from.minus(length).atZone(zone), range.to.atZone(zone))
                .map { it.toInstant() }
        }

        // ical4j는 범위 끝과 같은 회차도 돌려주므로 겹침 규칙으로 다시 거른다
        return starts
            .map { Occurrence(it, it.plus(length)) }
            .filter { range.overlaps(it.startAt, it.endAt, event.isAllDay) }
    }
}
