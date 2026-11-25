"use client";

import { useRef, useState, type FormEvent } from "react";
import { errorMessage } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { cardClass } from "@/shared/ui/card";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { fieldBaseClass } from "@/shared/ui/fields";
import { colorOf, DEFAULT_COLOR } from "../color";
import { useCreateCategory, useDeleteCategory, useUpdateCategory } from "../hooks";
import type { CategorySummary } from "../types";

type Props = { calendarId: number; categories: CategorySummary[]; editable: boolean };

const nameInputClass = cn(fieldBaseClass, "h-9 min-w-0 flex-1 border-transparent");

// 캘린더의 카테고리 (이 캘린더를 쓰는 모든 사람이 같은 카테고리를 본다). 편집은 OWNER·EDITOR
export function CategorySection({ calendarId, categories, editable }: Props) {
  const create = useCreateCategory();
  const [nameError, setNameError] = useState<string | null>(null);

  function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const name = String(form.get("name") ?? "").trim();
    if (!name) {
      create.reset();
      setNameError("이름을 입력해 주세요.");
      return;
    }
    create.mutate({ calendarId, name, color: String(form.get("color")) }, { onSuccess: () => formElement.reset() });
  }

  const error = nameError ?? (create.isError ? errorMessage(create.error) : null);

  return (
    <section className={`${cardClass} flex flex-col gap-3`}>
      <h2 className="text-base font-semibold">카테고리</h2>
      {categories.length === 0 && <p className="text-sm text-muted">카테고리가 없어요.</p>}
      <ul className="flex flex-col gap-1">
        {categories.map((category) => (
          <CategoryRow key={category.id} category={category} editable={editable} />
        ))}
      </ul>
      {editable && (
        <form noValidate onSubmit={handleCreate} className="flex items-center gap-2">
          <input type="color" name="color" defaultValue={DEFAULT_COLOR} aria-label="카테고리 색" className="h-9 w-9 shrink-0 cursor-pointer rounded-lg border border-line-strong" />
          <input
            name="name"
            maxLength={100}
            placeholder="새 카테고리 이름"
            aria-label="새 카테고리 이름"
            onChange={() => setNameError(null)}
            className={nameInputClass}
          />
          <Button type="submit" variant="secondary" disabled={create.isPending}>
            추가
          </Button>
        </form>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
    </section>
  );
}

function CategoryRow({ category, editable }: { category: CategorySummary; editable: boolean }) {
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const update = useUpdateCategory();
  const remove = useDeleteCategory();
  // 수정을 끝내면 "수정" 버튼으로 포커스를 돌려준다
  const returnFocus = useRef(false);

  function finishEditing() {
    returnFocus.current = true;
    update.reset();
    setNameError(null);
    setEditing(false);
  }

  function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") ?? "").trim();
    if (!name) {
      update.reset();
      setNameError("이름을 입력해 주세요.");
      return;
    }
    update.mutate({ id: category.id, name, color: String(form.get("color")) }, { onSuccess: finishEditing });
  }

  if (editing) {
    const error = nameError ?? (update.isError ? errorMessage(update.error) : null);
    return (
      <li className="flex flex-col gap-1">
        <form
          noValidate
          onSubmit={handleSave}
          onKeyDown={(event) => {
            if (event.key === "Escape" && !event.nativeEvent.isComposing) finishEditing();
          }}
          className="flex items-center gap-2"
        >
          <input type="color" name="color" defaultValue={colorOf(category.color)} aria-label="카테고리 색" className="h-9 w-9 shrink-0 cursor-pointer rounded-lg border border-line-strong" />
          <input
            name="name"
            defaultValue={category.name}
            maxLength={100}
            aria-label="카테고리 이름"
            autoFocus
            onChange={() => setNameError(null)}
            className={nameInputClass}
          />
          <Button type="submit" disabled={update.isPending}>
            저장
          </Button>
          <Button variant="ghost" onClick={finishEditing}>
            취소
          </Button>
        </form>
        {error && (
          <p role="alert" className="text-xs text-red-600">
            {error}
          </p>
        )}
      </li>
    );
  }

  return (
    <li className="flex flex-col">
      <div className="flex items-center gap-2 rounded-md px-1 py-1 hover:bg-zinc-50">
        <span className="h-3 w-3 rounded-full" style={{ backgroundColor: colorOf(category.color) }} />
        <span className="flex-1 text-sm">{category.name}</span>
        {editable && (
          <>
            <Button
              variant="ghost"
              onClick={() => setEditing(true)}
              ref={(button) => {
                if (button && returnFocus.current) {
                  returnFocus.current = false;
                  button.focus();
                }
              }}
            >
              수정
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(true)}>
              삭제
            </Button>
          </>
        )}
      </div>
      {confirming && (
        <ConfirmDialog
          title="카테고리 삭제"
          message={`'${category.name}' 카테고리를 삭제할까요? 이 카테고리를 단 일정은 남고 카테고리만 비워져요.`}
          confirmLabel="삭제"
          danger
          pending={remove.isPending}
          error={remove.isError ? errorMessage(remove.error) : null}
          onConfirm={() => remove.mutate(category.id, { onSuccess: () => setConfirming(false) })}
          onClose={() => {
            remove.reset();
            setConfirming(false);
          }}
        />
      )}
    </li>
  );
}
