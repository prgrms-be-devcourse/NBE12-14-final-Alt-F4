package com.team4.live_calendar.domain.category.service

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.dto.CategoryCreateRequest
import com.team4.live_calendar.domain.category.dto.CategoryResponse
import com.team4.live_calendar.domain.category.dto.CategoryUpdateRequest
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.repository.CategoryOptionRepository
import com.team4.live_calendar.domain.category.repository.EventCategoryRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 캘린더에 딸린 원본 카테고리 생성·수정·삭제 (목록은 GET /groups에서 캘린더마다 보여 준다)
// 권한은 일정과 같은 편집 권한(OWNER·EDITOR). 이용 관계 찾기·역할 확인은 EventService·CalendarService처럼 여기에 따로 둔다
@Service
@Transactional(readOnly = true)
class CategoryService(
    private val eventCategoryRepository: EventCategoryRepository,
    private val calendarRepository: CalendarRepository,
    private val subscribeRepository: SubscribeRepository,
    private val categoryOptionRepository: CategoryOptionRepository,
    private val eventRepository: EventRepository,
) {

    // 같은 캘린더에 같은 이름도 만들 수 있다 (그룹·캘린더 이름과 같음)
    @Transactional
    fun create(userId: Long, request: CategoryCreateRequest): CategoryResponse {
        val calendar = calendarRepository.findByIdOrNull(requireNotNull(request.calendarId))
            ?: throw BusinessException(ErrorCode.CALENDAR_NOT_FOUND)
        checkEditable(userId, requireNotNull(calendar.id))
        val category = eventCategoryRepository.save(EventCategory.create(calendar, request.name, request.color))
        return CategoryResponse.from(category)
    }

    // 모든 칸 보내기라 안 보낸 color는 비워진다 (캘린더 수정과 같음)
    @Transactional
    fun update(userId: Long, categoryId: Long, request: CategoryUpdateRequest): CategoryResponse {
        val category = findEditableCategory(userId, categoryId)
        category.update(request.name, request.color)
        return CategoryResponse.from(category)
    }

    // 이 카테고리를 단 일정은 남기고 카테고리만 비운다 ("카테고리 없음"). 사람별 설정은 지운다
    // 엔티티를 하나씩 불러 바꾸지 않고 카테고리 ID로 한 번에 처리해서, 일정·설정이 많아도 쿼리 수가 같다
    // 확인과 삭제 사이에 다른 요청이 이 카테고리로 일정을 저장하면 FK 때문에 실패한다 (드물어서 막지 않음)
    @Transactional
    fun delete(userId: Long, categoryId: Long) {
        findEditableCategory(userId, categoryId)
        // 벌크 update라 일정의 updated_at은 바뀌지 않는다
        eventRepository.clearCategory(categoryId)
        categoryOptionRepository.deleteAllOfCategory(categoryId)
        // 벌크 삭제라 SQL이 바로 나간다. 위에서 불러 둔 카테고리 엔티티는 컨텍스트에 남지만 다시 쓰지 않는다
        eventCategoryRepository.deleteCategory(categoryId)
    }

    // 없는 카테고리는 404, 그 카테고리 캘린더의 편집 권한이 없으면 403 (일정 API와 같음)
    private fun findEditableCategory(userId: Long, categoryId: Long): EventCategory {
        val category = eventCategoryRepository.findByIdOrNull(categoryId)
            ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        // 지연 로딩 프록시의 id는 쿼리를 더 만들지 않는다
        checkEditable(userId, requireNotNull(category.calendar.id))
        return category
    }

    // 이용 관계가 없으면 403, OWNER·EDITOR가 아니면 403
    private fun checkEditable(userId: Long, calendarId: Long) {
        val subscribe = subscribeRepository.findByUserIdAndCalendarId(userId, calendarId)
            ?: throw BusinessException(ErrorCode.FORBIDDEN)
        if (subscribe.role !in CalendarRole.EDITABLE_ROLES) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }
    }
}
