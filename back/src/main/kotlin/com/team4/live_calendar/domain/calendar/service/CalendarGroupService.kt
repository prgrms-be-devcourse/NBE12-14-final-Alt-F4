package com.team4.live_calendar.domain.calendar.service

import com.team4.live_calendar.domain.calendar.dto.CalendarGroupRequest
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupResponse
import com.team4.live_calendar.domain.calendar.dto.CalendarGroupWithCalendarsResponse
import com.team4.live_calendar.domain.calendar.dto.GroupCalendarResponse
import com.team4.live_calendar.domain.calendar.entity.CalendarGroup
import com.team4.live_calendar.domain.calendar.repository.CalendarGroupRepository
import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.user.repository.UserRepository
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 그룹은 각 사용자가 내 화면에서 캘린더를 정리하는 개인 폴더다
// 캘린더를 어느 그룹에 둘지는 사람마다 subscribes.group_id에 있다
@Service
@Transactional(readOnly = true)
class CalendarGroupService(
    private val calendarGroupRepository: CalendarGroupRepository,
    private val subscribeRepository: SubscribeRepository,
    private val userRepository: UserRepository,
) {

    // 내 그룹과 그 안의 내 캘린더. 쿼리는 그룹 한 번, 이용 관계 + 캘린더 한 번
    fun findMyGroups(userId: Long): List<CalendarGroupWithCalendarsResponse> {
        val groups = calendarGroupRepository.findAllByUserIdOrdered(userId)
        // 지연 로딩 프록시의 id(group.id)는 쿼리를 더 만들지 않는다
        // 내 그룹 목록에 없는 그룹을 가리키는 이용 관계는 설계상 생기지 않으며, 생기더라도 응답에서 빠진다
        val calendarsByGroupId = subscribeRepository.findAllWithCalendarByUserId(userId)
            .groupBy({ it.group.id }, { GroupCalendarResponse.from(it) })
        return groups.map { CalendarGroupWithCalendarsResponse.from(it, calendarsByGroupId[it.id].orEmpty()) }
    }

    // 새 그룹은 내 그룹 중 가장 뒤 (가장 큰 순서 + 1, 그룹이 없으면 0)
    // 동시에 두 개를 만들면 같은 순서가 나올 수 있지만, 순서가 같으면 id로 정렬하므로 막지 않는다
    @Transactional
    fun create(userId: Long, request: CalendarGroupRequest): CalendarGroupResponse {
        val displayOrder = calendarGroupRepository.findMaxDisplayOrder(userId)?.plus(1) ?: 0
        val group = CalendarGroup.create(userRepository.getReferenceById(userId), request.name, displayOrder)
        return CalendarGroupResponse.from(calendarGroupRepository.saveAndFlush(group))
    }

    @Transactional
    fun rename(userId: Long, groupId: Long, request: CalendarGroupRequest): CalendarGroupResponse {
        val group = findMyGroup(userId, groupId)
        group.rename(request.name)
        return CalendarGroupResponse.from(calendarGroupRepository.saveAndFlush(group))
    }

    // 캘린더(이용 관계)가 든 그룹은 지울 수 없다. 빈 그룹은 마지막 하나여도 지울 수 있다
    // 확인과 삭제 사이에 다른 요청이 이 그룹에 캘린더를 넣으면 FK 때문에 삭제가 실패한다 (드물어서 막지 않음)
    @Transactional
    fun delete(userId: Long, groupId: Long) {
        val group = findMyGroup(userId, groupId)
        if (subscribeRepository.existsByGroupId(groupId)) {
            throw BusinessException(ErrorCode.GROUP_NOT_EMPTY)
        }
        calendarGroupRepository.delete(group)
    }

    // 없는 그룹과 남의 그룹은 같은 404 (남의 그룹이 있는지 알려주지 않는다)
    private fun findMyGroup(userId: Long, groupId: Long): CalendarGroup =
        calendarGroupRepository.findByIdAndUserId(groupId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
}
