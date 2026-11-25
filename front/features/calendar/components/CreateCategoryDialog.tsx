"use client";

import { useState, type FormEvent } from "react";
import { errorMessage, fieldError } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { Modal } from "@/shared/ui/Modal";
import { TextField } from "@/shared/ui/TextField";
import { DEFAULT_COLOR } from "../color";
import { useCreateCategory } from "../hooks";
import { ColorPicker } from "./ColorPicker";

type Props = {
  calendarId: number;
  calendarName: string;
  onCreated: (categoryId: number) => void; // 캘린더 목록을 다시 불러온 뒤에 부른다 (새 카테고리가 목록에 있다)
  onClose: () => void;
};

// 일정 폼에서 바로 만드는 새 카테고리: 이름 + 색. 폼에서 지금 고른 캘린더에 만든다
// 일정 폼 <form> 밖에 그린다 (이 창의 Enter가 일정을 저장하지 않게)
export function CreateCategoryDialog({ calendarId, calendarName, onCreated, onClose }: Props) {
  const mutation = useCreateCategory();
  const [color, setColor] = useState(DEFAULT_COLOR);
  const [nameError, setNameError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    if (!name) {
      mutation.reset();
      setNameError("이름을 입력해 주세요.");
      return;
    }
    mutation.mutate({ calendarId, name, color }, { onSuccess: (result) => onCreated(result.id) });
  }

  const nameServerError = fieldError(mutation.error, "name");
  const commonError = mutation.isError && !nameServerError ? errorMessage(mutation.error) : null;

  return (
    <Modal title="새 카테고리" onClose={onClose} busy={mutation.isPending} width="sm">
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField label="이름" name="name" maxLength={100} data-autofocus error={nameError ?? nameServerError} onChange={() => setNameError(null)} />
        <ColorPicker value={color} onChange={setColor} />
        <p className="text-xs text-muted">&apos;{calendarName}&apos; 캘린더에 만들어요. 캘린더 설정에서 고치거나 지울 수 있어요.</p>
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
