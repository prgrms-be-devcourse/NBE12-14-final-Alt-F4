import { TZDate } from "@date-fns/tz";
import { addHours, format, isSameDay, parse, startOfHour } from "date-fns";
import { browserTimeZone, isFixedOffset } from "@/shared/lib/date";
import type { EventItem, EventUpdateBody } from "../types";
import { hasUntil, moveCustomStart, parseRrule, repeatProblem, toRrule, type Repeat, type RuleContext } from "./recurrence";

// 폼에서 다루는 값
// 날짜·시간은 폼 시간대(timezone)의 벽시계 값이다 (예: 뉴욕 일정이면 뉴욕의 10:00)
// 종일 일정은 날짜만 쓰고, 날짜는 UTC 날짜 그대로다 (응답의 startAt·endAt이 UTC 자정)
export type EventFormValues = {
  title: string;
  calendarId: number;
  categoryId: number | null;
  isAllDay: boolean;
  startDate: string; // yyyy-MM-dd
  startTime: string; // HH:mm (종일이면 안 씀)
  endDate: string; // 종일이면 끝 날짜 포함
  endTime: string;
  timezone: string; // 일정 시간대 (IANA 이름)
  location: string;
  description: string;
  repeat: Repeat;
};

export const ruleContext = (values: Pick<EventFormValues, "startDate" | "isAllDay" | "timezone">): RuleContext => ({
  startDate: values.startDate,
  isAllDay: values.isAllDay,
  timezone: values.timezone,
});

// 새 일정 기본값: 고른 날이 오늘이면 다음 정시부터 1시간 (23시 이후면 23:00~23:59), 다른 날이면 09:00~10:00
// 시간대는 브라우저 시간대
// options: 시간 격자의 빈 칸(start·end) 또는 종일 줄(allDay)을 눌러 열 때
export function newEventValues(
  day: Date,
  calendarId: number,
  now: Date,
  options: { start?: string; end?: string; allDay?: boolean } = {},
): EventFormValues {
  const date = format(day, "yyyy-MM-dd");
  let startTime = "09:00";
  let endTime = "10:00";
  if (options.start) {
    startTime = options.start;
    endTime = options.end ?? format(addHours(parse(options.start, "HH:mm", day), 1), "HH:mm");
  } else if (isSameDay(day, now)) {
    const next = addHours(startOfHour(now), 1);
    if (isSameDay(next, now) && next.getHours() < 23) {
      startTime = format(next, "HH:mm");
      endTime = format(addHours(next, 1), "HH:mm");
    } else {
      startTime = "23:00";
      endTime = "23:59";
    }
  }
  return {
    title: "",
    calendarId,
    categoryId: null,
    isAllDay: options.allDay ?? false,
    startDate: date,
    startTime,
    endDate: date,
    endTime,
    timezone: browserTimeZone(),
    location: "",
    description: "",
    repeat: { kind: "NONE" },
  };
}

// 순간 → 그 시간대의 벽시계. 시간대 이름을 모르면 브라우저 시간대로
function wallClock(iso: string, timezone: string): TZDate {
  try {
    return new TZDate(Date.parse(iso), timezone);
  } catch {
    return new TZDate(Date.parse(iso), browserTimeZone());
  }
}

// 기존 일정으로 폼 채우기
// 시간 일정은 일정 시간대로 바꿔서, 종일은 UTC 날짜 그대로(끝은 하루 뺌, 응답의 endAt은 끝 다음 날)
export function eventToValues(event: EventItem): EventFormValues {
  const base = {
    title: event.title,
    calendarId: event.calendarId,
    categoryId: event.categoryId,
    isAllDay: event.isAllDay,
    timezone: event.timezone,
    location: event.location ?? "",
    description: event.description ?? "",
  };
  if (event.isAllDay) {
    const start = utcDateString(event.startAt);
    const last = utcDateString(new Date(Date.parse(event.endAt) - 86_400_000).toISOString());
    const startDate = start;
    const endDate = last < start ? start : last;
    return {
      ...base,
      startDate,
      endDate,
      startTime: "09:00",
      endTime: "10:00",
      repeat: parseRrule(event.rrule, { startDate, isAllDay: true, timezone: event.timezone }),
    };
  }
  const start = wallClock(event.startAt, event.timezone);
  const end = wallClock(event.endAt, event.timezone);
  const startDate = format(start, "yyyy-MM-dd");
  return {
    ...base,
    startDate,
    startTime: format(start, "HH:mm"),
    endDate: format(end, "yyyy-MM-dd"),
    endTime: format(end, "HH:mm"),
    repeat: parseRrule(event.rrule, { startDate, isAllDay: false, timezone: event.timezone }),
  };
}

function utcDateString(iso: string): string {
  return iso.slice(0, 10);
}

// 벽시계 값 → 분 단위 수 (서머타임과 상관없이 벽시계로 길이를 잰다)
function wallMinutes(date: string, time: string): number {
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  return Date.UTC(year, month - 1, day, hour, minute) / 60_000;
}

function fromWallMinutes(minutes: number): { date: string; time: string } {
  const iso = new Date(minutes * 60_000).toISOString();
  return { date: iso.slice(0, 10), time: iso.slice(11, 16) };
}

// 시작을 옮기면 끝도 원래 길이만큼 같이 옮긴다 (종일은 날짜 수, 시간 일정은 벽시계 길이)
// 맞춤 반복(주 단위)이면 옛 시작 요일을 빼고 새 시작 요일을 넣는다
// 원래 값이 맞지 않으면(끝이 시작보다 앞 등) 시작만 바꾼다
export function moveStart(values: EventFormValues, field: "startDate" | "startTime", value: string): EventFormValues {
  const next = { ...values, [field]: value };
  if (field === "startDate" && values.repeat.kind === "CUSTOM" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    next.repeat = { kind: "CUSTOM", rule: moveCustomStart(values.repeat.rule, values.startDate, value) };
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(next.startDate)) return next;
  if (values.isAllDay) {
    const days = (wallMinutes(values.endDate, "00:00") - wallMinutes(values.startDate, "00:00")) / 1440;
    if (!(days >= 0)) return next;
    return { ...next, endDate: fromWallMinutes(wallMinutes(next.startDate, "00:00") + days * 1440).date };
  }
  const duration = wallMinutes(values.endDate, values.endTime) - wallMinutes(values.startDate, values.startTime);
  if (!(duration > 0) || !/^\d{2}:\d{2}$/.test(next.startTime)) return next;
  const end = fromWallMinutes(wallMinutes(next.startDate, next.startTime) + duration);
  return { ...next, endDate: end.date, endTime: end.time };
}

// 두 날짜(yyyy-MM-dd) 사이의 날 수 (b - a)
export function dayDiff(a: string, b: string): number {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(a) || !/^\d{4}-\d{2}-\d{2}$/.test(b)) return 0;
  return Math.round((wallMinutes(b, "00:00") - wallMinutes(a, "00:00")) / 1440);
}

// 끝 날짜 칸이 접혀 있을 때의 끝 날짜: 끝 시간이 시작보다 뒤면 시작 날짜, 이르거나 같으면 다음 날
export function collapsedEndDate(startDate: string, startTime: string, endTime: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) return startDate;
  if (endTime > startTime) return startDate;
  return fromWallMinutes(wallMinutes(startDate, "00:00") + 1440).date;
}

// 일정 값(회차 계산에 쓰이는 값)이 처음과 달라졌는지
// "모든 일정" 수정에서 이것이 바뀌면 서버가 예외 회차를 지운다 → 저장 전에 묻는다
export function scheduleChanged(values: EventFormValues, initial: EventFormValues): boolean {
  const pick = (v: EventFormValues) =>
    JSON.stringify([v.isAllDay, v.startDate, v.endDate, v.isAllDay ? "" : v.startTime, v.isAllDay ? "" : v.endTime, v.timezone, v.repeat]);
  return pick(values) !== pick(initial);
}

// 반복을 다시 만들지: 반복·시작 날짜·종일·시간대를 하나도 안 바꿨으면 원래 문자열을 그대로 보낸다
// (서버는 정리한 문자열끼리 비교해 다르면 예외 회차를 지운다)
function repeatTouched(values: EventFormValues, initial: EventFormValues): boolean {
  return (
    JSON.stringify(values.repeat) !== JSON.stringify(initial.repeat) ||
    values.startDate !== initial.startDate ||
    values.isAllDay !== initial.isAllDay ||
    values.timezone !== initial.timezone
  );
}

// 폼 값 → 요청 본문. 맞지 않으면 에러 문구를 돌려준다
// 시간 일정의 시각은 폼 시간대의 벽시계 값으로 만든 순간을 시차와 함께 보낸다 (예: 2026-11-09T10:00:00.000-05:00)
export function valuesToBody(
  values: EventFormValues,
  initial: EventFormValues,
  originalRrule: string | null,
): { body: EventUpdateBody } | { error: string } {
  const title = values.title.trim();
  if (!title) return { error: "제목을 입력해 주세요." };

  const problem = repeatProblem(values.repeat, values.startDate);
  if (problem) return { error: problem };
  if (values.repeat.kind !== "NONE" && !values.isAllDay && isFixedOffset(values.timezone)) {
    return { error: "반복 일정은 지역 시간대(예: Asia/Seoul)만 쓸 수 있어요." };
  }
  if (values.repeat.kind === "KEEP" && hasUntil(values.repeat.rrule) && values.isAllDay !== initial.isAllDay) {
    return { error: "종료 날짜가 있는 반복은 종일 여부를 바꿀 수 없어요. 반복을 다시 골라 주세요." };
  }

  const common = {
    categoryId: values.categoryId,
    title,
    description: values.description.trim() || null,
    location: values.location.trim() || null,
    isAllDay: values.isAllDay,
    timezone: values.timezone,
    rrule: repeatTouched(values, initial) ? toRrule(values.repeat, ruleContext(values)) : originalRrule,
  };

  if (values.isAllDay) {
    if (!values.startDate || !values.endDate) return { error: "날짜를 입력해 주세요." };
    if (values.endDate < values.startDate) return { error: "끝 날짜가 시작 날짜보다 앞설 수 없어요." };
    return { body: { ...common, startDate: values.startDate, endDate: values.endDate } };
  }

  const start = toInstant(values.startDate, values.startTime, values.timezone);
  const end = toInstant(values.endDate, values.endTime, values.timezone);
  if (!start || !end) return { error: "날짜와 시간을 입력해 주세요." };
  if (end.getTime() <= start.getTime()) return { error: "끝 시각은 시작 시각보다 뒤여야 해요." };
  return { body: { ...common, startAt: start.toISOString(), endAt: end.toISOString() } };
}

// 그 시간대의 벽시계 값 → 순간. 서머타임으로 없는 시각(예: 뉴욕 3월 02:30)은 TZDate가 앞으로 민다 (서버 Java와 같음)
function toInstant(date: string, time: string, timezone: string): TZDate | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) return null;
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  try {
    const instant = new TZDate(year, month - 1, day, hour, minute, 0, timezone);
    return Number.isNaN(instant.getTime()) ? null : instant;
  } catch {
    return null;
  }
}
