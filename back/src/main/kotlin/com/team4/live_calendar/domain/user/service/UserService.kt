package com.team4.live_calendar.domain.user.service

import com.team4.live_calendar.domain.user.dto.UserResponse
import com.team4.live_calendar.domain.user.dto.UserTagResponse
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val publicIds: PublicIds,
) {
    fun getMe(userId: Long): UserResponse {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        return UserResponse.from(user, publicIds)
    }

    // 태그 하나로 회원 한 명을 찾는다. 못 읽는 태그도 없는 회원과 같은 404
    fun findByTag(tag: String): UserTagResponse {
        val id = publicIds.user.decode(tag) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val user = userRepository.findByIdOrNull(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        return UserTagResponse.from(user, publicIds)
    }

}
