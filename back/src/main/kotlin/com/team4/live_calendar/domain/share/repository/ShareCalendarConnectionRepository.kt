package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShareCalendarConnectionRepository : JpaRepository<ShareCalendarConnection, Long> {

    @Query(
        """
    select count(distinct c.calendar.id)
    from ShareCalendarConnection c
    where c.share.id = :shareId
      and c.share.isPublic = true
      and c.calendar.id in :calendarIds
    """,
    )
    fun countPublicShareCalendars(
        @Param("shareId") shareId: Long,
        @Param("calendarIds") calendarIds: Collection<Long>,
    ): Long

    @Query(
        """
    select c
    from ShareCalendarConnection c
    join fetch c.calendar
    where c.share.id = :shareId
    order by c.displayOrder asc nulls last, c.id asc
    """,
    )
    fun findAllWithCalendarByShareId(
        @Param("shareId") shareId: Long,
    ): List<ShareCalendarConnection>

    // 캘린더 삭제: 이 캘린더의 공유 페이지 연결
    @Modifying
    @Query("delete from ShareCalendarConnection c where c.calendar.id = :calendarId")
    fun deleteAllInCalendar(calendarId: Long): Int
}
