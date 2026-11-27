package com.team4.live_calendar.global.websocket

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

// 구독(SUBSCRIBE) 권한과 보내기(SEND) 제한
class StompSubscribeTest : StompTestSupport() {

    @Test
    fun `내가 이용하는 캘린더는 역할과 상관없이 구독해서 신호를 받는다`() {
        // given
        val calendar = newCalendar()

        CalendarRole.entries.forEach { role ->
            val user = newUser()
            join(user, calendar, role)
            val destination = "/topic/calendars/${calendar.id}"

            // when
            val received = connect(tokenOf(user)).subscribeTo(destination)

            // then
            assertReceives(destination, received)
        }
    }

    @Test
    fun `남의 캘린더, 없는 캘린더, 모르는 주소, 와일드카드 구독은 버려지고 연결은 유지된다`() {
        // given
        val me = newUser()
        val mine = newCalendar().also { join(me, it, CalendarRole.OWNER) }
        val others = newCalendar().also { join(newUser(), it, CalendarRole.OWNER) }
        val mineDestination = "/topic/calendars/${mine.id}"
        val session = connect(tokenOf(me))

        // 구독 주소 to 거절되지 않았다면 신호를 받았을 주소
        val rejected = listOf(
            "/topic/calendars/${others.id}" to "/topic/calendars/${others.id}",
            "/topic/calendars/${Long.MAX_VALUE}" to "/topic/calendars/${Long.MAX_VALUE}",
            "/topic/unknown" to "/topic/unknown",
            "/topic/calendars/*" to mineDestination,
            "/topic/**" to mineDestination,
        ).map { (subscribeTo, signalTo) -> session.subscribeTo(subscribeTo) to signalTo }

        // when: 거절된 구독 뒤에 내 캘린더를 구독한다
        val received = session.subscribeTo(mineDestination)

        // then: 같은 연결의 내 캘린더 구독은 받는다 (연결이 끊기지 않았다)
        assertReceives(mineDestination, received)
        rejected.forEach { (queue, signalTo) ->
            sendSignal(signalTo)
            assertNothingReceived(queue)
        }
    }

    @Test
    fun `클라이언트가 우편함 주소로 직접 보내면 구독자에게 가지 않는다`() {
        // given
        val me = newUser()
        val calendar = newCalendar().also { join(me, it, CalendarRole.OWNER) }
        val destination = "/topic/calendars/${calendar.id}"
        val session = connect(tokenOf(me))
        val received = session.subscribeTo(destination)
        assertReceives(destination, received)

        // when
        session.send(destination, "가짜 신호".toByteArray())

        // then: 구독 확인용 테스트 신호가 늦게 올 수 있어서, 가짜 신호가 왔는지만 본다
        Thread.sleep(500)
        assertFalse(received.contains("가짜 신호"))
    }
}
