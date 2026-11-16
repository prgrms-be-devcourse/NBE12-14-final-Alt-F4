package com.team4.live_calendar.sample.service

import com.team4.live_calendar.sample.entity.SampleEntity
import com.team4.live_calendar.sample.repository.SampleRepository
import org.springframework.stereotype.Service

@Service
class SampleService(
    private val sampleRepository: SampleRepository
) {

    fun getSample(): SampleEntity{
        return sampleRepository.getReferenceById(1)
    }

}