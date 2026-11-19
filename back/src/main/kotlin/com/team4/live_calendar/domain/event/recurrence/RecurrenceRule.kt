package com.team4.live_calendar.domain.event.recurrence

import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.transform.recurrence.Frequency
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

// 반복 규칙(RRULE) 입력 정리·검증과 마지막 회차(recurUntil) 계산
object RecurrenceRule {

    // rrule: 저장할 규칙 (ical4j가 정리한 문자열), recurUntil: 마지막 회차의 끝 (무한 반복이면 null)
    data class Result(val rrule: String, val recurUntil: Instant?)

    private const val MAX_LENGTH = 255
    private const val MAX_COUNT = 1000

    // COUNT·UNTIL이 있는 규칙의 마지막 회차를 찾을 때 계산할 최대 기간
    private const val SEARCH_YEARS = 200L

    private val ALLOWED_FREQUENCIES = setOf(Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY)

    // 하루에 여러 번 생기는 규칙을 막는다 (막지 않으면 93일에 회차가 13만 개 넘게 나올 수 있음)
    private val BANNED_PARTS = setOf("BYHOUR", "BYMINUTE", "BYSECOND")

    private val PREFIX = Regex("^RRULE:", RegexOption.IGNORE_CASE)
    private val TIMED_UNTIL = Regex("""\d{8}T\d{6}Z""")
    private val ALL_DAY_UNTIL = Regex("""\d{8}""")

    // 시간 일정: startAt·endAt은 UTC 순간, timezone은 요청에 들어온 시간대 (없으면 null)
    // 종일 일정: startAt·endAt은 UTC 자정, timezone은 쓰지 않음 (날짜로 계산)
    fun resolve(raw: String, isAllDay: Boolean, startAt: Instant, endAt: Instant, timezone: String?): Result {
        val rule = clean(raw)
        if (rule.length > MAX_LENGTH) invalid("반복 규칙은 ${MAX_LENGTH}자 이하여야 합니다.")
        val parts = partsOf(rule)
        checkUntilFormat(parts["UNTIL"], isAllDay)

        return if (isAllDay) {
            val seed = startAt.atOffset(ZoneOffset.UTC).toLocalDate()
            val days = ChronoUnit.DAYS.between(seed, endAt.atOffset(ZoneOffset.UTC).toLocalDate())
            val recur = parse<LocalDate>(rule)
            checkRule(recur, parts)
            checkSeed(recur, seed)
            val last = lastStart(recur, parts, seed, seed.plusYears(SEARCH_YEARS))
            Result(recur.toString(), last?.plusDays(days)?.atStartOfDay(ZoneOffset.UTC)?.toInstant())
        } else {
            val zone = regionZone(timezone)
            val seed = startAt.atZone(zone)
            val recur = parse<ZonedDateTime>(rule)
            checkRule(recur, parts)
            checkSeed(recur, seed)
            val last = lastStart(recur, parts, seed, seed.plusYears(SEARCH_YEARS))
            Result(recur.toString(), last?.toInstant()?.plus(Duration.between(startAt, endAt)))
        }
    }

    // 앞뒤 공백 제거 → RRULE: 접두사 제거 → 대문자 (ical4j는 소문자 날짜 값을 읽지 못함)
    private fun clean(raw: String): String = raw.trim().replace(PREFIX, "").uppercase()

    private fun partsOf(rule: String): Map<String, String> =
        rule.split(";").associate { part ->
            val (key, value) = part.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            key to value
        }

    // ical4j는 시간 일정에 날짜만 있는 UNTIL도 받아들이므로 직접 검사한다
    private fun checkUntilFormat(until: String?, isAllDay: Boolean) {
        if (until == null) return
        if (isAllDay && !ALL_DAY_UNTIL.matches(until)) {
            invalid("종일 일정의 UNTIL은 날짜(yyyyMMdd)여야 합니다.")
        }
        if (!isAllDay && !TIMED_UNTIL.matches(until)) {
            invalid("시간 일정의 UNTIL은 UTC 날짜시각(yyyyMMddTHHmmssZ)이어야 합니다.")
        }
    }

    // 파싱 중 나는 예외는 종류가 여러 가지라 모두 400으로 바꾼다
    private fun <T : Temporal> parse(rule: String): Recur<T> =
        try {
            Recur<T>(rule)
        } catch (e: RuntimeException) {
            invalid("반복 규칙을 읽을 수 없습니다.")
        }

    private fun checkRule(recur: Recur<*>, parts: Map<String, String>) {
        if (recur.frequency !in ALLOWED_FREQUENCIES) {
            invalid("반복 주기(FREQ)는 DAILY, WEEKLY, MONTHLY, YEARLY만 사용할 수 있습니다.")
        }
        if (parts.keys.any { it in BANNED_PARTS }) {
            invalid("BYHOUR, BYMINUTE, BYSECOND는 사용할 수 없습니다.")
        }
        if ("UNTIL" in parts && "COUNT" in parts) {
            invalid("UNTIL과 COUNT는 함께 사용할 수 없습니다.")
        }
        // ical4j는 COUNT=0을 무한 반복으로 계산한다
        if ("COUNT" in parts && recur.count !in 1..MAX_COUNT) {
            invalid("COUNT는 1 이상 ${MAX_COUNT} 이하여야 합니다.")
        }
        // ical4j는 0이나 음수 INTERVAL을 막지 않는다
        if ("INTERVAL" in parts && recur.interval < 1) {
            invalid("INTERVAL은 1 이상이어야 합니다.")
        }
    }

    // ical4j는 규칙과 안 맞는 시작일을 회차에서 빼 버리므로, 시작일이 첫 회차인지 확인한다
    private fun <T : Temporal> checkSeed(recur: Recur<T>, seed: T) {
        if (recur.getDates(seed, seed, seed).isEmpty()) {
            invalid("시작일이 반복 규칙의 첫 회차와 맞지 않습니다.")
        }
    }

    // 고정 시차(+09:00, GMT+9 등)는 서머타임을 계산할 수 없어 지역 이름만 허용한다 (UTC는 허용)
    private fun regionZone(timezone: String?): ZoneId {
        if (timezone == null) invalid("시간 일정을 반복하려면 timezone이 필요합니다.")
        val zone = try {
            ZoneId.of(timezone)
        } catch (e: DateTimeException) {
            invalid("올바르지 않은 시간대입니다: $timezone")
        }
        val normalized = zone.normalized()
        if (normalized is ZoneOffset && normalized != ZoneOffset.UTC) {
            invalid("반복 일정의 timezone은 지역 이름(예: Asia/Seoul)이어야 합니다.")
        }
        return zone
    }

    // COUNT·UNTIL이 있으면 마지막 회차의 시작, 무한 반복이면 null
    // 계산 기간(200년) 뒤에도 회차가 남아 있으면 끝이 없는 것처럼 null로 둔다
    private fun <T : Temporal> lastStart(recur: Recur<T>, parts: Map<String, String>, seed: T, searchEnd: T): T? {
        if ("COUNT" !in parts && "UNTIL" !in parts) return null
        if (recur.getNextDate(seed, searchEnd) != null) return null
        return recur.getDates(seed, seed, searchEnd).lastOrNull()
    }

    private fun invalid(message: String): Nothing =
        throw BusinessException(ErrorCode.INVALID_RECURRENCE, message)
}
