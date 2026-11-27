package com.team4.live_calendar.domain.category.controller

import com.team4.live_calendar.domain.category.dto.CategoryCreateRequest
import com.team4.live_calendar.domain.category.dto.CategoryResponse
import com.team4.live_calendar.domain.category.dto.CategoryUpdateRequest
import com.team4.live_calendar.domain.category.service.CategoryService
import com.team4.live_calendar.global.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 카테고리 목록 API는 따로 없다. GET /api/v1/groups가 캘린더마다 categories로 보여 준다
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val categoryService: CategoryService,
) {

    // 본문에 calendarId (일정 생성과 같음)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CategoryCreateRequest,
    ): CategoryResponse = categoryService.create(userId, request)

    // 모든 칸 보내기 (안 보낸 color는 비워짐)
    @PatchMapping("/{id}")
    fun update(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CategoryUpdateRequest,
    ): CategoryResponse = categoryService.update(userId, id, request)

    // 이 카테고리를 단 일정은 남고 카테고리 없음이 된다
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ) = categoryService.delete(userId, id)

    // 내 화면에서만 이 카테고리의 일정 숨기기 (편집 권한이 없어도 된다). 이미 숨겼어도 204
    @PutMapping("/{id}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun hide(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ) = categoryService.hide(userId, id)

    // 숨긴 카테고리 다시 보이기. 숨긴 적 없어도 204
    @DeleteMapping("/{id}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun show(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
    ) = categoryService.show(userId, id)
}
