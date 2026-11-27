package com.team4.live_calendar.domain.share.dto

import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.global.publicid.PublicIds

data class ShareSummaryResponse(
    val shareId: String,
    val title: String,
    val content: String?,
) {
    companion object {
        fun from(share: ShareInfo, publicIds: PublicIds): ShareSummaryResponse =
            ShareSummaryResponse(
                shareId = publicIds.share.encode(requireNotNull(share.id)),
                title = share.title,
                content = share.content,
            )
    }
}