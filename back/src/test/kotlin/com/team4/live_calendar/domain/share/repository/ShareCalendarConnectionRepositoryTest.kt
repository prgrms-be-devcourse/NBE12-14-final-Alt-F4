package com.team4.live_calendar.domain.share.repository

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.share.entity.ShareCalendarConnection
import com.team4.live_calendar.domain.share.entity.ShareInfo
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException

// UNIQUE는 ddl-auto: validate가 확인하지 않아서, 같은 값을 두 번 저장해 DB가 막는지 본다
@DataJpaTest
@Import(JpaConfig::class)
class ShareCalendarConnectionRepositoryTest(
    @Autowired private val shareCalendarConnectionRepository: ShareCalendarConnectionRepository,
    @Autowired private val em: EntityManager,
) {

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    @Test
    fun `같은 공유 페이지에 같은 캘린더는 DB 유니크 제약으로 두 번 연결할 수 없다`() {
        // given
        val user = persist(User.signUp("test@mail.com", "{bcrypt}hash", "test"))
        val calendar = persist(Calendar.create("팀 캘린더", null, null))
        val share = persist(ShareInfo.create(user, "공유 페이지", null, null))
        shareCalendarConnectionRepository.saveAndFlush(ShareCalendarConnection.create(share, calendar, 1))

        // when & then
        assertThrows<DataIntegrityViolationException> {
            shareCalendarConnectionRepository.saveAndFlush(ShareCalendarConnection.create(share, calendar, 2))
        }
    }
}
