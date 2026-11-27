package com.team4.live_calendar.domain.share.dto

import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.global.publicid.PublicIds

data class ShareDetailResponse(
    val shareId: String,
    val title: String,
    val content: String?,
    val calendars: List<SharedCalendarResponse>,
) {
    data class SharedCalendarResponse(
        val calendarId: Long,
        val name: String,
    )

    companion object {
        fun from(
            share: ShareInfo,
            connections: List<ShareCalendarConnection>,
            publicIds: PublicIds
        ): ShareDetailResponse =
            ShareDetailResponse(
                shareId = publicIds.share.encode(requireNotNull(share.id)),
                title = share.title,
                content = share.content,
                calendars = connections.map { connection ->
                    val calendar = connection.calendar
                    SharedCalendarResponse(
                        calendarId = requireNotNull(calendar.id),
                        name = calendar.name,
                    )
                },
            )
    }
}