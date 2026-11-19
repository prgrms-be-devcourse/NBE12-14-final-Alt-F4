package com.team4.live_calendar.domain.share.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "tag")
class Tag protected constructor(
    name: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    // ERD에서 tag의 생성/수정 일시만 NULL 허용
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    var createdAt: Instant? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: Instant? = null
        protected set

    companion object {
        fun create(name: String): Tag = Tag(name = name.trim())
    }
}
