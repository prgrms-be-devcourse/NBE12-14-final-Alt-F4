package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.ShareTagConnection
import org.springframework.data.jpa.repository.JpaRepository

interface ShareTagConnectionRepository : JpaRepository<ShareTagConnection, Long>
