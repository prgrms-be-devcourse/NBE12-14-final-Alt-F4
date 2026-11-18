package com.team4.live_calendar.domain.user.repository

import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.config.JpaConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest
@Import(JpaConfig::class)
class UserRepositoryTest(
    @Autowired private val userRepository: UserRepository,
) {

    @Test
    fun `회원을 저장하면 id와 생성일시가 채워진다`() {
        val saved = userRepository.save(User.signUp("Test@Mail.com ", "{bcrypt}hash", "기백"))

        assertNotNull(saved.id)
        assertFalse(saved.createdAt.isAfter(Instant.now()))
        assertEquals("test@mail.com", saved.email)
    }

    @Test
    fun `이메일로 존재 여부와 회원을 조회한다`() {
        userRepository.save(User.signUp("a@mail.com", "{bcrypt}hash", "a"))

        assertTrue(userRepository.existsByEmail("a@mail.com"))
        assertNotNull(userRepository.findByEmail("a@mail.com"))
    }

    @Test
    fun `같은 이메일은 DB 유니크 제약으로 저장할 수 없다`() {
        userRepository.saveAndFlush(User.signUp("dup@mail.com", "{bcrypt}hash", "a"))

        assertThrows<DataIntegrityViolationException> {
            userRepository.saveAndFlush(User.signUp("dup@mail.com", "{bcrypt}hash", "b"))
        }
    }
}
