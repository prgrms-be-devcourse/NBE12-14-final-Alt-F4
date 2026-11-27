import { api } from "@/shared/api/client";
import type { EventCreateBody, EventItem, EventUpdateBody, RecurrenceScope } from "./types";

// 기간 조회 (최대 93일). from·to는 toISOString()(Z)로, timezone은 보는 사람 시간대
// calendarIds를 빼면 내가 이용하는 모든 캘린더
export function fetchEvents(params: { from: string; to: string; timezone: string; calendarIds?: number[] }) {
  return api<EventItem[]>("/api/v1/events", { query: params });
}

// 상세: 반복 일정이면 회차가 아니라 시리즈(첫 회차 기준) 값이 온다
export function fetchEvent(id: number) {
  return api<EventItem>(`/api/v1/events/${id}`);
}

export function createEvent(body: EventCreateBody) {
  return api<EventItem>("/api/v1/events", { method: "POST", body });
}

// 반복 일정이면 scope와 occurrenceStart(고른 회차의 원래 시작)를 같이 보낸다
type Target = { id: number; scope?: RecurrenceScope; occurrenceStart?: string };

// version: 수정 폼을 열 때 받은 일정의 version. 그사이 다른 곳에서 고쳤으면 409 EVENT_VERSION_CONFLICT
export function updateEvent({ id, scope, occurrenceStart, version, body }: Target & { version: number; body: EventUpdateBody }) {
  return api<EventItem>(`/api/v1/events/${id}`, { method: "PATCH", body: { ...body, version }, query: { scope, occurrenceStart } });
}

export function deleteEvent({ id, scope, occurrenceStart }: Target) {
  return api<void>(`/api/v1/events/${id}`, { method: "DELETE", query: { scope, occurrenceStart } });
}
