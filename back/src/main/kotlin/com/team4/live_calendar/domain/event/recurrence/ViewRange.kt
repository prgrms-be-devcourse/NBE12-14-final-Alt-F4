package com.team4.live_calendar.domain.event.recurrence

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// 기간 조회 범위 [from, to)
// 종일 일정은 UTC 자정으로 저장되어 있어서, 보는 사람 시간대의 날짜 [fromDate, toDateExclusive)로 비교한다
class ViewRange(
    val from: Instant,
    val to: Instant,
    viewZone: ZoneId,
) {
    // from이 속한 날짜 (내림)
    val fromDate: LocalDate = from.atZone(viewZone).toLocalDate()

    // to가 자정이면 그 날짜, 아니면 다음 날 (올림)
    val toDateExclusive: LocalDate = to.atZone(viewZone).let {
        if (it.toLocalTime() == LocalTime.MIDNIGHT) it.toLocalDate() else it.toLocalDate().plusDays(1)
    }

    fun overlaps(startAt: Instant, endAt: Instant, isAllDay: Boolean): Boolean =
        if (isAllDay) {
            startAt.utcDate() < toDateExclusive && endAt.utcDate() > fromDate
        } else {
            startAt < to && endAt > from
        }
}

internal fun Instant.utcDate(): LocalDate = atOffset(ZoneOffset.UTC).toLocalDate()
