package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.RoomSharedEvent
import com.team4.live_calendar.domain.room.entity.SharedEventDisplayMode

// 공유한 본인에게 돌려주는 응답 (공유, 표시 방식 변경)
data class MySharedEventResponse(
    val id: Long,
    val eventId: Long,
    val displayMode: SharedEventDisplayMode,
) {
    companion object {
        fun from(shared: RoomSharedEvent): MySharedEventResponse =
            MySharedEventResponse(
                id = requireNotNull(shared.id),
                eventId = requireNotNull(shared.event.id),
                displayMode = shared.displayMode,
            )
    }
}
