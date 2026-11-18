package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import org.springframework.data.jpa.repository.JpaRepository

interface ShareCalendarConnectionRepository : JpaRepository<ShareCalendarConnection, Long>
