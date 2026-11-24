package com.team4.live_calendar.domain.share.service

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.share.dto.ShareCreateRequest
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.share.repository.ShareCalendarConnectionRepository
import com.team4.live_calendar.domain.share.repository.ShareInfoRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShareService(
    private val shareInfoRepository: ShareInfoRepository,
    private val shareCalendarConnectionRepository: ShareCalendarConnectionRepository,
    private val subscribeRepository: SubscribeRepository,
) {
    @Transactional
    fun create(userId: Long, request: ShareCreateRequest): Long {
        val calendarIds = request.calendarIds.distinct()
        if (calendarIds.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        val ownerRelations = calendarIds.map { calendarId ->
            subscribeRepository.findByUserIdAndCalendarIdAndRole(
                userId = userId,
                calendarId = calendarId,
                role = CalendarRole.OWNER,
            ) ?: throw BusinessException(ErrorCode.FORBIDDEN)
        }

        val share = shareInfoRepository.save(
            ShareInfo.create(
                user = ownerRelations.first().user,
                title = request.title,
                groupName = null,
                content = request.content,
                isPublic = request.isPublic,
            ),
        )

        val connections = ownerRelations.mapIndexed { index, relation ->
            ShareCalendarConnection.create(
                share = share,
                calendar = relation.calendar,
                displayOrder = index + 1,
            )
        }
        shareCalendarConnectionRepository.saveAll(connections)

        return checkNotNull(share.id)
    }
}