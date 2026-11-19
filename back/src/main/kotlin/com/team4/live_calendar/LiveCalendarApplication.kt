package com.team4.live_calendar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LiveCalendarApplication

fun main(args: Array<String>) {
    runApplication<LiveCalendarApplication>(*args)
}
