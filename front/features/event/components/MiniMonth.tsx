"use client";

import { addDays, addMonths, addYears, endOfWeek, format, isSameDay, isSameMonth, isSameWeek, startOfMonth, startOfWeek } from "date-fns";
import { ko } from "date-fns/locale";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useLayoutEffect, useRef, useState, type KeyboardEvent } from "react";
import { IconButton } from "@/shared/ui/IconButton";
import { cn } from "@/shared/lib/cn";
import { formatDateParam } from "@/shared/lib/date";
import { monthGrid, WEEKDAYS } from "../lib/monthGrid";
import type { CalendarView } from "../types";

const WEEKDAY_FULL = ["일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"];

type Props = {
  month: Date; // 보여 줄 달 (그달 아무 날)
  today: Date;
  selected?: Date; // 고른 날짜 (연한 원)
  weekBand?: boolean; // 고른 날짜의 주에 띠 (주 보기)
  showNav?: boolean; // 머리의 ‹ › (연도 보기는 false)
  title?: string; // 머리 글자 (기본 "2026년 11월")
  onMonthChange?: (month: Date) => void;
  onPick: (day: Date) => void;
  focusDay?: Date | null; // 이 날짜 칸에 포커스를 줘야 할 때
  onFocusDay?: (day: Date) => void; // 키보드로 포커스가 옮겨 갈 날짜 (그달 밖이면 부르는 쪽이 달을 바꾼다)
};

// 작은 달력 (사이드바·연도 보기). WAI-ARIA APG 날짜 선택 표: grid + roving tabindex
// 방향키 하루·한 주, Home·End 주의 처음·끝, PageUp·PageDown 이전·다음 달(Shift면 해), Enter·Space 고르기
export function MiniMonth({ month, today, selected, weekBand, showNav = true, title, onMonthChange, onPick, focusDay, onFocusDay }: Props) {
  const { days } = monthGrid(month);
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const tabbable =
    (focusDay && isSameMonth(focusDay, month) && focusDay) || (selected && isSameMonth(selected, month) && selected) || startOfMonth(month);

  // 포커스할 날짜가 바뀌면 그 칸에 포커스 (DOM만 만짐)
  useLayoutEffect(() => {
    if (focusDay && isSameMonth(focusDay, month)) buttons.current.get(formatDateParam(focusDay))?.focus();
  }, [focusDay, month]);

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, day: Date) {
    const moves: Record<string, () => Date> = {
      ArrowLeft: () => addDays(day, -1),
      ArrowRight: () => addDays(day, 1),
      ArrowUp: () => addDays(day, -7),
      ArrowDown: () => addDays(day, 7),
      Home: () => startOfWeek(day, { weekStartsOn: 0 }),
      End: () => endOfWeek(day, { weekStartsOn: 0 }),
      PageUp: () => (event.shiftKey ? addYears(day, -1) : addMonths(day, -1)),
      PageDown: () => (event.shiftKey ? addYears(day, 1) : addMonths(day, 1)),
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    onFocusDay?.(move());
  }

  const weeks = Array.from({ length: 6 }, (_, row) => days.slice(row * 7, row * 7 + 7));
  return (
    <div className="flex flex-col gap-1 text-xs">
      <div className="flex h-7 items-center justify-between pl-1.5">
        <span className="text-[13px] font-semibold text-foreground">{title ?? format(month, "yyyy년 M월")}</span>
        {showNav && (
          <span className="flex">
            <IconButton label="이전 달" size="sm" onClick={() => onMonthChange?.(addMonths(month, -1))}>
              <ChevronLeft className="h-4 w-4" aria-hidden />
            </IconButton>
            <IconButton label="다음 달" size="sm" onClick={() => onMonthChange?.(addMonths(month, 1))}>
              <ChevronRight className="h-4 w-4" aria-hidden />
            </IconButton>
          </span>
        )}
      </div>
      <table role="grid" aria-label={format(month, "yyyy년 M월")} className="w-full border-collapse">
        <thead>
          <tr>
            {WEEKDAYS.map((weekday, index) => (
              <th key={weekday} abbr={WEEKDAY_FULL[index]} scope="col" className="h-6 text-[11px] font-normal text-muted">
                {weekday}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {weeks.map((week) => {
            const band = weekBand && selected && isSameWeek(week[0], selected, { weekStartsOn: 0 });
            return (
              <tr key={week[0].toISOString()} className={cn(band && "bg-brand-soft [&>td:first-child]:rounded-l-full [&>td:last-child]:rounded-r-full")}>
                {week.map((day) => {
                  const isToday = isSameDay(day, today);
                  const isSelected = selected !== undefined && isSameDay(day, selected);
                  const key = formatDateParam(day);
                  return (
                    <td key={key} role="gridcell" aria-selected={isSelected || undefined} className="p-0 text-center">
                      <button
                        ref={(element) => {
                          if (element) buttons.current.set(key, element);
                          else buttons.current.delete(key);
                        }}
                        type="button"
                        tabIndex={isSameDay(day, tabbable) ? 0 : -1}
                        aria-label={format(day, "yyyy년 M월 d일 EEEE", { locale: ko })}
                        aria-current={isToday ? "date" : undefined}
                        onClick={() => onPick(day)}
                        onKeyDown={(event) => handleKeyDown(event, day)}
                        className={cn(
                          "mx-auto flex h-7 w-7 items-center justify-center rounded-full text-xs focus-visible:outline-2 focus-visible:outline-brand",
                          isToday ? "bg-brand font-semibold text-white" : isSelected ? "bg-brand-soft font-semibold text-brand-ink" : "hover:bg-zinc-200/60",
                          !isToday && !isSelected && (isSameMonth(day, month) ? "text-zinc-700" : "text-zinc-400"),
                        )}
                      >
                        {format(day, "d")}
                      </button>
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// 사이드바의 미니 달력: 보여 줄 달을 자기 상태로 들되, 본 화면 날짜(URL)가 바뀌면 그 달로 돌아온다
// key로 다시 마운트하지 않는다 (포커스한 칸이 사라지지 않게)
export function SidebarMiniMonth({ view, date, today, onPick }: { view: CalendarView; date: Date; today: Date; onPick: (day: Date) => void }) {
  const urlKey = formatDateParam(date);
  const [state, setState] = useState({ anchor: urlKey, month: startOfMonth(date) });
  const [focusDay, setFocusDay] = useState<Date | null>(null);
  const month = state.anchor === urlKey ? state.month : startOfMonth(date);
  const setMonth = (next: Date) => setState({ anchor: urlKey, month: startOfMonth(next) });

  return (
    // 미니 달력 밖으로 포커스가 나가면 포커스 기억을 지운다 (본 화면을 옮길 때 미니 달력이 포커스를 가져가지 않게)
    <div
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocusDay(null);
      }}
    >
    <MiniMonth
      month={month}
      today={today}
      selected={date}
      weekBand={view === "week"}
      onMonthChange={(next) => {
        setFocusDay(null);
        setMonth(next);
      }}
      onPick={(day) => {
        setFocusDay(day);
        onPick(day);
      }}
      focusDay={focusDay}
      onFocusDay={(day) => {
        if (!isSameMonth(day, month)) setMonth(day);
        setFocusDay(day);
      }}
    />
    </div>
  );
}
