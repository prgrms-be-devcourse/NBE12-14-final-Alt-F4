package com.team4.live_calendar.sample.controller

import com.team4.live_calendar.sample.dto.SampleRequest
import com.team4.live_calendar.sample.dto.SampleResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/samples")
class SampleController {

    @GetMapping("/{id}")
    fun getSample(@PathVariable id: Long): ResponseEntity<SampleResponse> {
        return ResponseEntity.ok(SampleResponse())
    }

    @PostMapping
    fun postSample(@RequestBody request: SampleRequest): ResponseEntity<SampleResponse> {
        return ResponseEntity.ok(SampleResponse())
    }

    @PutMapping("/{id}")
    fun putSample(
        @PathVariable id: Long,
        @RequestBody request: SampleRequest
    ): ResponseEntity<SampleResponse> {
        return ResponseEntity.ok(SampleResponse())
    }

    @PatchMapping("/{id}")
    fun patchSample(
        @PathVariable id: Long,
        @RequestBody request: SampleRequest
    ): ResponseEntity<SampleResponse> {
        return ResponseEntity.ok(SampleResponse())
    }

    @DeleteMapping("/{id}")
    fun deleteSample(@PathVariable id: Long): ResponseEntity<Void> {
        return ResponseEntity.noContent().build()
    }

}