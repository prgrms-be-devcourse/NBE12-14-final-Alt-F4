"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/shared/ui/Button";
import { Dropdown } from "@/shared/ui/Dropdown";
import { IconButton } from "@/shared/ui/IconButton";
import { stepDate, VIEW_KEYS, VIEW_LABELS, VIEWS, viewTitle } from "../lib/viewRange";
import type { CalendarView } from "../types";

type Props = {
  view: CalendarView;
  date: Date;
  today: Date;
  shortcutsEnabled: boolean;
  onChange: (view: CalendarView, date: Date) => void;
  onToggleShortcuts: () => void;
};

const TOGGLE_SHORTCUTS = "TOGGLE_SHORTCUTS";

// 흰 카드 맨 위 한 줄: 오늘, ‹ ›, 제목, 보기 메뉴 (일·주·월·연도·일정 + 단축키 켜기·끄기)
// 새 일정은 사이드바의 "＋ 만들기" 메뉴로 옮겼다
export function CalendarToolbar({ view, date, today, shortcutsEnabled, onChange, onToggleShortcuts }: Props) {
  const hint = (key: string) => (shortcutsEnabled ? ` (${key})` : "");
  return (
    <div className="flex h-16 shrink-0 items-center gap-1.5 pl-5 pr-4">
      <Button variant="secondary" size="sm" className="mr-1 h-9 px-4" title={`오늘${hint("T")}`} onClick={() => onChange(view, today)}>
        오늘
      </Button>
      <IconButton label={`이전 기간${hint("K")}`} onClick={() => onChange(view, stepDate(view, date, -1))}>
        <ChevronLeft className="h-5 w-5" aria-hidden />
      </IconButton>
      <IconButton label={`다음 기간${hint("J")}`} onClick={() => onChange(view, stepDate(view, date, 1))}>
        <ChevronRight className="h-5 w-5" aria-hidden />
      </IconButton>
      <h1 className="ml-2 text-[22px] font-semibold tracking-[-0.01em]">{viewTitle(view, date)}</h1>
      <Dropdown<string>
        label="보기"
        hideLabel
        variant="pill"
        className="ml-auto w-24"
        value={view}
        onChange={(value) => (value === TOGGLE_SHORTCUTS ? onToggleShortcuts() : onChange(value as CalendarView, date))}
        options={[
          ...VIEWS.map((option) => ({ value: option, label: VIEW_LABELS[option], trailing: shortcutsEnabled ? VIEW_KEYS[option] : undefined })),
          { type: "separator" as const },
          { value: TOGGLE_SHORTCUTS, label: "키보드 단축키 사용", trailing: shortcutsEnabled ? "켜짐" : "꺼짐" },
        ]}
      />
    </div>
  );
}
