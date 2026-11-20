package com.team4.live_calendar.domain.event.recurrence

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewRangeTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val newYork = ZoneId.of("America/New_York")

    private fun day(date: String, zone: ZoneId) =
        ViewRange(
            LocalDate.parse(date).atStartOfDay(zone).toInstant(),
            LocalDate.parse(date).plusDays(1).atStartOfDay(zone).toInstant(),
            zone,
        )

    // 종일 일정 [startDate, endDate] (UTC 자정 저장)
    private fun ViewRange.overlapsAllDay(startDate: String, endDate: String = startDate) =
        overlaps(
            LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toInstant(),
            LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            true,
        )

    @Test
    fun `한국 18일 하루 조회에는 18일 종일 일정만 나온다`() {
        val range = day("2026-11-18", seoul)
        assertFalse(range.overlapsAllDay("2026-11-17"))
        assertTrue(range.overlapsAllDay("2026-11-18"))
        assertFalse(range.overlapsAllDay("2026-11-19"))
    }

    @Test
    fun `뉴욕 18일 하루 조회에는 18일 종일 일정만 나온다`() {
        val range = day("2026-11-18", newYork)
        assertFalse(range.overlapsAllDay("2026-11-17"))
        assertTrue(range.overlapsAllDay("2026-11-18"))
        assertFalse(range.overlapsAllDay("2026-11-19"))
    }

    @Test
    fun `한국 11월 한 달 조회에는 10월 31일과 12월 1일이 빠진다`() {
        val range = ViewRange(
            LocalDate.parse("2026-11-01").atStartOfDay(seoul).toInstant(),
            LocalDate.parse("2026-12-01").atStartOfDay(seoul).toInstant(),
            seoul,
        )
        assertFalse(range.overlapsAllDay("2026-10-31"))
        assertTrue(range.overlapsAllDay("2026-11-01"))
        assertTrue(range.overlapsAllDay("2026-11-30"))
        assertFalse(range.overlapsAllDay("2026-12-01"))
    }

    @Test
    fun `17일부터 18일까지 이틀짜리는 한국 18일 하루 조회에 나온다`() {
        assertTrue(day("2026-11-18", seoul).overlapsAllDay("2026-11-17", "2026-11-18"))
    }

    @Test
    fun `자정이 아닌 끝 시각은 그날까지 포함한다`() {
        // given: 한국 18일 00:00 ~ 18일 12:00
        val range = ViewRange(
            Instant.parse("2026-11-17T15:00:00Z"),
            Instant.parse("2026-11-18T03:00:00Z"),
            seoul,
        )

        // then
        assertEquals(LocalDate.parse("2026-11-19"), range.toDateExclusive)
        assertTrue(range.overlapsAllDay("2026-11-18"))
        assertFalse(range.overlapsAllDay("2026-11-19"))
    }
}
