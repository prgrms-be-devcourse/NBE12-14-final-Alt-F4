package com.team4.live_calendar.domain.category.service

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.dto.CategoryCreateRequest
import com.team4.live_calendar.domain.category.dto.CategoryResponse
import com.team4.live_calendar.domain.category.dto.CategoryUpdateRequest
import com.team4.live_calendar.domain.category.entity.CategoryOption
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.category.repository.CategoryOptionRepository
import com.team4.live_calendar.domain.category.repository.EventCategoryRepository
import com.team4.live_calendar.domain.event.repository.EventRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.websocket.CalendarChangedEvent
import com.team4.live_calendar.global.websocket.DisplayChangedEvent
import com.team4.live_calendar.global.websocket.SyncType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

// 캘린더에 딸린 원본 카테고리 생성·수정·삭제 (목록은 GET /groups에서 캘린더마다 보여 준다)
// 만들기·고치기·지우기는 일정과 같은 편집 권한(OWNER·EDITOR), 내 화면 숨김·복원은 이용 관계가 있는 누구나
// 이용 관계 찾기·역할 확인은 EventService·CalendarService처럼 여기에 따로 둔다
@Service
@Transactional(readOnly = true)
class CategoryService(
    private val eventCategoryRepository: EventCategoryRepository,
    private val calendarRepository: CalendarRepository,
    private val subscribeRepository: SubscribeRepository,
    private val categoryOptionRepository: CategoryOptionRepository,
    private val eventRepository: EventRepository,
    // 바뀐 사실만 이벤트로 알리고, 커밋 뒤 SyncSignalListener가 STOMP 신호로 보낸다
    private val eventPublisher: ApplicationEventPublisher,
) {

    // 같은 캘린더에 같은 이름도 만들 수 있다 (그룹·캘린더 이름과 같음)
    @Transactional
    fun create(userId: Long, request: CategoryCreateRequest): CategoryResponse {
        val calendar = calendarRepository.findByIdOrNull(requireNotNull(request.calendarId))
            ?: throw BusinessException(ErrorCode.CALENDAR_NOT_FOUND)
        checkEditable(userId, requireNotNull(calendar.id))
        publishCategoriesChanged(requireNotNull(calendar.id))
        val category = eventCategoryRepository.save(EventCategory.create(calendar, request.name, request.color))
        return CategoryResponse.from(category)
    }

    // 모든 칸 보내기라 안 보낸 color는 비워진다 (캘린더 수정과 같음)
    @Transactional
    fun update(userId: Long, categoryId: Long, request: CategoryUpdateRequest): CategoryResponse {
        val category = findEditableCategory(userId, categoryId)
        publishCategoriesChanged(requireNotNull(category.calendar.id))
        category.update(request.name, request.color)
        return CategoryResponse.from(category)
    }

    // 이 카테고리를 단 일정은 남기고 카테고리만 비운다 ("카테고리 없음"). 사람별 설정은 지운다
    // 엔티티를 하나씩 불러 바꾸지 않고 카테고리 ID로 한 번에 처리해서, 일정·설정이 많아도 쿼리 수가 같다
    // 확인과 삭제 사이에 다른 요청이 이 카테고리로 일정을 저장하면 FK 때문에 실패한다 (드물어서 막지 않음)
    @Transactional
    fun delete(userId: Long, categoryId: Long) {
        val category = findEditableCategory(userId, categoryId)
        publishCategoriesChanged(requireNotNull(category.calendar.id))
        // 벌크 update라 일정의 updated_at은 바뀌지 않는다
        eventRepository.clearCategory(categoryId)
        categoryOptionRepository.deleteAllOfCategory(categoryId)
        // 벌크 삭제라 SQL이 바로 나간다. 위에서 불러 둔 카테고리 엔티티는 컨텍스트에 남지만 다시 쓰지 않는다
        eventCategoryRepository.deleteCategory(categoryId)
    }

    // 내 화면에서만 이 카테고리의 일정을 숨긴다 (원본과 다른 사람 화면은 그대로). 이미 숨겼으면 그대로
    // 편집 권한이 없어도 (VIEWER·SUBSCRIBER) 자기 화면은 정리할 수 있다
    // 내 이용 관계 줄을 잠가 같은 사람의 동시 요청을 차례로 처리한다
    // READ_COMMITTED: 잠금을 얻은 뒤의 조회가 앞 요청이 방금 저장한 줄을 보도록 (MySQL 기본 REPEATABLE READ면 못 봐서 중복 저장)
    // 다른 트랜잭션 안에서 부르지 않는다 (격리 수준이 무시된다)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun hide(userId: Long, categoryId: Long) {
        val category = findCategory(categoryId)
        val subscribe = subscribeRepository.findForUpdate(userId, requireNotNull(category.calendar.id))
            ?: throw BusinessException(ErrorCode.FORBIDDEN)
        // 내 화면만 바뀌므로 캘린더 신호가 아니라 내 다른 기기에만 알린다 (이미 숨겼어도 한 번)
        eventPublisher.publishEvent(DisplayChangedEvent(userId))
        val option = categoryOptionRepository.findBySubscribeIdAndCategoryId(requireNotNull(subscribe.id), categoryId)
        if (option != null) {
            option.hide()
        } else {
            categoryOptionRepository.save(CategoryOption.create(subscribe, category, category.calendar, isVisible = false))
        }
    }

    // 숨긴 카테고리를 다시 보이게 한다: 내 줄만 지운다 (다른 사람의 숨김은 그대로). 숨긴 적 없어도 그대로
    // 나중에 카테고리 내 색을 쓰게 되면 줄을 지우지 않고 isVisible=true로 바꾼다
    // 벌크 delete라 잠금이 없어도 결과는 맞지만, 숨김과 같은 차례로 처리되도록 같은 모양으로 둔다
    // 다른 트랜잭션 안에서 부르지 않는다 (격리 수준이 무시된다)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun show(userId: Long, categoryId: Long) {
        val category = findCategory(categoryId)
        val subscribe = subscribeRepository.findForUpdate(userId, requireNotNull(category.calendar.id))
            ?: throw BusinessException(ErrorCode.FORBIDDEN)
        eventPublisher.publishEvent(DisplayChangedEvent(userId))
        categoryOptionRepository.deleteOfSubscribe(requireNotNull(subscribe.id), categoryId)
    }

    // 카테고리가 바뀌면 그 캘린더 화면이 그룹 목록과 일정을 다시 불러온다 (지우면 일정의 카테고리가 비므로)
    private fun publishCategoriesChanged(calendarId: Long) {
        eventPublisher.publishEvent(CalendarChangedEvent(SyncType.CATEGORIES_CHANGED, calendarId))
    }

    // 없는 카테고리는 404
    private fun findCategory(categoryId: Long): EventCategory =
        eventCategoryRepository.findByIdOrNull(categoryId)
            ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

    // 없는 카테고리는 404, 그 카테고리 캘린더의 편집 권한이 없으면 403 (일정 API와 같음)
    private fun findEditableCategory(userId: Long, categoryId: Long): EventCategory {
        val category = findCategory(categoryId)
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
