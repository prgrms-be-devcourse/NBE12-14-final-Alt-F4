package com.team4.live_calendar.domain.event.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.calendar.service.CalendarService
import com.team4.live_calendar.domain.event.dto.EventCreateRequest
import com.team4.live_calendar.domain.event.dto.EventResponse
import com.team4.live_calendar.domain.event.dto.EventUpdateRequest
import com.team4.live_calendar.domain.event.dto.RecurrenceScope
import com.team4.live_calendar.domain.event.entity.EventExceptionType
import com.team4.live_calendar.domain.event.repository.EventExceptionRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

// 같은 일정에 동시 요청이 오면 일정 줄 잠금으로 차례로 처리되는지 본다
// 테스트 트랜잭션 없이 실제로 커밋한다. 요청1이 커밋 전에 멈춘 사이 요청2를 보내고, 요청2가 기다리는 것을 확인한 뒤 요청1을 푼다
@SpringBootTest
class EventLockTest(
    @Autowired private val eventService: EventService,
    @Autowired private val calendarService: CalendarService,
    @Autowired private val eventRepository: EventRepository,
    @Autowired private val eventExceptionRepository: EventExceptionRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val calendarRepository: CalendarRepository,
    @Autowired private val calendarGroupRepository: CalendarGroupRepository,
    @Autowired private val subscribeRepository: SubscribeRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired transactionManager: PlatformTransactionManager,
) {

    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var pool: ExecutorService

    private lateinit var owner: User
    private lateinit var calendar: Calendar
    private lateinit var group: CalendarGroup
    private lateinit var subscribe: Subscribe

    @BeforeEach
    fun setUp() {
        // 캘린더 삭제 테스트는 막는 쪽까지 셋을 쓴다
        pool = Executors.newFixedThreadPool(3)
        owner = userRepository.save(User.signUp("lock-${UUID.randomUUID()}@mail.com", "{bcrypt}hash", "잠금"))
        calendar = calendarRepository.save(Calendar.create("잠금 테스트", null, null))
        group = calendarGroupRepository.save(CalendarGroup.create(owner, "그룹", 0))
        subscribe = subscribeRepository.save(Subscribe.create(owner, group, calendar, CalendarRole.OWNER, null))
    }

    // 스레드가 끝난 뒤 지운다. 커밋된 데이터가 남으면 전체 개수를 세는 다른 테스트가 깨진다
    // 이미 지운 캘린더(404)가 아닌 실패는 데이터가 남을 수 있어 경고를 남긴다 (테스트는 깨지 않는다)
    @AfterEach
    fun cleanUp() {
        pool.shutdownNow()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        runCatching { calendarService.delete(owner.id!!, calendar.id!!) }.onFailure { e ->
            if ((e as? BusinessException)?.errorCode != ErrorCode.CALENDAR_NOT_FOUND) {
                log.warn("테스트 정리: 캘린더 {} 삭제 실패, 데이터가 남을 수 있다", calendar.id, e)
            }
        }
        runCatching { subscribeRepository.delete(subscribe) }
        runCatching { calendarGroupRepository.delete(group) }
        runCatching { calendarRepository.delete(calendar) }
        runCatching { userRepository.delete(owner) }.onFailure { e ->
            log.warn("테스트 정리: 회원 {} 삭제 실패, 데이터가 남을 수 있다", owner.id, e)
        }
    }

    private fun kst(dateTime: String) = OffsetDateTime.parse("${dateTime}:00+09:00")

    private fun createSingle() = eventService.create(
        owner.id!!,
        EventCreateRequest(
            calendarId = calendar.id!!,
            categoryId = null,
            title = "팀 회의",
            description = null,
            location = null,
            startAt = kst("2026-11-18T14:00"),
            endAt = kst("2026-11-18T15:00"),
        ),
    )

    // 매주 월요일 한국 08:00~09:00 반복
    private fun createWeekly() = eventService.create(
        owner.id!!,
        EventCreateRequest(
            calendarId = calendar.id!!,
            categoryId = null,
            title = "주간 회의",
            description = null,
            location = null,
            startAt = kst("2026-11-16T08:00"),
            endAt = kst("2026-11-16T09:00"),
            timezone = "Asia/Seoul",
            rrule = "FREQ=WEEKLY;BYDAY=MO",
        ),
    )

    private fun request(title: String, start: String, end: String, version: Long, rrule: String? = "FREQ=WEEKLY;BYDAY=MO") =
        EventUpdateRequest(
            categoryId = null,
            title = title,
            description = null,
            location = null,
            startAt = kst(start),
            endAt = kst(end),
            rrule = rrule,
            version = version,
        )

    // 11/23 회차만 10시로
    private fun updateThis(event: EventResponse, version: Long) = eventService.update(
        owner.id!!, event.id, request("옮긴 회의", "2026-11-23T10:00", "2026-11-23T11:00", version),
        RecurrenceScope.THIS, kst("2026-11-23T08:00"),
    )

    // 요청1은 트랜잭션 안에서 action을 하고 커밋 전에 멈춘다. 그사이 요청2를 보내 기다리는지 확인한 뒤 요청1을 푼다
    private fun <A, B> race(first: () -> A, second: () -> B): Pair<Result<A>, Result<B>> {
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val future1: Future<A> = pool.submit<A> {
            transactionTemplate.execute {
                first().also {
                    holding.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        }
        val future2: Future<B>
        try {
            assertTrue(holding.await(10, TimeUnit.SECONDS), "요청1이 잠금을 잡지 못했다")
            future2 = pool.submit<B> { second() }
            awaitWaitingQueries(1)
        } finally {
            release.countDown()
        }
        return runCatching { future1.get(10, TimeUnit.SECONDS) } to runCatching { future2.get(10, TimeUnit.SECONDS) }
    }

    // 다른 연결이 일정 쪽 쿼리(events, event_exceptions, event_options)에서 기다리는 중인지
    // innodb_trx·data_lock_waits는 Testcontainers test 계정에 권한이 없어 processlist로 본다
    private fun awaitWaitingQueries(count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val waiting = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.processlist
                where id <> connection_id() and command = 'Query' and info like '%event%'
                """,
                Long::class.java,
            )
            if (waiting!! >= count) return
            Thread.sleep(20)
        }
        fail("기다리는 쿼리가 ${count}개가 되지 않았다")
    }

    private fun Result<*>.cause(): Throwable? = exceptionOrNull()?.let { it.cause ?: it }

    @Test
    fun `같은 일정을 같은 version으로 동시에 고치면 뒤 요청은 409다`() {
        // given
        val event = createSingle()

        // when
        val (first, second) = race(
            { eventService.update(owner.id!!, event.id, request("요청1의 제목", "2026-11-18T14:00", "2026-11-18T16:00", version = 0, rrule = null)) },
            { eventService.update(owner.id!!, event.id, request("요청2의 제목", "2026-11-18T14:00", "2026-11-18T17:00", version = 0, rrule = null)) },
        )

        // then
        assertTrue(first.isSuccess, "요청1 실패: ${first.cause()}")
        assertEquals(ErrorCode.EVENT_VERSION_CONFLICT, (second.cause() as? BusinessException)?.errorCode, "요청2 결과: ${second.cause()}")
        val saved = eventRepository.findById(event.id).get()
        assertEquals("요청1의 제목", saved.title)
        assertEquals(1, saved.version)
    }

    @Test
    fun `같은 회차를 동시에 고치고 지우면 둘 다 성공하고 예외는 DELETED 한 줄이다`() {
        // given
        val event = createWeekly()

        // when
        val (first, second) = race(
            { updateThis(event, version = 0) },
            { eventService.delete(owner.id!!, event.id, RecurrenceScope.THIS, kst("2026-11-23T08:00")) },
        )

        // then: 요청2는 잠금 뒤 요청1이 만든 예외를 보고 DELETED로 바꾼다 (중복 키 500 없음)
        assertTrue(first.isSuccess, "요청1 실패: ${first.cause()}")
        assertTrue(second.isSuccess, "요청2 실패: ${second.cause()}")
        val exceptions = eventExceptionRepository.findAll().filter { it.event.id == event.id }
        assertEquals(listOf(EventExceptionType.DELETED), exceptions.map { it.type })
        assertEquals(2, eventRepository.findById(event.id).get().version)
    }

    @Test
    fun `캘린더 삭제가 중간에 멈춘 사이 회차를 고쳐도 데드락이 없다`() {
        // given: 숨김 설정 줄(event_options)을 만들고, 막는 쪽이 그 줄을 잠가 캘린더 삭제를 중간에 멈추게 한다
        // (회차 수정이 먼저 잠그고 캘린더 삭제가 기다리는 모양은 잠금이 없어도 통과해서 데드락을 잡지 못한다)
        val event = createWeekly()
        eventService.hide(owner.id!!, event.id)
        val blockerHolding = CountDownLatch(1)
        val blockerRelease = CountDownLatch(1)
        val blocker = pool.submit {
            transactionTemplate.execute {
                jdbcTemplate.queryForList("select id from event_options where event_id = ? for update", event.id)
                blockerHolding.countDown()
                blockerRelease.await(10, TimeUnit.SECONDS)
            }
        }
        val future1: Future<Unit>
        val future2: Future<EventResponse>
        try {
            assertTrue(blockerHolding.await(10, TimeUnit.SECONDS), "막는 쪽이 잠금을 잡지 못했다")
            // 요청1: 캘린더 삭제. 일정 줄을 잠근 채 event_options 삭제에서 멈춘다
            future1 = pool.submit<Unit> { calendarService.delete(owner.id!!, calendar.id!!) }
            awaitWaitingQueries(1)
            // 요청2: 같은 캘린더 일정의 회차 수정. 일정 줄 잠금에서 기다린다
            future2 = pool.submit<EventResponse> { updateThis(event, version = 0) }
            awaitWaitingQueries(2)
        } finally {
            blockerRelease.countDown()
        }
        blocker.get(10, TimeUnit.SECONDS)
        val first = runCatching { future1.get(15, TimeUnit.SECONDS) }
        val second = runCatching { future2.get(15, TimeUnit.SECONDS) }

        // then: 캘린더 삭제는 성공, 회차 수정은 잠금 뒤 일정이 이미 지워져 404 (데드락·500 아님)
        // 잠금이 없으면 요청2가 일정 줄을 잡고 event_exceptions에 넣다가 요청1과 서로 기다려 데드락이 난다
        assertTrue(first.isSuccess, "요청1(캘린더 삭제) 실패: ${first.cause()}")
        assertEquals(ErrorCode.EVENT_NOT_FOUND, (second.cause() as? BusinessException)?.errorCode, "요청2(회차 수정) 결과: ${second.cause()}")
        assertFalse(calendarRepository.existsById(calendar.id!!))
    }
}
