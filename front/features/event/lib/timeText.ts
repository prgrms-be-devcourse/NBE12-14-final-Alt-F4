import { addMilliseconds, isSameDay } from "date-fns";

// 시간 문구 (시간 격자·일정 보기 공용). 브라우저 시간대 기준
// "오전 8시", "오전 8:30", "오후 12시", "오전 12시"(0시)
export function hourText(date: Date): string {
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const half = hours < 12 ? "오전" : "오후";
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  return `${half} ${hour12}${minutes ? `:${String(minutes).padStart(2, "0")}` : "시"}`;
}

// "오전 8시~9시", "오전 11시~오후 1시", "오후 10시~다음 날 오전 2시"
// 끝이 정확히 다음 날 0시면 같은 날로 본다 ("오후 11시~오전 12시")
export function timeRangeLabel(start: Date, end: Date): string {
  const startText = hourText(start);
  const endText = hourText(end);
  if (!isSameDay(start, addMilliseconds(end, -1))) return `${startText}~다음 날 ${endText}`;
  const sameHalf = start.getHours() < 12 === end.getHours() < 12 && !(end.getHours() === 0 && end.getMinutes() === 0);
  return sameHalf ? `${startText}~${endText.replace(/^오[전후] /, "")}` : `${startText}~${endText}`;
}
