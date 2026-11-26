"use client";

import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import type { CalendarSummary } from "@/features/calendar/types";
import { errorMessage, fieldError } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { cardClass } from "@/shared/ui/card";
import { CheckboxField, ErrorText, TextareaField } from "@/shared/ui/fields";
import { TextField } from "@/shared/ui/TextField";
import { createShare } from "../api";

type Props = { calendarId: number; ownedCalendars: CalendarSummary[] };

type FormErrors = { title?: string; calendarIds?: string };

// 공유 페이지 만들기: 내가 OWNER인 캘린더를 묶어서 등록한다 (이 캘린더는 기본으로 체크)
export function ShareCreateSection({ calendarId, ownedCalendars }: Props) {
  const mutation = useMutation({ mutationFn: createShare });
  const [errors, setErrors] = useState<FormErrors>({});
  const [createdId, setCreatedId] = useState<number | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const title = String(form.get("title") ?? "").trim();
    const calendarIds = form.getAll("calendarIds").map(Number);
    const nextErrors: FormErrors = {
      ...(!title && { title: "제목을 입력해 주세요." }),
      ...(calendarIds.length === 0 && { calendarIds: "캘린더를 하나 이상 골라 주세요." }),
    };
    setErrors(nextErrors);
    if (nextErrors.title || nextErrors.calendarIds) {
      mutation.reset();
      return;
    }
    mutation.mutate(
      {
        title,
        content: String(form.get("content") ?? "").trim() || null,
        calendarIds,
        isPublic: form.get("isPublic") === "on",
      },
      {
        onSuccess: ({ shareId }) => {
          // 다시 누르면 새 공유 페이지가 하나 더 생기므로 폼을 비운다
          formElement.reset();
          setCreatedId(shareId);
        },
      },
    );
  }

  const titleError = errors.title ?? fieldError(mutation.error, "title");
  const calendarIdsError = errors.calendarIds ?? fieldError(mutation.error, "calendarIds");
  const commonError = mutation.isError && !titleError && !calendarIdsError ? errorMessage(mutation.error) : null;

  return (
    <section className={`${cardClass} flex flex-col gap-3`}>
      <h2 className="text-base font-semibold">공유 페이지 만들기</h2>
      <p className="text-xs text-muted">내가 만든(OWNER) 캘린더를 묶어 공유 페이지로 등록해요. 공유 페이지를 보는 화면과 구독은 준비 중이에요.</p>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-3">
        <TextField label="제목" name="title" maxLength={255} error={titleError} onChange={() => setErrors((previous) => ({ ...previous, title: undefined }))} />
        <TextareaField label="소개" name="content" />
        <fieldset className="flex flex-col gap-1" aria-describedby={calendarIdsError ? "share-calendars-error" : undefined}>
          <legend className="text-[13px] font-medium text-zinc-700">묶을 캘린더</legend>
          {ownedCalendars.map((calendar) => (
            <CheckboxField
              key={calendar.id}
              id={`share-calendar-${calendar.id}`}
              name="calendarIds"
              value={calendar.id}
              label={calendar.name}
              defaultChecked={calendar.id === calendarId}
              onChange={() => setErrors((previous) => ({ ...previous, calendarIds: undefined }))}
            />
          ))}
          <ErrorText id="share-calendars-error" error={calendarIdsError} />
        </fieldset>
        <CheckboxField label="공개 (둘러보기에 보임)" name="isPublic" />
        {commonError && (
          <p role="alert" className="text-sm text-red-600">
            {commonError}
          </p>
        )}
        {createdId !== null && <p className="text-sm text-green-700">공유 페이지를 만들었어요 (번호 {createdId}).</p>}
        <Button type="submit" variant="secondary" disabled={mutation.isPending} className="self-start">
          {mutation.isPending ? "만드는 중…" : createdId !== null ? "하나 더 만들기" : "공유 페이지 만들기"}
        </Button>
      </form>
    </section>
  );
}
