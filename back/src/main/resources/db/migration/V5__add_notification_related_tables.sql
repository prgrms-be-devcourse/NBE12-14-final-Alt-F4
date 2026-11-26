-- =====================================================
-- 1. 일정 변경 이력 테이블
-- =====================================================

CREATE TABLE event_histories
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,

    calendar_id   BIGINT       NOT NULL,
    event_id      BIGINT       NOT NULL,
    actor_id      BIGINT       NOT NULL,

    target_type   VARCHAR(30)  NOT NULL,
    recurrence_id DATETIME NULL,
    action_type   VARCHAR(20)  NOT NULL,

    event_title   VARCHAR(200) NOT NULL,

    before_data   JSON NULL,
    after_data    JSON NULL,

    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX         idx_event_histories_calendar_created (
        calendar_id,
        created_at
    ),

    INDEX         idx_event_histories_event (
        event_id,
        created_at
    ),

    INDEX         idx_event_histories_actor (
        actor_id
    ),

    INDEX         idx_event_histories_recurrence (
        event_id,
        recurrence_id
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4;


-- =====================================================
-- 2. 알림 Outbox 테이블
-- =====================================================

CREATE TABLE notification_outbox
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,

    history_id    BIGINT      NOT NULL,

    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    retry_count   INT         NOT NULL DEFAULT 0,

    next_retry_at DATETIME NULL,
    locked_at     DATETIME NULL,
    processed_at  DATETIME NULL,

    error_message TEXT NULL,

    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_notification_outbox_history (
        history_id
        ),

    INDEX         idx_notification_outbox_pending (
        status,
        next_retry_at,
        id
    ),

    INDEX         idx_notification_outbox_locked (
        status,
        locked_at
    ),

    CONSTRAINT fk_notification_outbox_history
        FOREIGN KEY (history_id)
            REFERENCES event_histories (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4;


-- =====================================================
-- 3. 기존 notifications 테이블 변경
-- =====================================================

ALTER TABLE notifications
    ADD COLUMN event_history_id BIGINT NULL
        COMMENT '관련 일정 변경 이력 ID';

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_event_history
        FOREIGN KEY (event_history_id)
            REFERENCES event_histories (id);

ALTER TABLE notifications
    ADD UNIQUE KEY uk_notifications_user_history (
    user_id,
    event_history_id
    );

ALTER TABLE notifications
    ADD INDEX idx_notifications_user_read_created (
        user_id,
        is_read,
        created_at
    );


-- =====================================================
-- 4. 기존 subscribes 테이블 변경
-- =====================================================

ALTER TABLE subscribes
    ADD COLUMN notification_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '캘린더 알림 수신 여부';

ALTER TABLE subscribes
    ADD INDEX idx_subscribes_calendar_user (
        calendar_id,
        user_id
    );