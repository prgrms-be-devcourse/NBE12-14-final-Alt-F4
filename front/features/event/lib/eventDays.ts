import { addDays, startOfDay } from "date-fns";
import type { EventItem } from "../types";

// 일정이 걸치는 첫날·마지막 날 (브라우저 시간대의 자정, 마지막 날 포함)
// - 종일 일정: UTC 날짜 그대로 쓴다. 시간대로 바꾸면 UTC보다 늦은 시간대에서 하루가 밀린다. endAt은 다음 날이라 하루 뺀다
// - 시간 일정: 브라우저 시간대로 바꾼다. 끝이 자정 정각이면 그 날은 포함하지 않는다
export function eventDays(event: EventItem): { first: Date; last: Date } {
  if (event.isAllDay) {
    const first = utcDateAsLocal(event.startAt);
    const last = addDays(utcDateAsLocal(event.endAt), -1);
    return { first, last: last < first ? first : last };
  }
  const start = new Date(event.startAt);
  const end = new Date(event.endAt);
  const lastMoment = end > start ? new Date(end.getTime() - 1) : start;
  return { first: startOfDay(start), last: startOfDay(lastMoment) };
}

function utcDateAsLocal(iso: string): Date {
  const date = new Date(iso);
  return new Date(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

// 반복 일정은 회차마다 id가 같아서 원래 시작까지 붙여 구분한다
export function occurrenceKey(event: EventItem): string {
  return `${event.id}:${event.occurrenceStart}`;
}

// 종일 일정 먼저, 그다음 시작이 이른 순, 시작이 같으면 긴 일정이 먼저
// (시각 문자열은 시차 표기가 달라도 맞게 비교하려고 Date로 바꾼다)
export function compareEvents(a: EventItem, b: EventItem): number {
  if (a.isAllDay !== b.isAllDay) return a.isAllDay ? -1 : 1;
  const start = Date.parse(a.startAt) - Date.parse(b.startAt);
  if (start !== 0) return start;
  return Date.parse(b.endAt) - Date.parse(a.endAt);
}
