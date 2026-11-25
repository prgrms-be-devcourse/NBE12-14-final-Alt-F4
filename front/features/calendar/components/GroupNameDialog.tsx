"use client";

import { useState, type FormEvent } from "react";
import { errorMessage, fieldError } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { Modal } from "@/shared/ui/Modal";
import { TextField } from "@/shared/ui/TextField";
import { useCreateGroup, useRenameGroup } from "../hooks";
import type { CalendarGroup } from "../types";

// 그룹 추가(group 없음)와 이름 바꾸기(group 있음)에 같이 쓴다
export function GroupNameDialog({ group, onClose }: { group?: CalendarGroup; onClose: () => void }) {
  const create = useCreateGroup();
  const rename = useRenameGroup();
  const mutation = group ? rename : create;
  const [nameError, setNameError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    if (!name) {
      mutation.reset();
      setNameError("이름을 입력해 주세요.");
      return;
    }
    if (group) {
      if (name === group.name) onClose();
      else rename.mutate({ id: group.id, name }, { onSuccess: onClose });
    } else {
      create.mutate(name, { onSuccess: onClose });
    }
  }

  const serverNameError = fieldError(mutation.error, "name");

  return (
    <Modal title={group ? "그룹 이름 바꾸기" : "새 그룹"} onClose={onClose} width="sm" busy={mutation.isPending}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="그룹 이름"
          name="name"
          defaultValue={group?.name}
          maxLength={100}
          data-autofocus
          error={nameError ?? serverNameError}
          onChange={() => setNameError(null)}
        />
        {mutation.isError && !serverNameError && (
          <p role="alert" className="text-sm text-red-600">
            {errorMessage(mutation.error)}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            취소
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? "저장 중…" : group ? "저장" : "만들기"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
