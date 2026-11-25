import { format, parse } from "date-fns";
import { ko } from "date-fns/locale";
import type { RoomSummary } from "./types";

const day = (value: string) => format(parse(value, "yyyy-MM-dd", new Date(0)), "M월 d일 (EEE)", { locale: ko });
const time = (value: string) => value.slice(0, 5);

// 끝 시간이 시작보다 이르거나 같으면 다음 날로 넘어간다 (같으면 24시간)
export function crossesMidnight(room: Pick<RoomSummary, "dailyStartTime" | "dailyEndTime">): boolean {
  return time(room.dailyEndTime) <= time(room.dailyStartTime);
}

// "11월 25일 (수) ~ 11월 28일 (토) · 매일 22:00 ~ 02:00 (다음 날)"
// 방 시간대가 내 브라우저와 다를 때만 시간대를 붙인다
export function roomRangeText(room: RoomSummary, myTimeZone: string): string {
  const end = `${time(room.dailyEndTime)}${crossesMidnight(room) ? " (다음 날)" : ""}`;
  const zone = room.timezone === myTimeZone ? "" : ` · ${room.timezone} 기준`;
  return `${day(room.startDate)} ~ ${day(room.endDate)} · 매일 ${time(room.dailyStartTime)} ~ ${end}${zone}`;
}

// 끝 날짜가 오늘보다 앞이면 지난 방
export function isPastRoom(room: RoomSummary, today: string): boolean {
  return room.endDate < today;
}
