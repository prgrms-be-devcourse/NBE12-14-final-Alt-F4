-- 조율방: 방 삭제·나가기 시각(소프트 삭제)과 중복 방지 유일 제약
ALTER TABLE scheduling_rooms
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_scheduling_rooms_calendar UNIQUE (calendar_id);
ALTER TABLE room_members
    ADD COLUMN left_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_room_members_room_user UNIQUE (room_id, user_id);
ALTER TABLE room_shared_events
    ADD CONSTRAINT uk_room_shared_events_member_event UNIQUE (member_id, event_id);
ALTER TABLE room_invite_links
    ADD CONSTRAINT uk_room_invite_links_token_hash UNIQUE (token_hash);
