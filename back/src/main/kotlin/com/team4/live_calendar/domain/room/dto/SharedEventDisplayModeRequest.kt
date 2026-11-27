package com.team4.live_calendar.domain.room.dto

import com.team4.live_calendar.domain.room.entity.SharedEventDisplayMode

// TIME_ONLY 또는 CALENDAR_NAME_AND_TIME
class SharedEventDisplayModeRequest(
    val displayMode: SharedEventDisplayMode,
)
