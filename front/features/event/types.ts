// GET /api/v1/events 응답의 일정 하나 (반복 일정은 회차마다 하나씩, id는 시리즈와 같다)
// 시각은 UTC ISO 문자열 (예: "2026-11-25T09:00:00Z")
// 종일 일정: startAt·endAt이 UTC 자정이고 endAt은 끝 다음 날 (11/18~19 → 18일 00:00Z ~ 20일 00:00Z)
export type EventItem = {
  id: number;
  calendarId: number;
  categoryId: number | null;
  userId: number;
  title: string;
  description: string | null;
  location: string | null;
  startAt: string;
  endAt: string;
  isAllDay: boolean;
  timezone: string;
  rrule: string | null;
  occurrenceStart: string; // 반복 회차의 원래 시작. "이 일정만" 수정·삭제 때 그대로 보낸다
  version: number; // 충돌 감지용. 반복 회차도 시리즈의 version. 수정할 때 그대로 보낸다
  createdAt: string;
  updatedAt: string;
};

export type CalendarView = "day" | "week" | "month" | "year" | "agenda";

// 반복 일정을 고치거나 지울 때 범위 (반복 없는 일정은 보내지 않는다)
// THIS: 이 일정만(제목·설명·장소·시간만 바꿀 수 있다) / THIS_AND_FOLLOWING: 이 일정과 이후 / ALL: 모든 일정
export type RecurrenceScope = "THIS" | "THIS_AND_FOLLOWING" | "ALL";

// 생성·수정 요청 본문. 종일이면 startDate·endDate(끝 날짜 포함), 아니면 startAt·endAt(시차 포함 ISO)
export type EventUpdateBody = {
  categoryId: number | null;
  title: string;
  description: string | null;
  location: string | null;
  isAllDay: boolean;
  startAt?: string;
  endAt?: string;
  startDate?: string;
  endDate?: string;
  timezone: string;
  rrule: string | null;
};

export type EventCreateBody = EventUpdateBody & { calendarId: number };
