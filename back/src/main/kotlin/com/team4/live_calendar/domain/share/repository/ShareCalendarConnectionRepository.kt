package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShareCalendarConnectionRepository : JpaRepository<ShareCalendarConnection, Long> {

    @Query(
        """
        select case when count(c) > 0 then true else false end
        from ShareCalendarConnection c
        where c.share.id = :shareId
          and c.calendar.id = :calendarId
          and c.share.isPublic = true
        """,
    )
    fun existsPublicShareCalendar(
        @Param("shareId") shareId: Long,
        @Param("calendarId") calendarId: Long,
    ): Boolean

    // 캘린더 삭제: 이 캘린더의 공유 페이지 연결
    @Modifying
    @Query("delete from ShareCalendarConnection c where c.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int
}
