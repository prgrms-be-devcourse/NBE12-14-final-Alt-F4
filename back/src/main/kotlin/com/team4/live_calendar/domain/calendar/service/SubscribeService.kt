package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.entity.Subscribe
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubscribeService(
    private val groupRepository: CalendarGroupRepository,
    private val calendarRepository: CalendarRepository,
    private val subscribeRepository: SubscribeRepository,
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

        return checkNotNull(subscribeRepository.save(subscription).id)
    }
}