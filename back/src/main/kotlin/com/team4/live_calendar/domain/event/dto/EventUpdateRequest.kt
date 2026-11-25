package com.team4.live_calendar.domain.event.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime

// 일정 내용 전체 수정. 캘린더 이동은 지원하지 않는다
// 시간 일정은 startAt, endAt / 종일 일정은 isAllDay=true + startDate, endDate
// rrule을 보내지 않으면 반복이 해제되므로, 반복을 유지하려면 기존 rrule을 같이 보낸다
// 반복 일정은 scope 파라미터가 필요하다
// - scope=ALL: 반복 일정 자체의 값(첫 회차 기준, GET /events/{id} 응답)을 보낸다. 클릭한 회차의 시간을 보내면 반복의 시작이 그 회차로 옮겨지고,
//   시간이 바뀐 것으로 판단돼 이 일정만 수정·삭제한 회차들도 원래대로 돌아간다
// - scope=THIS: 그 회차의 제목·설명·장소·시간만 바뀐다. 종일 여부·카테고리는 반복 일정과 같아야 하고 rrule, timezone은 무시한다
// - scope=THIS_AND_FOLLOWING: 고른 회차부터의 새 일정 값을 보낸다. 원래 반복은 그 회차 전에 끝나고 새 일정(새 id)이 생긴다
//   새 일정은 고른 회차보다 앞당겨도 되지만, 앞에 남는 마지막 회차가 끝나기 전에 시작하면 400이다. 첫 회차를 고르면 ALL과 같다
//   응답의 id로 화면을 갱신한다 (보통 새 id, 첫 회차면 원래 id). COUNT 반복이면 남은 횟수는 프론트가 계산해서 rrule에 넣는다
class EventUpdateRequest(
    val categoryId: Long?,

    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    val description: String?,

    @field:Size(max = 255)
    val location: String?,

    val isAllDay: Boolean = false,

    val startAt: OffsetDateTime? = null,

    val endAt: OffsetDateTime? = null,

    val startDate: LocalDate? = null,

    val endDate: LocalDate? = null,

    // 없으면 기존 시간대 유지. 단, 일반·종일 일정을 처음 시간 반복으로 바꿀 때는 꼭 보내야 한다 (없으면 400 INVALID_RECURRENCE)
    @field:Size(max = 100)
    val timezone: String? = null,

    // 없거나 비어 있으면 반복 해제
    val rrule: String? = null,
)
