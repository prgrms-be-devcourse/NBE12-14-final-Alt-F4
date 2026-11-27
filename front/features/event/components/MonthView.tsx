"use client";

import { addDays, format, isSameDay, isSameMonth } from "date-fns";
import { ko } from "date-fns/locale";
import { useMemo, useState } from "react";
import { colorOf, inkOf, tintOf } from "@/features/calendar/color";
import { errorMessage } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { compareEvents, eventDays, occurrenceKey } from "../lib/eventDays";
import { hourText } from "../lib/timeText";
import { monthGrid, WEEKDAYS } from "../lib/monthGrid";
import { useEventsInRange } from "../hooks";
import type { EventItem } from "../types";
import { DayEventsDialog } from "./DayEventsDialog";

const MAX_PER_DAY = 3;

type Props = {
  date: Date;
  today: Date;
  colorByCalendar: ReadonlyMap<number, string | null>;
  hiddenCalendarIds: ReadonlySet<number>;
  onDayClick: (day: Date) => void;
  onEventClick: (event: EventItem) => void;
};

type DayItem = { event: EventItem; first: Date; last: Date };

// 일요일 빨강, 토요일 파랑
function weekdayColor(day: Date): string | null {
  if (day.getDay() === 0) return "text-red-500";
  if (day.getDay() === 6) return "text-blue-500";
  return null;
}

// 월간 보기: 6주 격자에 그날 걸치는 일정을 보여 준다 (하루 최대 3개, 나머지는 +N개 → 그날 목록 창)
export function MonthView({ date, today, colorByCalendar, hiddenCalendarIds, onDayClick, onEventClick }: Props) {
  // 같은 달이면 격자를 다시 만들지 않는다 (아래 날짜별 일정 계산이 매번 다시 돌지 않게)
  const year = date.getFullYear();
  const month = date.getMonth();
  const { days, from, to } = useMemo(() => monthGrid(new Date(year, month, 1)), [year, month]);
  const events = useEventsInRange(from, to);
  const [moreDay, setMoreDay] = useState<Date | null>(null);

  // 날짜별 일정을 한 번에 만든다 (칸마다 전체 일정을 다시 거르지 않게)
  // 사이드바에서 끈 캘린더는 화면에서만 뺀다 (다시 켜도 새로 조회하지 않는다)
  const byDay = useMemo(() => {
    const map = new Map<string, DayItem[]>();
    const sorted = (events.data ?? []).filter((event) => !hiddenCalendarIds.has(event.calendarId)).sort(compareEvents);
    for (const event of sorted) {
      const { first, last } = eventDays(event);
      for (let day = first < from ? from : first; day <= last && day < to; day = addDays(day, 1)) {
        const key = day.toDateString();
        const list = map.get(key) ?? [];
        list.push({ event, first, last });
        map.set(key, list);
      }
    }
    return map;
  }, [events.data, hiddenCalendarIds, from, to]);

  return (
    <div className="relative flex min-h-0 flex-1 flex-col">
      {/* 불러오는 동안 위쪽에 가는 진행 막대 (이전 달 화면은 그대로 둔다) */}
      {events.isError && (
        <div role="alert" className="flex items-center gap-3 border-b border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          <span>{errorMessage(events.error)}</span>
          <Button variant="secondary" onClick={() => void events.refetch()} className="py-1">
            다시 시도
          </Button>
        </div>
      )}
      <div className="grid grid-cols-7 px-2 text-center text-xs font-medium text-muted">
        {WEEKDAYS.map((weekday, index) => (
          <div key={weekday} className={cn("py-2", index === 0 && "text-red-500", index === 6 && "text-blue-500")}>
            {weekday}
          </div>
        ))}
      </div>
      {/* 6주를 칸 높이에 맞춰 나눈다 (줄 수가 늘어도 아래 주가 잘리지 않게, 넘치는 칩은 칸 안에서 숨긴다) */}
      <div className="grid min-h-0 flex-1 grid-cols-7 grid-rows-6 border-t border-line px-2 pb-2">
        {days.map((day) => {
          const dayEvents = byDay.get(day.toDateString()) ?? [];
          const inMonth = isSameMonth(day, date);
          const isToday = isSameDay(day, today);
          return (
            <div
              key={day.toISOString()}
              onClick={() => onDayClick(day)}
              className={cn(
                "flex min-h-0 min-w-0 cursor-pointer flex-col gap-[3px] overflow-hidden border-b border-r border-line pb-1 pt-1.5 hover:bg-brand-soft/30",
              )}
            >
              {/* 날짜 숫자는 버튼: 키보드로도 그날 새 일정을 연다 */}
              <button
                type="button"
                onClick={(clickEvent) => {
                  clickEvent.stopPropagation();
                  onDayClick(day);
                }}
                aria-label={`${format(day, "M월 d일", { locale: ko })} 일정 만들기`}
                className={cn(
                  "mx-2 flex h-[26px] w-[26px] shrink-0 items-center justify-center self-start rounded-full text-[12.5px] font-medium focus-visible:outline-2 focus-visible:outline-brand",
                  isToday ? "bg-brand font-semibold text-white" : cn("hover:bg-zinc-100", inMonth ? (weekdayColor(day) ?? "text-foreground") : "text-zinc-400"),
                )}
              >
                {format(day, "d")}
              </button>
              {dayEvents.slice(0, MAX_PER_DAY).map(({ event, first, last }) => (
                <EventChip
                  key={occurrenceKey(event)}
                  event={event}
                  color={colorOf(colorByCalendar.get(event.calendarId))}
                  showTime={!event.isAllDay && isSameDay(first, day)}
                  // 종일 막대가 이어지면 그쪽 모서리를 펴서 옆 칸과 붙인다 (주가 바뀌는 곳에서는 끊는다)
                  joinLeft={!isSameDay(first, day) && day.getDay() !== 0}
                  joinRight={!isSameDay(last, day) && day.getDay() !== 6}
                  onClick={() => onEventClick(event)}
                />
              ))}
              {dayEvents.length > MAX_PER_DAY && (
                <button
                  type="button"
                  onClick={(clickEvent) => {
                    clickEvent.stopPropagation();
                    setMoreDay(day);
                  }}
                  className="mx-1 self-start rounded-md px-1.5 text-xs font-semibold leading-5 text-zinc-600 hover:bg-zinc-100"
                >
                  +{dayEvents.length - MAX_PER_DAY}개 더
                </button>
              )}
            </div>
          );
        })}
      </div>
      {moreDay && (
        <DayEventsDialog
          day={moreDay}
          events={(byDay.get(moreDay.toDateString()) ?? []).map((item) => item.event)}
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

type ChipProps = { event: EventItem; color: string; showTime: boolean; joinLeft: boolean; joinRight: boolean; onClick: () => void };

// 종일: 캘린더 색을 옅게 깐 막대 / 시간 일정: 색 점 + 시작 시각 + 제목
function EventChip({ event, color, showTime, joinLeft, joinRight, onClick }: ChipProps) {
  const time = showTime ? hourText(new Date(event.startAt)) : null;
  return (
    <button
      type="button"
      title={event.title}
      onClick={(clickEvent) => {
        // 날짜 칸의 "새 일정"으로 번지지 않게 한다
        clickEvent.stopPropagation();
        onClick();
      }}
      className={cn(
        "flex h-[22px] shrink-0 items-center gap-1.5 text-left text-xs focus-visible:outline-2 focus-visible:outline-brand",
        event.isAllDay
          ? cn("px-2 font-semibold hover:brightness-95", joinLeft ? "ml-0 rounded-l-none" : "ml-1 rounded-l-md", joinRight ? "mr-0 rounded-r-none" : "mr-1 rounded-r-md")
          : "mx-1 rounded-md px-1.5 hover:bg-zinc-100",
      )}
      style={event.isAllDay ? { backgroundColor: tintOf(color), color: inkOf(color) } : undefined}
    >
      {!event.isAllDay && <span className="h-[7px] w-[7px] shrink-0 rounded-full" style={{ backgroundColor: color }} />}
      {time && <span className="shrink-0 text-muted">{time}</span>}
      <span className={cn("truncate", !event.isAllDay && "font-medium text-foreground")}>{event.title}</span>
    </button>
  );
}
