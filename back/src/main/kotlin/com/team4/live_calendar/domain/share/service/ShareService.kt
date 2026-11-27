package com.team4.live_calendar.domain.share.service

import com.team4.live_calendar.domain.calendar.entity.CalendarRole
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.category.repository.EventCategoryRepository
import com.team4.live_calendar.domain.event.service.EventService
import com.team4.live_calendar.domain.share.dto.ShareCalendarPreviewResponse
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
import java.time.OffsetDateTime

@Service
class ShareService(
    private val shareInfoRepository: ShareInfoRepository,
    private val shareCalendarConnectionRepository: ShareCalendarConnectionRepository,
    private val subscribeRepository: SubscribeRepository,
    private val calendarRepository: CalendarRepository,
    private val userRepository: UserRepository,
    private val publicIds: PublicIds,
    private val eventService: EventService,
    private val eventCategoryRepository: EventCategoryRepository,
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
    fun getCalendarPreview(
        shareId: Long,
        calendarId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime,
        timezone: String?,
    ): ShareCalendarPreviewResponse {
        // 구독 전에도, 이 공개 공유 페이지에 연결된 캘린더만 미리 볼 수 있다.
        if (shareCalendarConnectionRepository.countPublicShareCalendars(shareId, listOf(calendarId)) == 0L) {
            throw BusinessException(ErrorCode.SHARE_NOT_FOUND)
        }

        // 공유 미리보기에는 달력과 일정 목록에서 쓰는 필드만 포함한다.
        val events = eventService.findForSharePreview(calendarId, from, to, timezone).map { event ->
            ShareCalendarPreviewResponse.PreviewEvent(
                id = event.id,
                categoryId = event.categoryId,
                title = event.title,
                startAt = event.startAt,
                endAt = event.endAt,
                isAllDay = event.isAllDay,
                occurrenceStart = event.occurrenceStart,
            )
        }
        // 현재 기간에 일정이 없는 카테고리도 선택할 수 있도록 목록 전체를 내려준다.
        val categories = eventCategoryRepository.findAllByCalendarIdInOrderByIdAsc(listOf(calendarId)).map { category ->
            ShareCalendarPreviewResponse.PreviewCategory(
                id = requireNotNull(category.id),
                name = category.name,
                color = category.color,
            )
        }
        return ShareCalendarPreviewResponse(calendarId = calendarId, categories = categories, events = events)
    }

    @Transactional(readOnly = true)
    fun findPublicShares(
        keyword: String? = null,
        tag: String? = null,
    ): List<ShareSummaryResponse> {
        val normalizedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedTag = tag?.trim()?.takeIf { it.isNotEmpty() }

        return shareInfoRepository.searchPublicShares(
            keyword = normalizedKeyword,
            tag = normalizedTag,
        ).map { share -> ShareSummaryResponse.from(share, publicIds) }
    }
}
