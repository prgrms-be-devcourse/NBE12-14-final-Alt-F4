package com.team4.live_calendar.domain.share.entity

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
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 공유 페이지에 붙은 태그
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "share_tag_connection")
class ShareTagConnection protected constructor(
    share: ShareInfo,
    tag: Tag,
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
    @JoinColumn(name = "tag_id", nullable = false, updatable = false)
    var tag: Tag = tag
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(share: ShareInfo, tag: Tag): ShareTagConnection =
            ShareTagConnection(share = share, tag = tag)
    }
}
