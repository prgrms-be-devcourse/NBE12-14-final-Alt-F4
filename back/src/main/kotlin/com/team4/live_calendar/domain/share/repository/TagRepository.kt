package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.Tag
import org.springframework.data.jpa.repository.JpaRepository

interface TagRepository : JpaRepository<Tag, Long>
