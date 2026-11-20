package com.team4.live_calendar.domain.event.recurrence

import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurrenceRuleTest {

    // 2026-11-16은 월요일. 한국 오전 8시 = UTC 일요일 23시
    private val mondayMorning = OffsetDateTime.parse("2026-11-16T08:00:00+09:00").toInstant()

    private fun timed(
        rule: String,
        timezone: String? = "Asia/Seoul",
        startAt: Instant = mondayMorning,
        hours: Long = 1,
    ) = RecurrenceRule.resolve(rule, false, startAt, startAt.plusSeconds(hours * 3600), timezone)

    private fun allDay(rule: String, startDate: String = "2026-11-16", days: Long = 1) =
        LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toInstant().let {
            RecurrenceRule.resolve(rule, true, it, it.plusSeconds(days * 86400), null)
        }

    private fun assertInvalid(block: () -> Unit) {
        val e = assertThrows<BusinessException> { block() }
        assertEquals(ErrorCode.INVALID_RECURRENCE, e.errorCode)
    }

    // ===== 입력 정리 =====

    @Test
    fun `앞뒤 공백과 RRULE 접두사를 지우고 정해진 순서로 정리한다`() {
        // when
        val result = timed("  rrule:BYDAY=MO;COUNT=3;FREQ=WEEKLY  ")

        // then
        assertEquals("FREQ=WEEKLY;COUNT=3;BYDAY=MO", result.rrule)
    }

    @Test
    fun `소문자 규칙도 대문자로 바꿔 저장한다`() {
        // when
        val result = timed("freq=weekly;until=20261231t145959z")

        // then
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T145959Z", result.rrule)
    }

    // ===== 검증 =====

    @ParameterizedTest
    @ValueSource(
        strings = [
            "HELLO",
            "FREQ=WEEKLY;COUNT=abc",
            "FREQ=DAILY ;COUNT=3",
            "FREQ=HOURLY",
            "FREQ=MINUTELY",
            "FREQ=DAILY;BYHOUR=9",
            "FREQ=DAILY;BYMINUTE=0",
            "FREQ=DAILY;BYSECOND=0",
            "FREQ=DAILY;COUNT=3;UNTIL=20261231T145959Z",
            "FREQ=DAILY;COUNT=0",
            "FREQ=DAILY;COUNT=1001",
            "FREQ=DAILY;INTERVAL=0",
            "FREQ=DAILY;INTERVAL=-2",
            "FREQ=DAILY;UNTIL=20261231",
            "FREQ=DAILY;UNTIL=20261399T999999Z",
            "FREQ=DAILY;UNTIL=20261101T000000Z",
            "FREQ=WEEKLY;BYDAY=WE",
        ],
    )
    fun `잘못된 시간 일정 반복 규칙은 400 INVALID_RECURRENCE`(rule: String) {
        assertInvalid { timed(rule) }
    }

    @Test
    fun `정리 후 255자를 넘으면 400 INVALID_RECURRENCE`() {
        // given
        val rule = "FREQ=DAILY;X-NOTE=" + "A".repeat(255 - 18 + 1)

        // then
        assertEquals(256, rule.length)
        assertInvalid { timed(rule) }
    }

    @Test
    fun `종일 일정의 UNTIL이 날짜시각이면 400 INVALID_RECURRENCE`() {
        assertInvalid { allDay("FREQ=DAILY;UNTIL=20261231T000000Z") }
    }

    @Test
    fun `종일 일정 반복은 날짜 UNTIL을 쓰고 시간대가 없어도 된다`() {
        // when
        val result = allDay("FREQ=DAILY;UNTIL=20261120")

        // then
        assertEquals("FREQ=DAILY;UNTIL=20261120", result.rrule)
        assertEquals(Instant.parse("2026-11-21T00:00:00Z"), result.recurUntil)
    }

    @Test
    fun `COUNT는 1과 1000까지 허용한다`() {
        timed("FREQ=DAILY;COUNT=1")
        timed("FREQ=DAILY;COUNT=1000")
    }

    // ===== 시간대 =====

    @Test
    fun `시간 일정 반복에 시간대가 없으면 400 INVALID_RECURRENCE`() {
        assertInvalid { timed("FREQ=WEEKLY", timezone = null) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["+09:00", "GMT+9", "UTC+09:00", "Etc/GMT-9"])
    fun `시간 일정 반복의 시간대가 고정 시차면 400 INVALID_RECURRENCE`(timezone: String) {
        assertInvalid { timed("FREQ=WEEKLY", timezone = timezone) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Asia/Seoul", "UTC", "GMT"])
    fun `시간 일정 반복의 시간대는 지역 이름과 UTC를 허용한다`(timezone: String) {
        timed("FREQ=WEEKLY", timezone = timezone)
    }

    @Test
    fun `한국 월요일 오전 8시 시작에 BYDAY=MO는 한국 시간대 기준이라 통과한다`() {
        // when: UTC로 보면 일요일이지만 일정 시간대(Asia/Seoul) 기준으로 검사한다
        val result = timed("FREQ=WEEKLY;BYDAY=MO")

        // then
        assertEquals("FREQ=WEEKLY;BYDAY=MO", result.rrule)
    }

    // ===== recurUntil =====

    @Test
    fun `COUNT가 있으면 마지막 회차의 끝이 recurUntil이다`() {
        // when: 11/16, 11/23, 11/30 한국 08:00~09:00
        val result = timed("FREQ=WEEKLY;COUNT=3")

        // then
        assertEquals(Instant.parse("2026-11-30T00:00:00Z"), result.recurUntil)
    }

    @Test
    fun `UNTIL과 같은 시각의 회차까지 포함해 recurUntil을 계산한다`() {
        // when: UNTIL = 11/19 한국 08:00 (회차 시작과 같은 시각)
        val result = timed("FREQ=DAILY;UNTIL=20261118T230000Z")

        // then: 11/19 08:00~09:00 회차의 끝
        assertEquals(Instant.parse("2026-11-19T00:00:00Z"), result.recurUntil)
    }

    @Test
    fun `종일 일정 COUNT는 마지막 회차의 다음 날 자정이 recurUntil이다`() {
        // when: 2일짜리, 11/16 · 11/17 · 11/18 시작
        val result = allDay("FREQ=DAILY;COUNT=3", days = 2)

        // then: 11/18~11/19 회차의 끝
        assertEquals(Instant.parse("2026-11-20T00:00:00Z"), result.recurUntil)
    }

    @Test
    fun `끝이 없는 반복은 recurUntil이 null이다`() {
        assertNull(timed("FREQ=WEEKLY").recurUntil)
    }
}
