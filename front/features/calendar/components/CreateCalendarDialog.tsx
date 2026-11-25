"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { errorMessage, fieldError, hasCode } from "@/shared/api/errors";
import { browserTimeZone } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { Dropdown } from "@/shared/ui/Dropdown";
import { TextareaField } from "@/shared/ui/fields";
import { Modal } from "@/shared/ui/Modal";
import { TextField } from "@/shared/ui/TextField";
import { DEFAULT_COLOR } from "../color";
import { calendarQueryKeys, useCreateCalendar } from "../hooks";
import type { CalendarGroup } from "../types";
import { ColorPicker } from "./ColorPicker";

type Props = {
  groups: CalendarGroup[];
  initialGroupId: number | null; // 그룹 머리글의 +로 열면 그 그룹, 아니면 null(첫 그룹)
  onClose: () => void;
};

// 새 캘린더 만들기: 이름, 색, 설명, 넣을 그룹. 시간대는 브라우저 값을 보낸다
// 그룹 칸은 비제어(defaultValue): 그룹 목록을 다시 불러와 순서가 바뀌어도 고른 값이 유지되고,
// 고른 그룹이 사라지면 브라우저가 첫 옵션을 고른다
export function CreateCalendarDialog({ groups, initialGroupId, onClose }: Props) {
  const queryClient = useQueryClient();
  const mutation = useCreateCalendar();
  const [color, setColor] = useState(DEFAULT_COLOR);
  const [nameError, setNameError] = useState<string | null>(null);
  const [timezone] = useState(browserTimeZone);
  // 고른 그룹. 그룹 목록을 다시 불러와 그 그룹이 사라졌으면(다른 탭에서 삭제) 첫 그룹을 쓴다 (예전 네이티브 select와 같은 동작)
  const [pickedGroupId, setPickedGroupId] = useState(initialGroupId ?? groups[0]?.id);
  const groupId = groups.some((group) => group.id === pickedGroupId) ? pickedGroupId : groups[0]?.id;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") ?? "").trim();
    if (!name) {
      mutation.reset();
      setNameError("이름을 입력해 주세요.");
      return;
    }
    const description = String(form.get("description") ?? "").trim();
    const groupId = form.get("groupId");
    mutation.mutate(
      {
        name,
        color,
        timezone,
        ...(description && { description }),
        ...(groupId && { groupId: Number(groupId) }),
      },
      {
        onSuccess: onClose,
        onError: (error) => {
          // 다른 탭에서 그 그룹을 지웠다: 그룹 목록을 고친다
          if (hasCode(error, "GROUP_NOT_FOUND")) void queryClient.invalidateQueries({ queryKey: calendarQueryKeys.groups });
        },
      },
    );
  }

  const nameServerError = fieldError(mutation.error, "name");
  const commonError = mutation.isError && !nameServerError ? errorMessage(mutation.error) : null;

  return (
    <Modal title="새 캘린더" onClose={onClose} busy={mutation.isPending}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="이름"
          name="name"
          maxLength={100}
          data-autofocus
          error={nameError ?? nameServerError}
          onChange={() => setNameError(null)}
        />
        <ColorPicker value={color} onChange={setColor} />
        <TextareaField label="설명 (선택)" name="description" />
        {groups.length > 0 ? (
          <div className="flex flex-col gap-1">
            <Dropdown
              label="넣을 그룹"
              name="groupId"
              value={groupId ?? groups[0].id}
              onChange={setPickedGroupId}
              options={groups.map((group) => ({ value: group.id, label: group.name }))}
            />
            <p className="text-xs text-zinc-500">내 화면에서만 쓰는 폴더예요. 나중에 설정에서 옮길 수 있어요.</p>
          </div>
        ) : (
          <p className="rounded-md bg-zinc-50 p-3 text-sm text-zinc-600">그룹이 없으면 &apos;내 캘린더&apos; 그룹이 새로 만들어져요.</p>
        )}
        <p className="text-xs text-zinc-500">시간대: {timezone} · 설정에서 바꿀 수 있어요.</p>
        {commonError && (
          <p role="alert" className="text-sm text-red-600">
            {commonError}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            취소
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? "만드는 중…" : "만들기"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
