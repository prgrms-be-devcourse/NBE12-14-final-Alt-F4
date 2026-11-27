package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.event.service.EventOccurrenceReader
import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SharedEventDisplayMode
import com.team4.live_calendar.global.publicid.PublicIds
import java.time.Instant

// 다른 참여자에게는 제목·설명·장소를 주지 않는다 (기능 정의서: 응답 데이터에서도 제외)
// 반복 일정은 조율 범위 안 회차마다 한 줄이고 id가 같다
data class SharedEventResponse(
    // 공유 ID. 표시 방식 변경·공유 빼기에 쓴다
    val id: Long,
    // 공유한 회원의 태그(Sqids). 숫자 user id는 내보내지 않는다
    val tag: String,
    val nickname: String,
    val isMine: Boolean,
    val displayMode: SharedEventDisplayMode,
    // CALENDAR_NAME_AND_TIME으로 공유했을 때만
    val calendarName: String?,
    // 내가 공유한 일정만 (내 공유 목록을 관리할 때 무슨 일정인지 알아보도록)
    val title: String?,
    // 내가 공유한 일정만. 일정 고르는 화면에서 이미 공유한 일정에 체크를 미리 켜는 데 쓴다
    val eventId: Long?,
    val startAt: Instant,
    val endAt: Instant,
    val isAllDay: Boolean,
) {
    companion object {
        fun of(
            shared: RoomSharedEvent,
            occurrence: EventOccurrenceReader.Occurrence,
            isMine: Boolean,
            publicIds: PublicIds,
        ): SharedEventResponse {
            val event = shared.event
            return SharedEventResponse(
                id = requireNotNull(shared.id),
                tag = publicIds.user.encode(requireNotNull(shared.member.user.id)),
                nickname = shared.member.user.nickname,
                isMine = isMine,
                displayMode = shared.displayMode,
                calendarName = event.calendar.name.takeIf { shared.displayMode == SharedEventDisplayMode.CALENDAR_NAME_AND_TIME },
                title = event.title.takeIf { isMine },
                eventId = event.id.takeIf { isMine },
                startAt = occurrence.startAt,
                endAt = occurrence.endAt,
                isAllDay = event.isAllDay,
            )
        }
    }
}
