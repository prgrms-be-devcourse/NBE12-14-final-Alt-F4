"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { startOfDay } from "date-fns";
import { ChevronDown, Plus } from "lucide-react";
import { useState } from "react";
import { CalendarSidebar } from "@/features/calendar/components/CalendarSidebar";
import { CreateCalendarDialog } from "@/features/calendar/components/CreateCalendarDialog";
import { GroupNameDialog } from "@/features/calendar/components/GroupNameDialog";
import { useChangeCalendarDisplay, useGroups } from "@/features/calendar/hooks";
import { formatDateParam, parseDateParam } from "@/shared/lib/date";
import { MenuButton } from "@/shared/ui/MenuButton";
import { stepDate, toView, weekDays } from "../lib/viewRange";
import { setShortcutsEnabled, useShortcutsEnabled } from "../shortcutsSetting";
import type { CalendarView } from "../types";
import { CalendarToolbar } from "./CalendarToolbar";
import { EventDialogs, type EventDialogState } from "./EventDialogs";
import { SidebarMiniMonth } from "./MiniMonth";
import { AgendaView } from "./AgendaView";
import { MonthView } from "./MonthView";
import { TimeGridView } from "./TimeGridView";
import { useCalendarShortcuts } from "./useCalendarShortcuts";
import { useNow } from "./useNow";
import { YearView } from "./YearView";

// 메인 캘린더 화면: /calendar?view=month&date=2026-11-25
// 보기와 날짜는 URL에 둔다 (새로고침·뒤로 가기·링크 공유가 그대로 된다)
export function CalendarScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  // 1분마다·화면이 다시 보일 때 새로 잡는다 (자정이 지나면 오늘이 바뀐다)
  const today = useNow(60_000);
  const view = toView(searchParams.get("view"));
  const date = parseDateParam(searchParams.get("date"), today);
  const [dialog, setDialog] = useState<EventDialogState | null>(null);
  const [creating, setCreating] = useState<"calendar" | "group" | null>(null);
  const shortcutsEnabled = useShortcutsEnabled();

  const groups = useGroups();
  const changeDisplay = useChangeCalendarDisplay();
  const calendars = (groups.data ?? []).flatMap((group) => group.calendars);
  // 끈 캘린더는 서버에 사람별로 저장된다 (다른 기기에도 실시간으로 반영). 일정은 서버가 빼고 주지만, 누른 즉시 화면에서도 뺀다
  const hiddenCalendarIds = new Set(calendars.filter((calendar) => !calendar.isVisible).map((calendar) => calendar.id));
  const colorByCalendar = new Map(calendars.map((calendar) => [calendar.id, calendar.color] as const));

  // "만들기 → 일정"과 C: 주소의 날짜(지금 고른 날짜). 연도 보기만 오늘
  const createDay = view === "year" ? today : date;

  function navigate(nextView: CalendarView, nextDate: Date) {
    router.push(`/calendar?view=${nextView}&date=${formatDateParam(nextDate)}`);
  }

  function openCreateEvent() {
    if (groups.isSuccess) setDialog({ kind: "create", day: createDay });
  }

  useCalendarShortcuts(shortcutsEnabled, (action) => {
    if (action === "today") navigate(view, today);
    else if (action === "next") navigate(view, stepDate(view, date, 1));
    else if (action === "previous") navigate(view, stepDate(view, date, -1));
    else if (action === "create") openCreateEvent();
    else navigate(action, date);
  });

  function toggleCalendar(calendarId: number) {
    const calendar = calendars.find((item) => item.id === calendarId);
    if (calendar) changeDisplay.mutate({ id: calendarId, isVisible: !calendar.isVisible, color: calendar.myColor });
  }

  // 미니 달력: 지금 보기를 유지한 채 그 날짜로. 연도 보기에서 고르면 그날 일 보기
  function pickDay(day: Date) {
    navigate(view === "year" ? "day" : view, day);
  }

  const sidebarTop = (
    <div className="flex flex-col gap-5">
      <MenuButton
        label={
          <span className="flex items-center gap-2.5">
            <Plus className="h-[22px] w-[22px] text-brand" strokeWidth={2.4} aria-hidden />
            만들기
            <ChevronDown className="h-4 w-4 text-muted" aria-hidden />
          </span>
        }
        ariaLabel="만들기"
        className="flex h-12 items-center self-start rounded-2xl bg-white pl-3.5 pr-4 text-sm font-semibold text-foreground shadow-raised outline-none hover:bg-zinc-50 focus-visible:ring-[3px] focus-visible:ring-brand/30"
        items={[
          { label: "일정", trailing: shortcutsEnabled ? "C" : undefined, onSelect: openCreateEvent },
          { label: "캘린더", onSelect: () => setCreating("calendar") },
          { label: "그룹", onSelect: () => setCreating("group") },
          { type: "separator" },
          { label: "일정 조율방", onSelect: () => router.push("/rooms/new") },
        ]}
      />
      <SidebarMiniMonth view={view} date={date} today={today} onPick={pickDay} />
    </div>
  );

  return (
    // 화면 높이에 맞춘다 (레이아웃이 화면 높이): 시간 격자·일정 목록이 안쪽에서 스크롤되게
    <div className="flex min-h-0 flex-1">
      <CalendarSidebar top={sidebarTop} hiddenCalendarIds={hiddenCalendarIds} onToggleCalendar={toggleCalendar} />
      {/* 흰 카드 (디자인 기준): 툴바와 보기 */}
      <main className="flex min-w-0 flex-1 flex-col py-3 pr-4">
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-card bg-white shadow-card">
        <CalendarToolbar
          view={view}
          date={date}
          today={today}
          shortcutsEnabled={shortcutsEnabled}
          onChange={navigate}
          onToggleShortcuts={() => setShortcutsEnabled(!shortcutsEnabled)}
        />
        {view === "month" ? (
          <MonthView
            date={date}
            today={today}
            colorByCalendar={colorByCalendar}
            hiddenCalendarIds={hiddenCalendarIds}
            onDayClick={(day) => setDialog({ kind: "create", day })}
            onEventClick={(event) => setDialog({ kind: "detail", event })}
          />
        ) : view === "week" || view === "day" ? (
          <TimeGridView
            view={view}
            days={view === "week" ? weekDays(date).map((day) => startOfDay(day)) : [startOfDay(date)]}
            now={today}
            colorByCalendar={colorByCalendar}
            hiddenCalendarIds={hiddenCalendarIds}
            onCreate={(request) => groups.isSuccess && setDialog({ kind: "create", ...request })}
            onEventClick={(event) => setDialog({ kind: "detail", event })}
            onDayClick={(day) => navigate("day", day)}
          />
        ) : view === "year" ? (
          <YearView year={date.getFullYear()} today={today} onDayClick={(day) => navigate("day", day)} />
        ) : (
          <AgendaView
            start={date}
            now={today}
            colorByCalendar={colorByCalendar}
            hiddenCalendarIds={hiddenCalendarIds}
            onEventClick={(event) => setDialog({ kind: "detail", event })}
          />
        )}
        </div>
      </main>
      {dialog && groups.isSuccess && <EventDialogs state={dialog} calendars={calendars} hiddenCalendarIds={hiddenCalendarIds} onChange={setDialog} />}
      {creating === "calendar" && groups.data && <CreateCalendarDialog groups={groups.data} initialGroupId={null} onClose={() => setCreating(null)} />}
      {creating === "group" && <GroupNameDialog onClose={() => setCreating(null)} />}
    </div>
  );
}
