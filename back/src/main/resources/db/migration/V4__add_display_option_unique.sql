-- 표시 설정: 한 이용 관계는 같은 일정·같은 카테고리에 설정 줄을 하나만 가진다
ALTER TABLE event_options
    ADD CONSTRAINT uk_event_options_subscribe_event UNIQUE (subscribe_id, event_id);
ALTER TABLE category_options
    ADD CONSTRAINT uk_category_options_subscribe_category UNIQUE (subscribe_id, category_id);
