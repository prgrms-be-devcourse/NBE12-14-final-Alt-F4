package com.team4.live_calendar

import com.team4.live_calendar.global.config.JpaConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals

// 엔티티로 만들어진 테이블이 ERD(docs/erd-final.sql)와 같은지 확인
// 컬럼 이름 뒤 ?는 NULL 허용 컬럼
@DataJpaTest
@Import(JpaConfig::class)
class ErdMappingTest(
    @Autowired private val em: EntityManager,
) {

    private val erd = mapOf(
        "users" to "id email? password_hash? nickname created_at updated_at",
        "share_calendar_connection" to "id share_id calendar_id display_order? created_at",
        "tag" to "id name created_at? updated_at?",
        "event_exceptions" to "id event_id recurrence_id type title? description? location? start_at? end_at? created_at updated_at",
        "calendar_groups" to "id user_id name display_order? is_visible? created_at updated_at",
        "event_options" to "id subscribe_id event_id is_visible created_at updated_at calendar_id",
        "event_categories" to "id calendar_id name color? created_at updated_at",
        "category_options" to "id subscribe_id category_id calendar_id is_visible created_at updated_at",
        "calendars" to "id name description? color? timezone created_at updated_at",
        "events" to "id calendar_id category_id? user_id uid title description? location? start_at end_at is_all_day timezone rrule? recur_until? created_at updated_at",
        "room_invite_links" to "id room_id token_hash revoked_at? created_at user_id",
        "room_shared_events" to "id member_id display_mode created_at updated_at event_id",
        "calendar_invitations" to "id calendar_id user_id token_hash role expires_at status created_at",
        "notifications" to "id user_id calendar_id? type params? is_read created_at",
        "room_messages" to "id member_id content created_at",
        "scheduling_rooms" to "id name start_date end_date daily_start_time daily_end_time timezone created_at updated_at owner_id calendar_id",
        "share_tag_connection" to "id share_id tag_id created_at",
        "room_invitations" to "id room_id status responded_at? created_at updated_at inviter_id invitee_id",
        "room_members" to "id room_id joined_at user_id",
        "subscribes" to "id user_id group_id calendar_id role color? is_visible created_at updated_at",
        "share_info" to "id user_id title group_name? content? created_at updated_at",
    )

    @Test
    fun `모든 테이블의 컬럼 이름과 NULL 허용 여부가 ERD와 같다`() {
        val rows = em.createNativeQuery(
            "SELECT LOWER(table_name), LOWER(column_name), is_nullable " +
                "FROM information_schema.columns WHERE table_schema = 'PUBLIC'",
        ).resultList.map { it as Array<*> }

        val actual = rows
            .groupBy({ it[0] as String }, { (it[1] as String) + if (it[2] == "YES") "?" else "" })
            .filterKeys { it in erd }
            .mapValues { it.value.toSet() }
        val expected = erd.mapValues { it.value.split(" ").toSet() }

        assertEquals(expected.keys, actual.keys, "ERD 테이블 중 엔티티가 없는 테이블이 있습니다")
        expected.forEach { (table, columns) -> assertEquals(columns, actual[table], "$table 컬럼이 ERD와 다릅니다") }
    }
}
