"use client";

import { keepPreviousData, useInfiniteQuery } from "@tanstack/react-query";
import { addDays, format, isSameDay, min as minDate, startOfDay } from "date-fns";
import { ko } from "date-fns/locale";
import { Fragment, useMemo } from "react";
import { colorOf } from "@/features/calendar/color";
import { errorMessage } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { browserTimeZone } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { Spinner } from "@/shared/ui/Spinner";
import { fetchEvents } from "../api";
import { eventQueryKeys } from "../hooks";
import { compareEvents, eventDays, occurrenceKey } from "../lib/eventDays";
import { hourText, timeRangeLabel } from "../lib/timeText";
import { agendaEnd } from "../lib/viewRange";
import type { EventItem } from "../types";

const PAGE_DAYS = 90; // 백엔드 조회 기간 최대 93일

type Props = {
  start: Date; // 목록 시작 날짜
  now: Date;
  colorByCalendar: ReadonlyMap<number, string | null>;
  hiddenCalendarIds: ReadonlySet<number>;
  onEventClick: (event: EventItem) => void;
};

type Row = { event: EventItem; time: string; part: string };

// 일정(목록) 보기: 고른 날짜부터 1년. 90일씩 받고 "더 보기"로 이어 받는다
// 백엔드는 기간과 겹치는 일정을 모두 줘서, 경계를 걸친 일정이 두 페이지에 다 온다 → occurrenceKey로 중복을 뺀다
export function AgendaView({ start, now, colorByCalendar, hiddenCalendarIds, onEventClick }: Props) {
  const from = startOfDay(start);
  const end = agendaEnd(from);
  const query = useInfiniteQuery({
    queryKey: [...eventQueryKeys.all, "agenda", from.toISOString()],
    initialPageParam: from.toISOString(),
    queryFn: async ({ pageParam }) => {
      const pageFrom = new Date(pageParam);
      const pageTo = minDate([addDays(pageFrom, PAGE_DAYS), end]);
      const events = await fetchEvents({ from: pageFrom.toISOString(), to: pageTo.toISOString(), timezone: browserTimeZone() });
      return { events, to: pageTo.toISOString() };
    },
    getNextPageParam: (last) => (new Date(last.to) < end ? last.to : undefined),
    placeholderData: keepPreviousData,
  });

  const fromKey = from.getTime();
  const pages = query.data?.pages;
  const loadedEnd = pages?.length ? new Date(pages[pages.length - 1].to) : from;
  const loadedEndKey = loadedEnd.getTime();

  // 날짜별 줄: [시작 날, 지금까지 받은 끝) 안의 날만 (시작 날보다 앞선 날·아직 안 받은 날은 그리지 않는다)
  const groups = useMemo(() => {
    const unique = new Map<string, EventItem>();
    for (const page of pages ?? []) for (const event of page.events) unique.set(occurrenceKey(event), event);
    const byDay = new Map<number, Row[]>();
    const sorted = [...unique.values()].filter((event) => !hiddenCalendarIds.has(event.calendarId)).sort(compareEvents);
    for (const event of sorted) {
      const { first, last } = eventDays(event);
      const total = Math.round((last.getTime() - first.getTime()) / 86_400_000) + 1;
      for (let day = first, index = 1; day <= last; day = addDays(day, 1), index += 1) {
        if (day.getTime() < fromKey || day.getTime() >= loadedEndKey) continue;
        const rows = byDay.get(day.getTime()) ?? [];
        rows.push({ event, time: timeText(event, day, first, last), part: total > 1 ? ` (${index}/${total}일)` : "" });
        byDay.set(day.getTime(), rows);
      }
    }
    return byDay;
  }, [pages, hiddenCalendarIds, fromKey, loadedEndKey]);

  // 오늘이 목록 기간에 있으면 일정이 없어도 오늘 줄을 보인다 (지금 시각 선)
  const todayKey = startOfDay(now).getTime();
  const showToday = todayKey >= fromKey && todayKey < loadedEndKey;
  const dayKeys = [...new Set([...groups.keys(), ...(showToday ? [todayKey] : [])])].sort((a, b) => a - b);

  if (query.isPending) return <Spinner fullScreen />;

  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      {query.isError && (
        <div role="alert" className="flex items-center gap-3 border-b border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          <span>{errorMessage(query.error)}</span>
          <Button variant="secondary" onClick={() => void query.refetch()} className="py-1">
            다시 시도
          </Button>
        </div>
      )}
      {dayKeys.length === 0 && <p className="px-6 py-10 text-center text-sm text-zinc-500">이 기간에 일정이 없어요.</p>}
      <ul>
        {dayKeys.map((key) => {
          const day = new Date(key);
          const rows = groups.get(key) ?? [];
          const isToday = isSameDay(day, now);
          // 지금 시각 선의 위치: 종일과 지금보다 먼저 시작한 일정 다음
          const lineIndex = isToday ? rows.filter((row) => row.event.isAllDay || new Date(row.event.startAt) <= now).length : -1;
          return (
            <li key={key} className="flex gap-4 border-b border-line px-6 py-2.5">
              <div className="flex w-24 shrink-0 items-center gap-2 self-start pt-1">
                <span className={cn("flex h-8 w-8 items-center justify-center rounded-full text-lg font-medium", isToday && "bg-brand font-semibold text-white")}>
                  {format(day, "d")}
                </span>
                <span className={cn("text-xs", isToday ? "font-semibold text-brand-ink" : "text-muted")}>{format(day, "M월, EEE", { locale: ko })}</span>
              </div>
              <div className="flex min-w-0 flex-1 flex-col">
                {rows.map((row, index) => (
                  <Fragment key={occurrenceKey(row.event)}>
                    {index === lineIndex && <NowLine />}
                    <button
                      type="button"
                      onClick={() => onEventClick(row.event)}
                      className="flex items-center gap-3 rounded-lg px-2.5 py-2 text-left text-sm hover:bg-zinc-100/70 focus-visible:outline-2 focus-visible:outline-brand"
                    >
                      <span aria-hidden className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: colorOf(colorByCalendar.get(row.event.calendarId)) }} />
                      <span className="w-40 shrink-0 text-muted">{row.time}</span>
                      <span className="min-w-0 truncate font-medium">
                        {row.event.title}
                        {row.part && <span className="text-zinc-400">{row.part}</span>}
                      </span>
                    </button>
                  </Fragment>
                ))}
                {lineIndex === rows.length && <NowLine />}
              </div>
            </li>
          );
        })}
      </ul>
      <div className="flex flex-col items-center gap-2 px-6 py-6 text-sm text-zinc-500">
        {query.isFetchingNextPage && <Spinner />}
        {query.hasNextPage ? (
          <Button variant="secondary" onClick={() => void query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            더 보기
          </Button>
        ) : (
          <p>{format(addDays(end, -1), "yyyy년 M월 d일")}까지의 일정이에요.</p>
        )}
      </div>
    </div>
  );
}

function NowLine() {
  return (
    <div className="relative my-1 h-0.5 bg-red-500" aria-hidden>
      <span className="absolute -left-1 -top-1 h-2.5 w-2.5 rounded-full bg-red-500" />
    </div>
  );
}

// 줄의 시간 문구: 종일 / 하루짜리 시간 일정 / 여러 날 시간 일정의 첫날·가운데·마지막 날
function timeText(event: EventItem, day: Date, first: Date, last: Date): string {
  if (event.isAllDay) return "종일";
  const start = new Date(event.startAt);
  const end = new Date(event.endAt);
  if (isSameDay(first, last)) return timeRangeLabel(start, end);
  if (isSameDay(day, first)) return `${hourText(start)}~`;
  if (isSameDay(day, last)) return `~${hourText(end)}`;
  return "종일";
}
