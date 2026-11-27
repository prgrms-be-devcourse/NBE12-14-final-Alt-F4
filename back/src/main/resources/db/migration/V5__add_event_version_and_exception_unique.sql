-- 동시성 보완: 일정 수정 충돌 감지(version), 같은 회차 예외 한 줄
-- 1) 같은 회차에 예외가 두 줄 이상이면 하나만 남긴다 (DELETED 줄 우선, 그다음 먼저 만든 줄)
--    MySQL은 같은 테이블을 서브쿼리로 읽으면서 지울 수 없어(오류 1093) 파생 테이블로 JOIN 한다
DELETE x FROM event_exceptions x
JOIN (SELECT event_id, recurrence_id,
             COALESCE(MIN(CASE WHEN type = 'DELETED' THEN id END), MIN(id)) AS keep_id
      FROM event_exceptions
      GROUP BY event_id, recurrence_id
      HAVING COUNT(*) > 1) k
  ON k.event_id = x.event_id AND k.recurrence_id = x.recurrence_id
WHERE x.id <> k.keep_id;

-- 2) 한 일정의 한 회차에는 예외 한 줄
ALTER TABLE event_exceptions
    ADD CONSTRAINT uk_event_exceptions_event_recurrence UNIQUE (event_id, recurrence_id);

-- 3) 일정 버전: 수정·회차 변경마다 1씩 오른다 (서비스가 직접 올린다, JPA @Version 아님)
--    마지막에 둔다: 앞 단계(유일 제약)에서 실패하면 이 칸이 아직 없어서, 다시 돌려도 "칸 중복"으로 실패하지 않게
ALTER TABLE events ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
