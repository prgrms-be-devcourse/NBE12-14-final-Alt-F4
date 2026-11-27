import { TZDate } from "@date-fns/tz";
import { addMonths, format, parse } from "date-fns";

// 반복 규칙(RRULE)을 화면에서 다루는 모양과 그 변환
// - 요일·몇 번째·날짜는 폼의 시작 날짜 문자열(일정 시간대의 벽시계 날짜)로 계산한다.
//   Date#getDay()를 브라우저 시간대의 순간에 쓰면 서울 월요일 08:00이 UTC 일정에서는 일요일이 되는 등 어긋난다
// - 백엔드(ical4j)는 규칙을 다시 정리해 저장하고 그 문자열로 "일정이 바뀌었는지" 비교한다
//   그래서 반복을 건드리지 않았으면 원래 문자열을 그대로 보낸다 (eventForm.ts의 valuesToBody)
// - ical4j는 WKST가 없으면 서버 JVM 언어 설정의 주 시작을 쓴다 → 요일을 고르는 주 단위 규칙에는 WKST=SU를 붙인다

export type Weekday = "SU" | "MO" | "TU" | "WE" | "TH" | "FR" | "SA";
export const WEEKDAY_CODES: readonly Weekday[] = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"];
export const WEEKDAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"] as const;
const WORK_DAYS: readonly Weekday[] = ["MO", "TU", "WE", "TH", "FR"];

export type Preset = "DAILY" | "WEEKLY" | "MONTHLY_DAY" | "MONTHLY_NTH" | "MONTHLY_LAST" | "YEARLY" | "WEEKDAYS";
export const PRESETS: readonly Preset[] = ["DAILY", "WEEKLY", "MONTHLY_DAY", "MONTHLY_NTH", "MONTHLY_LAST", "YEARLY", "WEEKDAYS"];

export type RepeatEnd =
  | { type: "NEVER" }
  | { type: "UNTIL"; date: string; raw?: string } // date: yyyy-MM-dd (폼 시간대). raw: 읽어 온 원래 UNTIL 값
  | { type: "COUNT"; count: number };

export type CustomRule = {
  interval: number; // 1~99
  unit: "DAY" | "WEEK" | "MONTH" | "YEAR";
  weekdays: Weekday[]; // unit=WEEK. 시작 요일은 항상 들어 있다
  monthly: "DAY" | "NTH" | "LAST"; // unit=MONTH
  end: RepeatEnd;
};

export type Repeat =
  | { kind: "NONE" }
  | { kind: "PRESET"; preset: Preset }
  | { kind: "CUSTOM"; rule: CustomRule }
  | { kind: "KEEP"; rrule: string }; // 이 화면으로 표현할 수 없는 기존 규칙: 글자 그대로 보낸다

// 규칙을 만들고 읽는 데 필요한 일정 정보 (UNTIL 형식이 종일 여부·시간대에 따라 다르다)
export type RuleContext = { startDate: string; isAllDay: boolean; timezone: string };

export const MAX_INTERVAL = 99;
export const MAX_COUNT = 1000;

// ===== 날짜 정보 (yyyy-MM-dd 문자열 기준, 브라우저 시간대와 상관없음) =====

function dateOf(value: string): Date {
  return parse(value, "yyyy-MM-dd", new Date(0));
}

export function weekdayOf(startDate: string): Weekday {
  return WEEKDAY_CODES[dateOf(startDate).getDay()];
}

// 그달의 몇 번째 같은 요일인지 (1~5)
export function nthOf(startDate: string): number {
  return Math.ceil(dateOf(startDate).getDate() / 7);
}

// 그달의 마지막 같은 요일인지 (7일 뒤가 다음 달이면 마지막)
export function isLastOf(startDate: string): boolean {
  const date = dateOf(startDate);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + 7).getMonth() !== date.getMonth();
}

// 매월 몇 번째 문구: 1→첫째, 2→둘째, 3→셋째, 4→넷째 (5번째는 "마지막"으로만 보인다)
export function ordinalName(n: number): string {
  return ["첫째", "둘째", "셋째", "넷째"][n - 1] ?? `${n}번째`;
}

const weekdayName = (code: Weekday) => WEEKDAY_NAMES[WEEKDAY_CODES.indexOf(code)];
const monthDay = (startDate: string) => format(dateOf(startDate), "M월 d일");
const dayOfMonth = (startDate: string) => dateOf(startDate).getDate();

// ===== 목록 항목 =====

export function presetAvailable(preset: Preset, startDate: string): boolean {
  if (preset === "MONTHLY_NTH") return nthOf(startDate) <= 4;
  if (preset === "MONTHLY_LAST") return isLastOf(startDate);
  if (preset === "WEEKDAYS") return WORK_DAYS.includes(weekdayOf(startDate));
  return true;
}

function presetRrule(preset: Preset, startDate: string): string {
  const weekday = weekdayOf(startDate);
  switch (preset) {
    case "DAILY":
      return "FREQ=DAILY";
    case "WEEKLY":
      // BYDAY 없이 보낸다: ical4j가 시작 요일을 쓰고, 기존 "FREQ=WEEKLY" 일정과 문자열이 같다
      return "FREQ=WEEKLY";
    case "MONTHLY_DAY":
      return "FREQ=MONTHLY";
    case "MONTHLY_NTH":
      return `FREQ=MONTHLY;BYDAY=${nthOf(startDate)}${weekday}`;
    case "MONTHLY_LAST":
      return `FREQ=MONTHLY;BYDAY=-1${weekday}`;
    case "YEARLY":
      return "FREQ=YEARLY";
    case "WEEKDAYS":
      return `FREQ=WEEKLY;WKST=SU;BYDAY=${WORK_DAYS.join(",")}`;
  }
}

export function presetLabel(preset: Preset, startDate: string): string {
  const weekday = weekdayName(weekdayOf(startDate));
  switch (preset) {
    case "DAILY":
      return "매일";
    case "WEEKLY":
      return `매주 ${weekday}요일`;
    case "MONTHLY_DAY":
      return `매월 ${dayOfMonth(startDate)}일`;
    case "MONTHLY_NTH":
      return `매월 ${ordinalName(nthOf(startDate))} ${weekday}요일`;
    case "MONTHLY_LAST":
      return `매월 마지막 ${weekday}요일`;
    case "YEARLY":
      return `매년 ${monthDay(startDate)}`;
    case "WEEKDAYS":
      return "주중 매일(월-금)";
  }
}

// ===== 맞춤 규칙 =====

export function defaultCustomRule(startDate: string): CustomRule {
  return { interval: 1, unit: "WEEK", weekdays: [weekdayOf(startDate)], monthly: "DAY", end: { type: "NEVER" } };
}

// 종료 날짜 기본값: 시작 + 3개월 (구글과 같음)
export function defaultUntilDate(startDate: string): string {
  return format(addMonths(dateOf(startDate), 3), "yyyy-MM-dd");
}

export function presetToCustom(preset: Preset, startDate: string): CustomRule {
  const base = defaultCustomRule(startDate);
  switch (preset) {
    case "DAILY":
      return { ...base, unit: "DAY" };
    case "WEEKLY":
      return base;
    case "WEEKDAYS":
      return { ...base, weekdays: [...WORK_DAYS] };
    case "MONTHLY_DAY":
      return { ...base, unit: "MONTH", monthly: "DAY" };
    case "MONTHLY_NTH":
      return { ...base, unit: "MONTH", monthly: "NTH" };
    case "MONTHLY_LAST":
      return { ...base, unit: "MONTH", monthly: "LAST" };
    case "YEARLY":
      return { ...base, unit: "YEAR" };
  }
}

// 맞춤 규칙이 목록 항목과 같으면 그 항목으로 (예: 1주·시작 요일만·종료 없음 → 매주 ○요일)
export function simplifyCustom(rule: CustomRule, startDate: string): Repeat {
  if (rule.interval === 1 && rule.end.type === "NEVER") {
    const start = weekdayOf(startDate);
    if (rule.unit === "DAY") return { kind: "PRESET", preset: "DAILY" };
    if (rule.unit === "YEAR") return { kind: "PRESET", preset: "YEARLY" };
    if (rule.unit === "MONTH") {
      return { kind: "PRESET", preset: rule.monthly === "NTH" ? "MONTHLY_NTH" : rule.monthly === "LAST" ? "MONTHLY_LAST" : "MONTHLY_DAY" };
    }
    if (rule.weekdays.length === 1 && rule.weekdays[0] === start) return { kind: "PRESET", preset: "WEEKLY" };
    if (rule.weekdays.length === WORK_DAYS.length && WORK_DAYS.every((day) => rule.weekdays.includes(day))) {
      return { kind: "PRESET", preset: "WEEKDAYS" };
    }
  }
  return { kind: "CUSTOM", rule };
}

const sortWeekdays = (days: Weekday[]) => [...new Set(days)].sort((a, b) => WEEKDAY_CODES.indexOf(a) - WEEKDAY_CODES.indexOf(b));

// 시작 날짜를 옮길 때: 주 단위면 옛 시작 요일을 빼고 새 시작 요일을 넣는다 (사용자가 따로 켠 요일은 그대로)
export function moveCustomStart(rule: CustomRule, oldStart: string, newStart: string): CustomRule {
  if (rule.unit !== "WEEK") return rule;
  const oldDay = weekdayOf(oldStart);
  const newDay = weekdayOf(newStart);
  if (oldDay === newDay) return rule;
  return { ...rule, weekdays: sortWeekdays([...rule.weekdays.filter((day) => day !== oldDay), newDay]) };
}

function untilValue(end: Extract<RepeatEnd, { type: "UNTIL" }>, context: RuleContext): string {
  // 날짜를 바꾸지 않았으면 원래 값 그대로 ("이 일정과 이후"로 자른 시리즈는 "고른 회차 1초 전"이라 다시 만들면 그 회차가 되살아난다)
  if (end.raw && untilToDate(end.raw, context) === end.date && /T/.test(end.raw) !== context.isAllDay) return end.raw;
  if (context.isAllDay) return end.date.replaceAll("-", "");
  const [year, month, day] = end.date.split("-").map(Number);
  const lastMoment = new TZDate(year, month - 1, day, 23, 59, 59, context.timezone);
  return format(new TZDate(lastMoment.getTime(), "UTC"), "yyyyMMdd'T'HHmmss'Z'");
}

// UNTIL 값 → 폼 시간대의 날짜 (종일은 그 날짜 그대로)
export function untilToDate(raw: string, context: RuleContext): string {
  const day = /^(\d{4})(\d{2})(\d{2})/.exec(raw);
  if (!day) return context.startDate;
  const timed = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/.exec(raw);
  if (!timed || context.isAllDay) return `${day[1]}-${day[2]}-${day[3]}`;
  const [, y, mo, d, h, mi, s] = timed.map(Number);
  return format(new TZDate(Date.UTC(y, mo - 1, d, h, mi, s), context.timezone), "yyyy-MM-dd");
}

function customRrule(rule: CustomRule, context: RuleContext): string {
  const freq = { DAY: "DAILY", WEEK: "WEEKLY", MONTH: "MONTHLY", YEAR: "YEARLY" }[rule.unit];
  const parts = [`FREQ=${freq}`];
  const weekdays = sortWeekdays(rule.weekdays);
  const multiDay = rule.unit === "WEEK" && !(weekdays.length === 1 && weekdays[0] === weekdayOf(context.startDate));
  if (multiDay) parts.push("WKST=SU");
  if (rule.end.type === "UNTIL") parts.push(`UNTIL=${untilValue(rule.end, context)}`);
  if (rule.end.type === "COUNT") parts.push(`COUNT=${rule.end.count}`);
  if (rule.interval > 1) parts.push(`INTERVAL=${rule.interval}`);
  if (multiDay) parts.push(`BYDAY=${weekdays.join(",")}`);
  if (rule.unit === "MONTH" && rule.monthly !== "DAY") {
    const weekday = weekdayOf(context.startDate);
    parts.push(`BYDAY=${rule.monthly === "LAST" ? "-1" : nthOf(context.startDate)}${weekday}`);
  }
  return parts.join(";");
}

export function toRrule(repeat: Repeat, context: RuleContext): string | null {
  switch (repeat.kind) {
    case "NONE":
      return null;
    case "KEEP":
      return repeat.rrule;
    case "PRESET":
      return presetRrule(repeat.preset, context.startDate);
    case "CUSTOM":
      return customRrule(repeat.rule, context);
  }
}

// ===== 문구 =====

function describeCustom(rule: CustomRule, startDate: string): string {
  const every = rule.interval > 1;
  const weekday = weekdayName(weekdayOf(startDate));
  let base: string;
  if (rule.unit === "DAY") base = every ? `${rule.interval}일마다` : "매일";
  else if (rule.unit === "WEEK") {
    const days = sortWeekdays(rule.weekdays).map(weekdayName).join("·");
    base = `${every ? `${rule.interval}주마다` : "매주"} ${days}요일`;
  } else if (rule.unit === "MONTH") {
    const which = rule.monthly === "NTH" ? `${ordinalName(nthOf(startDate))} ${weekday}요일` : rule.monthly === "LAST" ? `마지막 ${weekday}요일` : `${dayOfMonth(startDate)}일`;
    base = `${every ? `${rule.interval}개월마다` : "매월"} ${which}`;
  } else base = `${every ? `${rule.interval}년마다` : "매년"} ${monthDay(startDate)}`;

  if (rule.end.type === "COUNT") return `${base}, ${rule.end.count}회`;
  if (rule.end.type === "UNTIL") return `${base}, ${format(dateOf(rule.end.date), "yyyy년 M월 d일")}까지`;
  return base;
}

export function describeRepeat(repeat: Repeat, startDate: string): string {
  switch (repeat.kind) {
    case "NONE":
      return "반복 안 함";
    case "KEEP":
      return `반복 (${repeat.rrule})`;
    case "PRESET":
      return presetLabel(repeat.preset, startDate);
    case "CUSTOM":
      return describeCustom(repeat.rule, startDate);
  }
}

// 지금 날짜에서 쓸 수 없는 반복이면 그 이유 (보내기 전에 막는다)
export function repeatProblem(repeat: Repeat, startDate: string): string | null {
  if (repeat.kind === "PRESET" && !presetAvailable(repeat.preset, startDate)) return "이 날짜에는 이 반복을 쓸 수 없어요.";
  if (repeat.kind !== "CUSTOM") return null;
  const { rule } = repeat;
  if (!(rule.interval >= 1 && rule.interval <= MAX_INTERVAL)) return `반복 주기는 1~${MAX_INTERVAL} 사이여야 해요.`;
  if (rule.unit === "MONTH" && rule.monthly === "NTH" && nthOf(startDate) > 4) return "이 날짜에는 이 반복을 쓸 수 없어요.";
  if (rule.unit === "MONTH" && rule.monthly === "LAST" && !isLastOf(startDate)) return "이 날짜에는 이 반복을 쓸 수 없어요.";
  if (rule.unit === "WEEK" && !rule.weekdays.includes(weekdayOf(startDate))) return "시작 날짜의 요일이 반복 요일에 있어야 해요.";
  if (rule.end.type === "UNTIL" && rule.end.date < startDate) return "종료 날짜는 시작 날짜와 같거나 뒤여야 해요.";
  if (rule.end.type === "COUNT" && !(rule.end.count >= 1 && rule.end.count <= MAX_COUNT)) return `반복 횟수는 1~${MAX_COUNT} 사이여야 해요.`;
  return null;
}

// ===== 저장된 규칙 읽기 =====

const KNOWN_KEYS = new Set(["FREQ", "INTERVAL", "BYDAY", "WKST", "UNTIL", "COUNT", "BYMONTHDAY", "BYMONTH"]);

function partsOf(rrule: string): Map<string, string> {
  const parts = new Map<string, string>();
  for (const part of rrule.trim().replace(/^RRULE:/i, "").toUpperCase().split(";")) {
    if (!part) continue;
    const [key, value = ""] = part.split("=", 2);
    parts.set(key, value);
  }
  return parts;
}

// 저장된 규칙 → 화면 모양. 화면으로 표현할 수 없으면 KEEP (글자 그대로 보낸다)
export function parseRrule(rrule: string | null, context: RuleContext): Repeat {
  if (!rrule) return { kind: "NONE" };
  const keep: Repeat = { kind: "KEEP", rrule };
  const parts = partsOf(rrule);
  if ([...parts.keys()].some((key) => !KNOWN_KEYS.has(key))) return keep;
  if (parts.has("WKST") && parts.get("WKST") !== "SU") return keep;
  if (parts.has("UNTIL") && parts.has("COUNT")) return keep;

  const startDay = weekdayOf(context.startDate);
  const interval = parts.has("INTERVAL") ? Number(parts.get("INTERVAL")) : 1;
  if (!Number.isInteger(interval) || interval < 1 || interval > MAX_INTERVAL) return keep;

  // 시작일과 같은 BYMONTHDAY(매월·매년)·BYMONTH(매년)는 없는 것과 같다
  const freq = parts.get("FREQ");
  const date = dateOf(context.startDate);
  if (parts.has("BYMONTHDAY") && !((freq === "MONTHLY" || freq === "YEARLY") && Number(parts.get("BYMONTHDAY")) === date.getDate())) return keep;
  if (parts.has("BYMONTH") && !(freq === "YEARLY" && Number(parts.get("BYMONTH")) === date.getMonth() + 1)) return keep;

  let end: RepeatEnd = { type: "NEVER" };
  if (parts.has("COUNT")) {
    const count = Number(parts.get("COUNT"));
    if (!Number.isInteger(count) || count < 1 || count > MAX_COUNT) return keep;
    end = { type: "COUNT", count };
  } else if (parts.has("UNTIL")) {
    const raw = parts.get("UNTIL") ?? "";
    end = { type: "UNTIL", date: untilToDate(raw, context), raw };
  }

  const byDay = parts.get("BYDAY");
  const base = { interval, weekdays: [startDay], monthly: "DAY" as const, end };
  let rule: CustomRule;
  if (freq === "DAILY" && !byDay) rule = { ...base, unit: "DAY" };
  else if (freq === "YEARLY" && !byDay) rule = { ...base, unit: "YEAR" };
  else if (freq === "WEEKLY") {
    const days = byDay ? byDay.split(",") : [startDay];
    if (!days.every((day): day is Weekday => WEEKDAY_CODES.includes(day as Weekday))) return keep;
    // 시작일이 첫 회차여야 한다 (서버 검사와 같음)
    if (!days.includes(startDay)) return keep;
    rule = { ...base, unit: "WEEK", weekdays: sortWeekdays(days) };
  } else if (freq === "MONTHLY") {
    if (!byDay) rule = { ...base, unit: "MONTH" };
    else {
      const match = /^(-1|[1-4])(SU|MO|TU|WE|TH|FR|SA)$/.exec(byDay);
      if (!match || match[2] !== startDay) return keep;
      if (match[1] === "-1" && isLastOf(context.startDate)) rule = { ...base, unit: "MONTH", monthly: "LAST" };
      else if (Number(match[1]) === nthOf(context.startDate)) rule = { ...base, unit: "MONTH", monthly: "NTH" };
      else return keep;
    }
  } else return keep;

  return simplifyCustom(rule, context.startDate);
}

// 원래 규칙에 COUNT가 있는지 ("이 일정과 이후" 남은 횟수는 계산하지 않고 막는다)
export function hasCount(rrule: string | null): boolean {
  return rrule !== null && partsOf(rrule).has("COUNT");
}

// 원래 규칙에 UNTIL이 있는지 (기존 규칙 유지 중 종일 여부를 바꾸면 형식이 맞지 않는다)
export function hasUntil(rrule: string | null): boolean {
  return rrule !== null && partsOf(rrule).has("UNTIL");
}
