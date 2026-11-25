import { addDays, startOfMonth, startOfWeek } from "date-fns";

export const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 월간 화면의 6주 격자 (일요일 시작, 42일). 조회 기간은 [첫날, 마지막 날 다음 날)
export function monthGrid(date: Date): { days: Date[]; from: Date; to: Date } {
  const from = startOfWeek(startOfMonth(date), { weekStartsOn: 0 });
  const days = Array.from({ length: 42 }, (_, index) => addDays(from, index));
  return { days, from, to: addDays(from, 42) };
}
