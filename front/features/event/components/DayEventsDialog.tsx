"use client";

import { format } from "date-fns";
import { ko } from "date-fns/locale";
import { colorOf } from "@/features/calendar/color";
import { Modal } from "@/shared/ui/Modal";
import { occurrenceKey } from "../lib/eventDays";
import { hourText } from "../lib/timeText";
import type { EventItem } from "../types";

type Props = {
  day: Date;
  events: EventItem[];
  colorByCalendar: ReadonlyMap<number, string | null>;
  onSelect: (event: EventItem) => void;
  onClose: () => void;
};

// 월간 칸의 "+N개": 그날 일정 전체 목록. 누르면 상세로 간다
export function DayEventsDialog({ day, events, colorByCalendar, onSelect, onClose }: Props) {
  return (
    <Modal title={format(day, "M월 d일 (EEE)", { locale: ko })} onClose={onClose} width="sm">
      <ul className="flex flex-col gap-1">
        {events.map((event) => (
          <li key={occurrenceKey(event)}>
            <button
              type="button"
              onClick={() => onSelect(event)}
              className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-sm hover:bg-zinc-100/70 focus-visible:outline-2 focus-visible:outline-brand"
            >
              <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: colorOf(colorByCalendar.get(event.calendarId)) }} />
              <span className="w-16 shrink-0 text-xs text-muted">{event.isAllDay ? "종일" : hourText(new Date(event.startAt))}</span>
              <span className="min-w-0 truncate">{event.title}</span>
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  );
}
