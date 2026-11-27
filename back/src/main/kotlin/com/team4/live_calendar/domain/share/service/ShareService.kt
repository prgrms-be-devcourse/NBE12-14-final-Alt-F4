package com.team4.live_calendar.domain.share.service

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.share.dto.ShareCreateRequest
import com.team4.live_calendar.domain.share.dto.ShareDetailResponse
import com.team4.live_calendar.domain.share.dto.ShareSummaryResponse
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.share.repository.ShareCalendarConnectionRepository
import com.team4.live_calendar.domain.share.repository.ShareInfoRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShareService(
    private val shareInfoRepository: ShareInfoRepository,
    private val shareCalendarConnectionRepository: ShareCalendarConnectionRepository,
    private val subscribeRepository: SubscribeRepository,
    private val calendarRepository: CalendarRepository,
    private val userRepository: UserRepository,
    private val publicIds: PublicIds,
) {
    @Transactional
    fun create(userId: Long, request: ShareCreateRequest): Long {
        val calendarIds = request.calendarIds.distinct()
        if (calendarIds.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        val ownerCount = subscribeRepository.countByUserIdAndCalendarIdInAndRole(
            userId = userId,
            calendarIds = calendarIds,
            role = CalendarRole.OWNER,
        )
        if (ownerCount != calendarIds.size.toLong()) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        val share = shareInfoRepository.save(
            ShareInfo.create(
                user = userRepository.getReferenceById(userId),
                title = request.title,
                groupName = null,
                content = request.content,
                isPublic = request.isPublic,
            ),
        )

        val connections = calendarIds.mapIndexed { index, calendarId ->
            ShareCalendarConnection.create(
                share = share,
                calendar = calendarRepository.getReferenceById(calendarId),
                displayOrder = index + 1,
            )
        }
        shareCalendarConnectionRepository.saveAll(connections)

        return checkNotNull(share.id)
    }

    @Transactional(readOnly = true)
    fun getDetail(shareId: Long): ShareDetailResponse {
        val share = shareInfoRepository.findByIdOrNull(shareId)
            ?.takeIf { it.isPublic }
            ?: throw BusinessException(ErrorCode.SHARE_NOT_FOUND)

        val connections = shareCalendarConnectionRepository.findAllWithCalendarByShareId(shareId)
        return ShareDetailResponse.from(share, connections, publicIds)
    }

    @Transactional(readOnly = true)
    fun findPublicShares(): List<ShareSummaryResponse> =
        shareInfoRepository.findAllByIsPublicTrueOrderByCreatedAtDescIdDesc()
            .map { share -> ShareSummaryResponse.from(share, publicIds) }
}
