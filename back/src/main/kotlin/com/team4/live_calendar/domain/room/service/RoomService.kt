package com.team4.live_calendar.domain.room.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.repository.CalendarRepository
import com.team4.live_calendar.domain.room.dto.RoomCreateRequest
import com.team4.live_calendar.domain.room.dto.RoomDetailResponse
import com.team4.live_calendar.domain.room.entity.SchedulingRoom
import com.team4.live_calendar.domain.room.repository.RoomMemberRepository
import com.team4.live_calendar.domain.room.repository.SchedulingRoomRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.publicid.PublicIds
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class RoomService(
    private val userRepository: UserRepository,
    private val calendarRepository: CalendarRepository,
    private val schedulingRoomRepository: SchedulingRoomRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val roomAccess: RoomAccess,
    private val roomMembership: RoomMembership,
    private val publicIds: PublicIds,
) {

    // 방과 공통 캘린더를 만들고 방장을 첫 참여자로 넣는다
    @Transactional
    fun create(userId: Long, request: RoomCreateRequest): RoomDetailResponse {
        val owner = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val group = roomMembership.myGroup(userId, request.groupId)
        val timezone = validTimezone(request.timezone)
        validatePeriod(request)

        // 확정 일정을 담는 캘린더. 이름은 방 이름을 그대로 쓴다
        val calendar = calendarRepository.save(Calendar.create(request.name, null, null, timezone))
        val room = schedulingRoomRepository.save(
            SchedulingRoom.create(
                owner = owner,
                calendar = calendar,
                name = request.name,
                startDate = request.startDate,
                endDate = request.endDate,
                dailyStartTime = request.dailyStartTime,
                dailyEndTime = request.dailyEndTime,
                timezone = timezone,
            ),
        )
        roomMembership.join(room, owner, group)
        return toDetail(room, userId)
    }

    private fun toDetail(room: SchedulingRoom, userId: Long): RoomDetailResponse =
        RoomDetailResponse.from(
            room, roomMemberRepository.findActiveMembersWithUser(requireNotNull(room.id)), userId, publicIds,
        )

    // 끝 날짜는 마지막 날 포함. 기간 길이 제한은 없다 (조회 API가 from·to로 나눠 본다, PR4·5)
    // 시간 범위는 자정을 넘어도 되므로(22:00~02:00) 시작·끝 시각은 검사하지 않는다
    private fun validatePeriod(request: RoomCreateRequest) {
        if (request.endDate.isBefore(request.startDate)) {
            throw BusinessException(ErrorCode.INVALID_ROOM_PERIOD)
        }
    }

    private fun validTimezone(timezone: String): String =
        try {
            ZoneId.of(timezone).id
        } catch (e: DateTimeException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "올바르지 않은 시간대입니다: $timezone")
        }
    
}
