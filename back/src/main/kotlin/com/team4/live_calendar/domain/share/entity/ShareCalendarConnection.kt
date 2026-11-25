package com.team4.live_calendar.domain.share.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 공유 페이지에 걸린 캘린더
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "share_calendar_connection",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_share_calendar_connection_share_calendar",
            columnNames = ["share_id", "calendar_id"],
        ),
    ],
)
class ShareCalendarConnection protected constructor(
    share: ShareInfo,
    calendar: Calendar,
    displayOrder: Int?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_id", nullable = false, updatable = false)
    var share: ShareInfo = share
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    var calendar: Calendar = calendar
        protected set

    @Column(name = "display_order")
    var displayOrder: Int? = displayOrder
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(
            share: ShareInfo,
            calendar: Calendar,
            displayOrder: Int?,
        ): ShareCalendarConnection =
            ShareCalendarConnection(
                share = share,
                calendar = calendar,
                displayOrder = displayOrder,
            )
    }
}
