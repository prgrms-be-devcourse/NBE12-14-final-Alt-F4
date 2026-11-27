package com.team4.live_calendar.global.websocket

import com.team4.live_calendar.domain.calendar.dto.CalendarDisplayRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupMoveRequest
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.service.CalendarService
import com.team4.live_calendar.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.user.SimpUserRegistry
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// 표시 신호가 같은 사람의 연결에만 가는지 실제 STOMP 연결로 본다
class DisplaySyncStompTest : StompTestSupport() {

    @Autowired private lateinit var calendarService: CalendarService
    @Autowired private lateinit var userRegistry: SimpUserRegistry

    @Test
    fun `표시 설정을 바꾸면 내 모든 연결이 받고 다른 사람은 받지 않는다`() {
        // given: 내 연결 두 개(예: PC와 휴대폰), 다른 사람 연결 하나
        val me = newUser()
        val other = newUser()
        val calendar = newCalendar().also { join(me, it, CalendarRole.OWNER) }
        val pc = connect(tokenOf(me)).subscribeTo(DISPLAY)
        val phone = connect(tokenOf(me)).subscribeTo(DISPLAY)
        val othersReceived = connect(tokenOf(other)).subscribeTo(DISPLAY)
        assertReceivesDisplay(me, pc)
        assertReceivesDisplay(me, phone)
        assertReceivesDisplay(other, othersReceived)

        // when (테스트 트랜잭션 없이 불러서 커밋된다)
        calendarService.changeDisplay(me.id!!, calendar.id!!, CalendarDisplayRequest(isVisible = false))

        // then
        listOf(pc, phone).forEach { received ->
            val signal = awaitSignal(received, SyncType.DISPLAY_CHANGED)
            assertFalse(signal.containsKey("userId"))
        }
        // 한 요청에 한 번만 온다
        listOf(pc, phone).forEach { assertNoRealSignal(it) }
        assertNoRealSignal(othersReceived)
    }

    @Test
    fun `표시 이벤트를 발행한 뒤 실패한 요청은 롤백되어 신호를 보내지 않는다`() {
        // given
        val me = newUser()
        val calendar = newCalendar().also { join(me, it, CalendarRole.OWNER) }
        val received = connect(tokenOf(me)).subscribeTo(DISPLAY)
        assertReceivesDisplay(me, received)

        // when: 없는 그룹으로 옮기면 발행한 뒤 그룹 확인에서 404로 실패한다 (남의 그룹과 같은 404)
        assertThrows<BusinessException> {
            calendarService.moveGroup(me.id!!, calendar.id!!, CalendarGroupMoveRequest(groupId = Long.MAX_VALUE))
        }

        // then
        assertNoRealSignal(received)
    }

    @Test
    fun `표시 주소 말고 큐를 직접 구독하거나 와일드카드로 구독하면 남의 표시 신호를 받지 못한다`() {
        // given: 피해자가 표시 주소를 구독하고 받는 것까지 확인
        val victim = newUser()
        val attacker = newUser()
        val victimReceived = connect(tokenOf(victim)).subscribeTo(DISPLAY)
        assertReceivesDisplay(victim, victimReceived)
        // Spring이 바꾼 피해자 연결 전용 주소 (예: /queue/display-user{세션 id})
        val victimSessionId = userRegistry.getUser(victim.id.toString())!!.sessions.single().id
        val attackerSession = connect(tokenOf(attacker))

        // when: 거절될 주소들을 먼저 구독한 뒤 내 표시 주소를 구독한다
        val rejected = listOf(
            "/queue/**",
            "/queue/display-user*",
            "/queue/display-user$victimSessionId",
            "/user/queue/*",
            // 받아들여도 Spring이 공격자 자신의 연결 주소로 바꿔서 안전하다. 허용 규칙(정확히 두 주소)을 보여 주려고 둔다
            "/user/${victim.id}/queue/display",
        ).map { it to attackerSession.subscribeTo(it) }
        val attackerOwn = attackerSession.subscribeTo(DISPLAY)

        // then: 같은 연결의 내 표시 구독은 받는다 (연결이 끊기지 않았다)
        assertReceivesDisplay(attacker, attackerOwn)
        // 피해자에게 보내도 거절된 구독에는 아무것도 오지 않는다 (공격자 자신의 TEST도 오면 안 된다)
        sendDisplaySignal(victim)
        rejected.forEach { (destination, queue) ->
            assertEquals(null, queue.poll(500, TimeUnit.MILLISECONDS), destination)
        }
    }

    @Test
    fun `클라이언트가 남의 표시 주소로 보내면 그 사람에게 가지 않는다`() {
        // given
        val victim = newUser()
        val attacker = newUser()
        val victimReceived = connect(tokenOf(victim)).subscribeTo(DISPLAY)
        assertReceivesDisplay(victim, victimReceived)
        val attackerSession = connect(tokenOf(attacker))

        // when
        attackerSession.send("/user/${victim.id}/queue/display", "가짜 신호".toByteArray())

        // then: 구독 확인용 TEST가 늦게 올 수 있어서, 가짜 신호가 왔는지만 본다
        Thread.sleep(500)
        assertFalse(victimReceived.contains("가짜 신호"))
    }

    companion object {
        private const val DISPLAY = SyncSignalListener.USER_DISPLAY_QUEUE
    }
}
