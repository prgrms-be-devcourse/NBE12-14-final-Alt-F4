import { format, isValid, parse } from "date-fns";

// 보는 사람(브라우저) 시간대 이름. 예: "Asia/Seoul"
export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

// URL의 날짜(yyyy-MM-dd) ↔ Date(브라우저 시간대 자정)
export function parseDateParam(value: string | null, fallback: Date): Date {
  if (!value) return fallback;
  const date = parse(value, "yyyy-MM-dd", fallback);
  return isValid(date) ? date : fallback;
}

export function formatDateParam(date: Date): string {
  return format(date, "yyyy-MM-dd");
}

// 시간대 이름을 브라우저 기준으로 정규화한다 (예: Asia/Kolkata ↔ Asia/Calcutta). 모르는 이름이면 그대로
export function normalizeTimeZone(zone: string): string {
  try {
    return new Intl.DateTimeFormat("en", { timeZone: zone }).resolvedOptions().timeZone;
  } catch {
    return zone;
  }
}

export function sameTimeZone(a: string, b: string): boolean {
  return a === b || normalizeTimeZone(a) === normalizeTimeZone(b);
}

// 고정 시차(+09:00, GMT+9, UTC+9, Etc/GMT-9)인지. UTC 자체는 아니다
// 백엔드는 반복하는 시간 일정에 고정 시차를 받지 않는다 (지역 이름과 UTC만)
export function isFixedOffset(zone: string): boolean {
  const name = zone.trim().toUpperCase();
  if (name === "UTC" || name === "GMT" || name === "Z" || name === "ETC/UTC" || name === "ETC/GMT") return false;
  return /^[+-]\d/.test(name) || /^(UTC|GMT|UT)[+-]\d/.test(name) || /^ETC\/GMT[+-]\d/.test(name);
}
