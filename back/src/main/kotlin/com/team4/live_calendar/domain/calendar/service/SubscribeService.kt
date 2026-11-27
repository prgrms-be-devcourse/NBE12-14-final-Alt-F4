package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarGroupRequest
import com.team4.live_calendar.domain.calendar.dto.SubscribeBatchRequest
import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.repository.CategoryOptionRepository
import com.team4.live_calendar.domain.event.repository.EventOptionRepository
import com.team4.live_calendar.domain.share.repository.ShareCalendarConnectionRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.java

@Service
class SubscribeService(
    private val groupRepository: CalendarGroupRepository,
    private val calendarRepository: CalendarRepository,
    private val subscribeRepository: SubscribeRepository,
    private val eventOptionRepository: EventOptionRepository,
    private val categoryOptionRepository: CategoryOptionRepository,
    private val shareCalendarConnectionRepository: ShareCalendarConnectionRepository,
    private val calendarGroupService: CalendarGroupService,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun subscribe(userId: Long, groupId: Long, calendarId: Long): Long {
        val group = groupRepository.findByIdAndUserId(groupId, userId)
            ?: throw BusinessException(ErrorCode.FORBIDDEN)
        val calendar = calendarRepository.findByIdOrNull(calendarId)
            ?: throw BusinessException(ErrorCode.CALENDAR_NOT_FOUND)
        val existingRelation = subscribeRepository.findByUserIdAndCalendarId(userId, calendarId)
        if (existingRelation != null) {
            throw BusinessException(ErrorCode.CALENDAR_RELATION_ALREADY_EXISTS)
        }

        val subscription = Subscribe.create(
            user = group.user,
            group = group,
            calendar = calendar,
            role = CalendarRole.SUBSCRIBER,
            color = null,
        )

        val saved = try {
            subscribeRepository.saveAndFlush(subscription)
        } catch (e: DataIntegrityViolationException) {
            val duplicateRelation = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .any { it.constraintName?.substringAfterLast('.') == "uk_subscribes_user_calendar" }
            if (duplicateRelation) {
                throw BusinessException(ErrorCode.CALENDAR_RELATION_ALREADY_EXISTS)
            }
            throw e
        }
        return checkNotNull(saved.id)
    }

    @Transactional
    fun cancel(userId: Long, calendarId: Long) {
        val subscription = subscribeRepository.findByUserIdAndCalendarIdAndRole(
            userId = userId,
            calendarId = calendarId,
            role = CalendarRole.SUBSCRIBER,
        ) ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)

        val subscriptionId = checkNotNull(subscription.id)
        eventOptionRepository.deleteAllBySubscribeId(subscriptionId)
        categoryOptionRepository.deleteAllBySubscribeId(subscriptionId)
        subscribeRepository.delete(subscription)
    }

    @Transactional
    fun changeNotificationEnabled(userId: Long, calendarId: Long, enabled: Boolean) {
        val subscription = subscribeRepository.findForUpdate(userId, calendarId)
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        subscription.changeNotificationEnabled(enabled)
    }

    @Transactional
    fun subscribeBatchFromShare(
        userId: Long,
        shareId: Long,
        request: SubscribeBatchRequest,
    ): List<Long> {
        val groupName = request.groupName
        val calendarIds = request.calendarIds.distinct()

        if (shareId <= 0 || calendarIds.any { it <= 0 }) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        entityManager.find(User::class.java, userId, LockModeType.PESSIMISTIC_WRITE)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val sharedCalendarCount = shareCalendarConnectionRepository.countPublicShareCalendars(
            shareId = shareId,
            calendarIds = calendarIds,
        )
        if (sharedCalendarCount != calendarIds.size.toLong()) {
            throw BusinessException(ErrorCode.PUBLIC_SHARE_CALENDAR_NOT_FOUND)
        }

        val groupId = groupRepository.findFirstByUserIdAndNameOrderByIdAsc(userId, groupName)?.id
            ?: calendarGroupService.create(userId, CalendarGroupRequest(name = groupName)).id

        return calendarIds.map { calendarId ->
            subscribe(userId = userId, groupId = groupId, calendarId = calendarId)
        }
    }
}
