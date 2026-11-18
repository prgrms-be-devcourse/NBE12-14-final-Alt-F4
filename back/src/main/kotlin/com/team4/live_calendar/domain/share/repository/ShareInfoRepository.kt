package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.ShareInfo
import org.springframework.data.jpa.repository.JpaRepository

interface ShareInfoRepository : JpaRepository<ShareInfo, Long>
