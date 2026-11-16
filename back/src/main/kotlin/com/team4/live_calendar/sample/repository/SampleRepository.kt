package com.team4.live_calendar.sample.repository

import com.team4.live_calendar.sample.entity.SampleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SampleRepository : JpaRepository<SampleEntity, Long> {
}