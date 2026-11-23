package com.team4.live_calendar.domain.auth.repository

import com.team4.live_calendar.domain.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    // DELETE 한 문장으로 지우고 지운 행 수를 돌려준다. 동시 재발급 때 한쪽만 1을 받는다
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    fun deleteByTokenHash(tokenHash: String): Int
}