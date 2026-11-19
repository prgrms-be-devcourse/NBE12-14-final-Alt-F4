package com.team4.live_calendar.global.exception

class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)
