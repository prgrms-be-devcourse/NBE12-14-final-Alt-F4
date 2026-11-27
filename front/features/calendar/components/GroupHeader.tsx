"use client";

import { Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { useDeleteGroup } from "../hooks";
import type { CalendarGroup } from "../types";
import { GroupNameDialog } from "./GroupNameDialog";

// 마우스를 올리면 보이는 버튼. 키보드 포커스와 터치 화면에서도 보인다 (display:none이면 Tab으로 갈 수 없다)
export const hoverRevealClass =
  "opacity-0 group-hover/row:opacity-100 group-focus-within/row:opacity-100 [@media(hover:none)]:opacity-100";

const actionClass =
  "flex h-6 w-6 items-center justify-center rounded-md text-zinc-400 hover:bg-zinc-200/70 hover:text-zinc-700 focus-visible:outline-2 focus-visible:outline-brand";

// 사이드바의 그룹 이름 줄: 캘린더 만들기(+)·이름 바꾸기·삭제 (그룹은 내 개인 폴더라 다른 사람에게 영향 없음)
export function GroupHeader({ group, onCreateCalendar }: { group: CalendarGroup; onCreateCalendar: () => void }) {
  const [dialog, setDialog] = useState<"rename" | "delete" | null>(null);
  const remove = useDeleteGroup();
  const hasCalendars = group.calendars.length > 0;

  return (
    <div className="group/row flex h-7 items-center gap-1 px-1.5">
      <h2 className="min-w-0 flex-1 truncate text-xs font-semibold text-zinc-600">{group.name}</h2>
      <span className={`flex gap-0.5 ${hoverRevealClass}`}>
        <button type="button" onClick={onCreateCalendar} aria-label={`${group.name}에 캘린더 만들기`} title={`${group.name}에 캘린더 만들기`} className={actionClass}>
          <Plus className="h-3.5 w-3.5" aria-hidden />
        </button>
        <button type="button" onClick={() => setDialog("rename")} aria-label={`${group.name} 그룹 이름 바꾸기`} title="이름 바꾸기" className={actionClass}>
          <Pencil className="h-3.5 w-3.5" aria-hidden />
        </button>
        {/* 캘린더가 든 그룹은 지울 수 없다 (서버 409). disabled 대신 aria-disabled라 안내 글이 보인다 */}
        <button
          type="button"
          aria-disabled={hasCalendars}
          title={hasCalendars ? "캘린더를 먼저 다른 그룹으로 옮겨 주세요." : "그룹 삭제"}
          onClick={() => {
            if (!hasCalendars) setDialog("delete");
          }}
          aria-label={`${group.name} 그룹 삭제`}
          className={`${actionClass} aria-disabled:cursor-not-allowed aria-disabled:opacity-40 aria-disabled:hover:bg-transparent aria-disabled:hover:text-zinc-400`}
        >
          <Trash2 className="h-3.5 w-3.5" aria-hidden />
        </button>
      </span>

      {dialog === "rename" && <GroupNameDialog group={group} onClose={() => setDialog(null)} />}
      {dialog === "delete" && (
        <ConfirmDialog
          title="그룹 삭제"
          message={`'${group.name}' 그룹을 삭제할까요?`}
          confirmLabel="삭제"
          danger
          pending={remove.isPending}
          error={remove.isError ? errorMessage(remove.error) : null}
          onConfirm={() => remove.mutate(group.id, { onSuccess: () => setDialog(null) })}
          onClose={() => {
            remove.reset();
            setDialog(null);
          }}
        />
      )}
    </div>
  );
}
