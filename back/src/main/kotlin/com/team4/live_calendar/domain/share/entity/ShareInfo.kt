package com.team4.live_calendar.domain.share.entity

import com.team4.live_calendar.domain.user.entity.User
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
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.hibernate.annotations.ColumnDefault
import java.time.Instant

// 공유 페이지
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "share_info")
class ShareInfo protected constructor(
    user: User,
    title: String,
    groupName: String?,
    content: String?,
    isPublic: Boolean,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @Column(name = "title", nullable = false, length = 255)
    var title: String = title
        protected set

    @Column(name = "group_name", length = 100)
    var groupName: String? = groupName
        protected set

    @Column(name = "content", columnDefinition = "TEXT")
    var content: String? = content
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    @Column(name = "is_public", nullable = false)
    @ColumnDefault("0")
    var isPublic: Boolean = isPublic
        protected set

    companion object {
        fun create(
            user: User,
            title: String,
            groupName: String?,
            content: String?,
            isPublic: Boolean = false,
        ): ShareInfo =
            ShareInfo(
                user = user,
                title = title.trim(),
                groupName = groupName,
                content = content,
                isPublic = isPublic,
            )
    }
}
