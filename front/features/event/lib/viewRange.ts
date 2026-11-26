import { addDays, addMonths, addWeeks, addYears, endOfWeek, format, isSameMonth, isSameYear, startOfDay, startOfMonth, startOfWeek } from "date-fns";
import { ko } from "date-fns/locale";
import type { CalendarView } from "../types";

// 보기별 규칙: 제목, ‹ › 이동, 주 날짜. 주는 일요일 시작 (월 보기·미니 달력과 같음)
export const VIEWS: readonly CalendarView[] = ["day", "week", "month", "year", "agenda"];
export const VIEW_LABELS: Record<CalendarView, string> = { day: "일", week: "주", month: "월", year: "연도", agenda: "일정" };
export const VIEW_KEYS: Record<CalendarView, string> = { day: "D", week: "W", month: "M", year: "Y", agenda: "A" };

export function toView(value: string | null): CalendarView {
  return VIEWS.includes(value as CalendarView) ? (value as CalendarView) : "month";
}

export function weekDays(date: Date): Date[] {
  const start = startOfWeek(date, { weekStartsOn: 0 });
  return Array.from({ length: 7 }, (_, index) => addDays(start, index));
}

// 일정 보기: 고른 날짜부터 1년 (끝은 1년 뒤 0시, 그 전날까지 보인다)
export function agendaEnd(start: Date): Date {
  return addYears(startOfDay(start), 1);
}

const yearMonth = (date: Date) => format(date, "yyyy년 M월");

function spanTitle(start: Date, end: Date): string {
  if (isSameMonth(start, end)) return yearMonth(start);
  if (isSameYear(start, end)) return `${yearMonth(start)} – ${format(end, "M월")}`;
  return `${yearMonth(start)} – ${yearMonth(end)}`;
}

export function viewTitle(view: CalendarView, date: Date): string {
  switch (view) {
    case "day":
      return format(date, "yyyy년 M월 d일 (EEE)", { locale: ko });
    case "week":
      return spanTitle(startOfWeek(date, { weekStartsOn: 0 }), endOfWeek(date, { weekStartsOn: 0 }));
    case "month":
      return yearMonth(date);
    case "year":
      return format(date, "yyyy");
    case "agenda":
      return spanTitle(date, addDays(agendaEnd(date), -1));
  }
}

// ‹ › 한 번의 이동. 월·일정 보기는 그달 1일로 맞춘다 (1/31 → 2/28 → 3/28로 밀리지 않게)
export function stepDate(view: CalendarView, date: Date, amount: 1 | -1): Date {
  switch (view) {
    case "day":
      return addDays(date, amount);
    case "week":
      return addWeeks(date, amount);
    case "month":
    case "agenda":
      return startOfMonth(addMonths(date, amount));
    case "year":
      return addYears(date, amount);
  }
}
