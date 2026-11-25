-- #37 공유 페이지 등록: 공개 여부, 같은 공유 페이지에 같은 캘린더 중복 연결 방지
ALTER TABLE share_info
    ADD COLUMN is_public BIT(1) NOT NULL DEFAULT b'0';
ALTER TABLE share_calendar_connection
    ADD CONSTRAINT uk_share_calendar_connection_share_calendar UNIQUE (share_id, calendar_id);