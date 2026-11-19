package com.team4.live_calendar.global.security

// 컨트롤러 파라미터에 붙이면 로그인한 회원의 ID(Long)를 넣어 준다
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUserId
