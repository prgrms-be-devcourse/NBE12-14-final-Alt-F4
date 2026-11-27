"use client";

import { addDays, addHours, format, isSameDay, max as maxDate, min as minDate, startOfDay } from "date-fns";
import { ko } from "date-fns/locale";
import { useEffectEvent, useLayoutEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { colorOf, inkOf, tintOf } from "@/features/calendar/color";
import { errorMessage } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { useEventsInRange } from "../hooks";
import { compareEvents, eventDays, occurrenceKey } from "../lib/eventDays";
import { layoutAllDay, layoutDay, MIN_VISIBLE_MINUTES, type Bar, type Segment } from "../lib/layoutDay";
import { timeRangeLabel } from "../lib/timeText";
import type { CalendarView, EventItem } from "../types";
import { DayEventsDialog } from "./DayEventsDialog";

export const HOUR_HEIGHT = 48; // 한 시간 높이(px)
const LANE_HEIGHT = 22; // 종일 줄 한 줄 높이(px)
const MIN_BLOCK_HEIGHT = (MIN_VISIBLE_MINUTES / 60) * HOUR_HEIGHT; // 20px

export type CreateRequest = { day: Date; start?: string; end?: string; allDay?: boolean };

type Props = {
  view: CalendarView; // "week" | "day"
  days: Date[]; // 보여 줄 날 (0시)
  now: Date;
  colorByCalendar: ReadonlyMap<number, string | null>;
  hiddenCalendarIds: ReadonlySet<number>;
  onCreate: (request: CreateRequest) => void;
  onEventClick: (event: EventItem) => void;
  onDayClick: (day: Date) => void; // 날짜 숫자 → 그날 일 보기
};

// 벽시계 분 (시각 줄 글자와 맞춘다. 서머타임 날에도 22:00은 "오후 10시" 줄)
const wallMinutes = (date: Date) => date.getHours() * 60 + date.getMinutes();
// 하루 이상인 시간 일정은 종일 줄로 (시작 + 하루 ≤ 끝, 벽시계 하루)
const isLong = (event: EventItem) => event.isAllDay || addDays(new Date(event.startAt), 1) <= new Date(event.endAt);

// 주·일 보기: 날짜 머리, 종일 줄, 0~24시 시간 격자
// key로 다시 마운트하지 않는다: 주를 넘길 때 같은 쿼리 옵저버가 이전 일정을 유지해 깜빡이지 않는다
export function TimeGridView({ view, days, now, colorByCalendar, hiddenCalendarIds, onCreate, onEventClick, onDayClick }: Props) {
  const first = days[0];
  const end = addDays(days[days.length - 1], 1);
  const events = useEventsInRange(first, end);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [moreDay, setMoreDay] = useState<number | null>(null);
  const dayCount = days.length;
  const firstKey = first.getTime();
  const endKey = end.getTime();

  const { allDay, timedByDay } = useMemo(() => {
    const visible = (events.data ?? []).filter((event) => !hiddenCalendarIds.has(event.calendarId)).sort(compareEvents);
    const range = { start: new Date(firstKey), end: new Date(endKey) };
    const bars: Bar<EventItem>[] = [];
    const timed: Segment<EventItem>[][] = Array.from({ length: dayCount }, () => []);
    for (const event of visible) {
      if (isLong(event)) {
        const span = eventDays(event);
        const firstIndex = Math.round((startOfDay(maxDate([span.first, range.start])).getTime() - range.start.getTime()) / 86_400_000);
        const lastIndex = Math.round((startOfDay(minDate([span.last, addDays(range.end, -1)])).getTime() - range.start.getTime()) / 86_400_000);
        if (lastIndex >= 0 && firstIndex < dayCount && firstIndex <= lastIndex) {
          bars.push({ item: event, key: occurrenceKey(event), first: Math.max(firstIndex, 0), last: Math.min(lastIndex, dayCount - 1) });
        }
        continue;
      }
      const start = new Date(event.startAt);
      const finish = new Date(event.endAt);
      for (let index = 0; index < dayCount; index += 1) {
        const dayStart = addDays(range.start, index);
        const next = addDays(dayStart, 1);
        const segmentStart = maxDate([start, dayStart]);
        const segmentEnd = minDate([finish, next]);
        if (segmentStart >= segmentEnd) continue;
        const startMinute = segmentStart.getTime() === dayStart.getTime() ? 0 : wallMinutes(segmentStart);
        const endMinute = segmentEnd >= next ? 1440 : Math.max(wallMinutes(segmentEnd), startMinute);
        timed[index].push({ item: event, key: occurrenceKey(event), start: startMinute, end: endMinute });
      }
    }
    return { allDay: layoutAllDay(bars, dayCount), timedByDay: timed.map((segments) => layoutDay(segments)) };
  }, [events.data, hiddenCalendarIds, firstKey, endKey, dayCount]);

  // 보기를 바꾸거나 처음 열 때만 스크롤을 맞춘다 (주·일을 넘길 때는 그대로)
  const scrollToStart = useEffectEvent(() => {
    const container = scrollRef.current;
    if (!container) return;
    const hasToday = days.some((day) => isSameDay(day, now));
    const minute = hasToday ? Math.max(wallMinutes(now) - 60, 0) : 8 * 60;
    container.scrollTop = (minute / 60) * HOUR_HEIGHT;
  });
  useLayoutEffect(() => scrollToStart(), [view]);

  function handleColumnClick(event: MouseEvent<HTMLDivElement>, day: Date) {
    const rect = event.currentTarget.getBoundingClientRect();
    const minute = Math.min(Math.max(Math.floor(((event.clientY - rect.top) / HOUR_HEIGHT) * 2) * 30, 0), 23 * 60 + 30);
    // 서머타임으로 없는 시각은 브라우저가 앞으로 민다 (예: 뉴욕 3월 02:30 → 03:30)
    const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), Math.floor(minute / 60), minute % 60);
    const endTime = addHours(start, 1);
    onCreate({
      day,
      start: format(start, "HH:mm"),
      end: isSameDay(endTime, start) ? format(endTime, "HH:mm") : "23:59",
    });
  }

  const offsetLabel = useMemo(() => {
    const part = new Intl.DateTimeFormat("ko", { timeZoneName: "shortOffset" }).formatToParts(new Date(firstKey)).find((item) => item.type === "timeZoneName");
    return part?.value ?? "";
  }, [firstKey]);
  const allDayHeight = Math.max(allDay.lanes, 1) * LANE_HEIGHT + (allDay.hiddenPerDay.some(Boolean) ? 18 : 0) + 6;
  const columns = { gridTemplateColumns: `repeat(${dayCount}, minmax(0, 1fr))` };

  return (
    <div className="relative flex min-h-0 flex-1 flex-col">
      {events.isError && (
        <div role="alert" className="flex items-center gap-3 border-b border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          <span>{errorMessage(events.error)}</span>
          <Button variant="secondary" onClick={() => void events.refetch()} className="py-1">
            다시 시도
          </Button>
        </div>
      )}

      {/* 날짜 머리 */}
      {/* 머리·종일 줄도 스크롤 막대 자리를 비워 둔다 (막대가 자리를 차지하는 OS에서 열이 어긋나지 않게) */}
      <div className="flex overflow-y-hidden pr-3 [scrollbar-gutter:stable]">
        <div className="w-16 shrink-0" />
        <div className="grid flex-1" style={columns}>
          {days.map((day) => {
            const isToday = isSameDay(day, now);
            const weekday = day.getDay();
            return (
              <div key={day.toISOString()} className="flex flex-col items-center gap-1 pb-2.5 pt-1">
                <span className={cn("text-xs font-medium", weekday === 0 ? "text-red-500" : weekday === 6 ? "text-blue-500" : "text-muted")}>
                  {format(day, "EEE", { locale: ko })}
                </span>
                <button
                  type="button"
                  onClick={() => onDayClick(day)}
                  aria-label={`${format(day, "M월 d일")} 일 보기`}
                  className={cn(
                    "flex h-10 w-10 items-center justify-center rounded-full text-xl font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand",
                    isToday ? "bg-brand font-semibold text-white hover:bg-brand-hover" : "hover:bg-zinc-100",
                  )}
                >
                  {format(day, "d")}
                </button>
              </div>
            );
          })}
        </div>
      </div>

      {/* 종일 줄 */}
      <div className="flex overflow-y-hidden border-y border-line pr-3 [scrollbar-gutter:stable]">
        <div className="flex w-16 shrink-0 items-start justify-end pr-2.5 pt-2 text-[11px] text-muted">{offsetLabel}</div>
        <div className="relative flex-1" style={{ height: allDayHeight }}>
          <div className="absolute inset-0 grid" style={columns}>
            {days.map((day, index) => (
              <div key={day.toISOString()} onClick={() => onCreate({ day, allDay: true })} className="relative cursor-pointer border-l border-line hover:bg-brand-soft/30">
                {allDay.hiddenPerDay[index] > 0 && (
                  <button
                    type="button"
                    onClick={(clickEvent) => {
                      clickEvent.stopPropagation();
                      setMoreDay(index);
                    }}
                    className="absolute bottom-0.5 left-1 rounded-md px-1.5 text-[11px] font-semibold text-zinc-600 hover:bg-zinc-100"
                  >
                    +{allDay.hiddenPerDay[index]}개
                  </button>
                )}
              </div>
            ))}
          </div>
          {allDay.bars
            .filter((bar) => !bar.hidden)
            .map((bar) => {
              const color = colorOf(colorByCalendar.get(bar.item.calendarId));
              return (
                <button
                  key={bar.key}
                  type="button"
                  title={bar.item.title}
                  onClick={() => onEventClick(bar.item)}
                  className="absolute truncate rounded-md px-2 text-left text-xs font-semibold leading-5 hover:brightness-95 focus-visible:outline-2 focus-visible:outline-brand"
                  style={{
                    backgroundColor: tintOf(color),
                    color: inkOf(color),
                    top: 3 + bar.lane * LANE_HEIGHT,
                    height: LANE_HEIGHT - 2,
                    left: `calc(${(bar.first / dayCount) * 100}% + 3px)`,
                    width: `calc(${((bar.last - bar.first + 1) / dayCount) * 100}% - 6px)`,
                  }}
                >
                  {bar.item.title}
                </button>
              );
            })}
        </div>
      </div>

      {/* 시간 격자 */}
      <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto pr-3 [scrollbar-gutter:stable]">
        <div className="relative flex" style={{ height: 24 * HOUR_HEIGHT }}>
          <div className="relative w-16 shrink-0">
            {Array.from({ length: 23 }, (_, index) => index + 1).map((hour) => (
              <span key={hour} className="absolute right-2.5 -translate-y-1/2 whitespace-nowrap text-[11px] text-muted" style={{ top: hour * HOUR_HEIGHT }}>
                {hour < 12 ? `오전 ${hour}시` : hour === 12 ? "오후 12시" : `오후 ${hour - 12}시`}
              </span>
            ))}
          </div>
          <div className="grid flex-1" style={columns}>
            {days.map((day, index) => {
              const isToday = isSameDay(day, now);
              return (
                <div
                  key={day.toISOString()}
                  onClick={(clickEvent) => handleColumnClick(clickEvent, day)}
                  className={cn("relative cursor-pointer border-l border-line", isToday && "bg-brand-soft/30")}
                  style={{
                    // 정시 선은 연하게, 30분 선은 더 연하게
                    backgroundImage: `repeating-linear-gradient(to bottom, #eef0f3 0, #eef0f3 1px, transparent 1px, transparent ${HOUR_HEIGHT / 2}px, #f6f7f9 ${HOUR_HEIGHT / 2}px, #f6f7f9 ${HOUR_HEIGHT / 2 + 1}px, transparent ${HOUR_HEIGHT / 2 + 1}px, transparent ${HOUR_HEIGHT}px)`,
                  }}
                >
                  {timedByDay[index].map((segment) => {
                    const top = (segment.start / 60) * HOUR_HEIGHT;
                    const height = Math.max(((segment.end - segment.start) / 60) * HOUR_HEIGHT, MIN_BLOCK_HEIGHT);
                    const width = (segment.span / segment.columns) * 100;
                    const left = (segment.column / segment.columns) * 100;
                    const color = colorOf(colorByCalendar.get(segment.item.calendarId));
                    const time = timeRangeLabel(new Date(segment.item.startAt), new Date(segment.item.endAt));
                    return (
                      <button
                        key={`${segment.key}-${index}`}
                        type="button"
                        aria-label={`${segment.item.title}, ${time}`}
                        onClick={(clickEvent) => {
                          clickEvent.stopPropagation();
                          onEventClick(segment.item);
                        }}
                        className="absolute overflow-hidden rounded-lg px-2 py-1 text-left shadow-[0_0_0_2px_#fff] hover:brightness-95 focus-visible:outline-2 focus-visible:outline-brand"
                        style={{ top, height, left: `calc(${left}% + 2px)`, width: `calc(${width}% - 6px)`, backgroundColor: tintOf(color), color: inkOf(color) }}
                      >
                        <span className="block truncate text-xs font-semibold leading-4">{segment.item.title}</span>
                        {height >= (45 / 60) * HOUR_HEIGHT && <span className="block truncate text-[11px] leading-[15px] opacity-85">{time}</span>}
                      </button>
                    );
                  })}
                  {isToday && (
                    <div className="pointer-events-none absolute inset-x-0 z-10" style={{ top: (wallMinutes(now) / 60) * HOUR_HEIGHT }} aria-hidden>
                      <div className="relative h-0.5 bg-[#e5484d]">
                        <span className="absolute -left-1.5 -top-1 h-2.5 w-2.5 rounded-full bg-[#e5484d]" />
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {moreDay !== null && (
        <DayEventsDialog
          day={days[moreDay]}
          events={allDay.bars.filter((bar) => bar.first <= moreDay && moreDay <= bar.last).map((bar) => bar.item)}
          colorByCalendar={colorByCalendar}
          onSelect={(event) => {
            setMoreDay(null);
            onEventClick(event);
          }}
          onClose={() => setMoreDay(null)}
        />
      )}
    </div>
  );
}

