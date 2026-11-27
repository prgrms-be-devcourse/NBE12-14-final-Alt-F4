package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.ShareInfo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShareInfoRepository : JpaRepository<ShareInfo, Long> {

    @Query(
        """
        select s
        from ShareInfo s
        where s.isPublic = true
          and (
              :keyword is null
              or locate(lower(:keyword), lower(s.title)) > 0
              or locate(lower(:keyword), lower(coalesce(s.content, ''))) > 0
          )
          and (
              :tag is null
              or exists (
                  select 1
                  from ShareTagConnection c
                  where c.share = s
                    and lower(c.tag.name) = lower(:tag)
              )
          )
        order by s.createdAt desc, s.id desc
        """,
    )
    fun searchPublicShares(
        @Param("keyword") keyword: String?,
        @Param("tag") tag: String?,
    ): List<ShareInfo>
}
