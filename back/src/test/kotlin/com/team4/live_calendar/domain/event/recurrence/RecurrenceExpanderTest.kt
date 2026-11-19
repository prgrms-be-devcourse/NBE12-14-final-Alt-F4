package com.team4.live_calendar.domain.event.recurrence

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.user.entity.User
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurrenceExpanderTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val newYork = ZoneId.of("America/New_York")
    private val calendar = Calendar.create("팀 캘린더", null, null)
    private val user = User.signUp("owner@mail.com", "{bcrypt}hash", "소유자")

    private fun timedEvent(start: String, zone: ZoneId, rrule: String, length: Duration = Duration.ofHours(1)): Event {
        val startAt = LocalDateTime.parse(start).atZone(zone).toInstant()
        return Event.create(
            calendar, null, user, "반복", null, null, startAt, startAt.plus(length),
            timezone = zone.id, rrule = rrule,
        )
    }

    private fun allDayEvent(startDate: String, days: Long, rrule: String): Event {
        val startAt = LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toInstant()
        return Event.create(
            calendar, null, user, "종일 반복", null, null, startAt, startAt.plus(Duration.ofDays(days)),
            isAllDay = true, rrule = rrule,
        )
    }

    // zone 기준 날짜 [fromDate, toDate) 범위
    private fun range(fromDate: String, toDate: String, zone: ZoneId) =
        ViewRange(
            LocalDate.parse(fromDate).atStartOfDay(zone).toInstant(),
            LocalDate.parse(toDate).atStartOfDay(zone).toInstant(),
            zone,
        )

    private fun localDates(occurrences: List<RecurrenceExpander.Occurrence>, zone: ZoneId) =
        occurrences.map { it.startAt.atZone(zone).toLocalDate().toString() }

    private fun utcDates(occurrences: List<RecurrenceExpander.Occurrence>) =
        occurrences.map { it.startAt.utcDate().toString() }

    @Test
    fun `격주 월요일과 수요일 반복을 펼친다`() {
        // given: 2026-11-02 월요일 시작
        val event = timedEvent("2026-11-02T09:00", seoul, "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-01", "2026-12-01", seoul))

        // then
        assertEquals(listOf("2026-11-02", "2026-11-04", "2026-11-16", "2026-11-18", "2026-11-30"), localDates(result, seoul))
    }

    @Test
    fun `매월 둘째 화요일 반복을 펼친다`() {
        // given
        val event = timedEvent("2026-11-10T10:00", seoul, "FREQ=MONTHLY;BYDAY=2TU")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-01", "2027-02-01", seoul))

        // then
        assertEquals(listOf("2026-11-10", "2026-12-08", "2027-01-12"), localDates(result, seoul))
    }

    @Test
    fun `매월 마지막 날 반복을 펼친다`() {
        // given
        val event = allDayEvent("2026-11-30", 1, "FREQ=MONTHLY;BYMONTHDAY=-1")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-01", "2027-03-01", ZoneOffset.UTC))

        // then
        assertEquals(listOf("2026-11-30", "2026-12-31", "2027-01-31", "2027-02-28"), utcDates(result))
    }

    @Test
    fun `매월 31일 반복은 31일이 없는 달을 건너뛴다`() {
        // given
        val event = timedEvent("2026-10-31T09:00", seoul, "FREQ=MONTHLY")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-10-01", "2027-04-01", seoul))

        // then
        assertEquals(listOf("2026-10-31", "2026-12-31", "2027-01-31", "2027-03-31"), localDates(result, seoul))
    }

    @Test
    fun `여러 날짜짜리 종일 일정도 반복한다`() {
        // given: 11/16~11/17 이틀짜리, 매주
        val event = allDayEvent("2026-11-16", 2, "FREQ=WEEKLY")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-01", "2026-12-01", ZoneOffset.UTC))

        // then
        assertEquals(listOf("2026-11-16", "2026-11-23", "2026-11-30"), utcDates(result))
        assertTrue(result.all { Duration.between(it.startAt, it.endAt) == Duration.ofDays(2) })
    }

    @Test
    fun `서머타임이 끝나도 뉴욕 현지 10시를 유지한다`() {
        // given: 2026-11-01에 서머타임이 끝난다
        val event = timedEvent("2026-10-26T10:00", newYork, "FREQ=WEEKLY")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-10-25", "2026-11-08", newYork))

        // then: 현지 시각은 10시 그대로, UTC는 14시 → 15시
        assertEquals(listOf(LocalTime.of(10, 0), LocalTime.of(10, 0)), result.map { it.startAt.atZone(newYork).toLocalTime() })
        assertEquals(
            listOf(Instant.parse("2026-10-26T14:00:00Z"), Instant.parse("2026-11-02T15:00:00Z")),
            result.map { it.startAt },
        )
    }

    @Test
    fun `서머타임 시작으로 없는 시각은 한 시간 뒤로 밀린다`() {
        // given: 뉴욕 2026-03-08 02:00~03:00은 없는 시각
        val event = timedEvent("2026-03-07T02:30", newYork, "FREQ=DAILY;COUNT=3")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-03-01", "2026-04-01", newYork))

        // then
        assertEquals(
            listOf(LocalTime.of(2, 30), LocalTime.of(3, 30), LocalTime.of(2, 30)),
            result.map { it.startAt.atZone(newYork).toLocalTime() },
        )
    }

    @Test
    fun `한국 오전 8시 매주 월요일 반복은 한국 기준 월요일로 나온다`() {
        // given: UTC로는 일요일 23시
        val event = timedEvent("2026-11-16T08:00", seoul, "FREQ=WEEKLY;BYDAY=MO")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-01", "2026-12-01", seoul))

        // then
        assertEquals(listOf("2026-11-16", "2026-11-23", "2026-11-30"), localDates(result, seoul))
        assertTrue(result.all { it.startAt.atZone(seoul).dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun `기간 끝과 같은 시각에 시작하는 회차는 빠진다`() {
        // given
        val event = timedEvent("2026-11-16T09:00", seoul, "FREQ=DAILY")
        val from = LocalDateTime.parse("2026-11-17T00:00").atZone(seoul).toInstant()
        val to = LocalDateTime.parse("2026-11-18T09:00").atZone(seoul).toInstant()

        // when
        val result = RecurrenceExpander.expand(event, ViewRange(from, to, seoul))

        // then: 11/18 09:00 회차는 빠진다
        assertEquals(listOf("2026-11-17"), localDates(result, seoul))
    }

    @Test
    fun `기간 시작 전에 시작해서 걸친 시간 일정 회차도 포함한다`() {
        // given: 매일 23시 ~ 다음 날 02시
        val event = timedEvent("2026-11-16T23:00", seoul, "FREQ=DAILY", Duration.ofHours(3))

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-18", "2026-11-19", seoul))

        // then: 11/17 23시 회차(18일 02시까지)와 11/18 23시 회차
        assertEquals(listOf("2026-11-17", "2026-11-18"), localDates(result, seoul))
    }

    @Test
    fun `기간 시작 전에 시작해서 걸친 여러 날짜 종일 회차도 포함한다`() {
        // given: 11/16~11/18 사흘짜리, 매주
        val event = allDayEvent("2026-11-16", 3, "FREQ=WEEKLY")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-18", "2026-11-19", ZoneOffset.UTC))

        // then
        assertEquals(listOf("2026-11-16"), utcDates(result))
    }

    @Test
    fun `10년 전에 시작한 매일 반복도 조회 기간만 계산한다`() {
        // given
        val event = timedEvent("2016-11-01T09:00", seoul, "FREQ=DAILY")

        // when
        val result = RecurrenceExpander.expand(event, range("2026-11-01", "2026-12-01", seoul))

        // then
        assertEquals(30, result.size)
        assertEquals("2026-11-01", localDates(result, seoul).first())
        assertEquals("2026-11-30", localDates(result, seoul).last())
    }
}
