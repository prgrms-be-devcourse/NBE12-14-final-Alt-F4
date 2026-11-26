"use client";

import { Check, Settings } from "lucide-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { errorMessage } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { Spinner } from "@/shared/ui/Spinner";
import { colorOf } from "../color";
import { useGroups } from "../hooks";
import { CreateCalendarDialog } from "./CreateCalendarDialog";
import { GroupHeader, hoverRevealClass } from "./GroupHeader";

type Props = {
  top?: ReactNode; // 맨 위 칸 (캘린더 화면이 만들기 메뉴·미니 달력을 넣는다)
  hiddenCalendarIds: ReadonlySet<number>;
  onToggleCalendar: (calendarId: number) => void;
};

const roleLabels: Record<string, string> = {
  OWNER: "",
  EDITOR: "편집",
  VIEWER: "보기",
  SUBSCRIBER: "구독",
};

// 내 그룹 → 캘린더 목록. 끈 캘린더는 이 브라우저에 기억한다 (hiddenCalendars.ts)
export function CalendarSidebar({ top, hiddenCalendarIds, onToggleCalendar }: Props) {
  const groups = useGroups();
  // 캘린더 만들기 창: null이면 닫힘, groupId가 null이면 첫 그룹
  const [createTarget, setCreateTarget] = useState<{ groupId: number | null } | null>(null);

  return (
    <aside className="flex w-[264px] shrink-0 flex-col gap-6 overflow-y-auto px-4 pb-4 pt-[18px]">
      {top}
      {groups.isPending && <Spinner />}
      {groups.isError && (
        <div className="flex flex-col items-start gap-2">
          <p className="text-sm text-red-600">{errorMessage(groups.error)}</p>
          <Button variant="secondary" onClick={() => void groups.refetch()}>
            다시 시도
          </Button>
        </div>
      )}

      {groups.data?.length === 0 && (
        <div className="flex flex-col gap-3 rounded-2xl bg-white p-4 text-sm text-zinc-600 shadow-card">
          <p>아직 캘린더가 없어요. 첫 캘린더를 만들어 보세요.</p>
          <Button onClick={() => setCreateTarget({ groupId: null })}>첫 캘린더 만들기</Button>
        </div>
      )}

      {groups.data?.map((group) => (
        <section key={group.id} className="flex flex-col gap-1">
          <GroupHeader group={group} onCreateCalendar={() => setCreateTarget({ groupId: group.id })} />
          {group.calendars.length === 0 && <p className="px-1.5 text-xs text-zinc-400">비어 있음</p>}
          {group.calendars.map((calendar) => {
            const color = colorOf(calendar.color);
            const visible = !hiddenCalendarIds.has(calendar.id);
            return (
              <div key={calendar.id} className="group/row flex h-8 items-center gap-2 rounded-lg px-1.5 hover:bg-zinc-200/50">
                {/* 줄을 눌러도 켜고 끈다. 끈 캘린더도 테두리로 색이 보인다 */}
                <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5">
                  <input
                    type="checkbox"
                    checked={visible}
                    onChange={() => onToggleCalendar(calendar.id)}
                    aria-label={`${calendar.name} 보기`}
                    className="peer sr-only"
                  />
                  <span
                    aria-hidden
                    className="flex h-4 w-4 shrink-0 items-center justify-center rounded-[5px] border-2 text-white peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand"
                    style={{ borderColor: color, backgroundColor: visible ? color : "transparent" }}
                  >
                    {visible && <Check className="h-3 w-3" strokeWidth={3.5} />}
                  </span>
                  <span className={`min-w-0 flex-1 truncate text-[13px] ${visible ? "text-foreground" : "text-zinc-400"}`}>{calendar.name}</span>
                </label>
                {roleLabels[calendar.role] && <span className="text-[11px] text-muted">{roleLabels[calendar.role]}</span>}
                <Link
                  href={`/calendars/${calendar.id}/settings`}
                  className={`flex h-6 w-6 items-center justify-center rounded-md text-zinc-400 hover:bg-zinc-200/70 hover:text-zinc-700 focus-visible:outline-2 focus-visible:outline-brand ${hoverRevealClass}`}
                  aria-label={`${calendar.name} 설정`}
                  title="설정"
                >
                  <Settings className="h-3.5 w-3.5" aria-hidden />
                </Link>
              </div>
            );
          })}
        </section>
      ))}

      {createTarget && groups.data && (
        <CreateCalendarDialog groups={groups.data} initialGroupId={createTarget.groupId} onClose={() => setCreateTarget(null)} />
      )}
    </aside>
  );
}
