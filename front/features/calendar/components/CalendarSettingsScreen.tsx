"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type FormEvent, type ReactNode } from "react";
import { ShareCreateSection } from "@/features/share/components/ShareCreateSection";
import { errorMessage, fieldError } from "@/shared/api/errors";
import { Button, buttonClass } from "@/shared/ui/Button";
import { cardClass } from "@/shared/ui/card";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { Dropdown } from "@/shared/ui/Dropdown";
import { TextareaField } from "@/shared/ui/fields";
import { Spinner } from "@/shared/ui/Spinner";
import { TextField } from "@/shared/ui/TextField";
import { TimezoneCombobox } from "@/shared/ui/TimezoneCombobox";
import { colorOf } from "../color";
import { useCalendar, useDeleteCalendar, useGroups, useLeaveCalendar, useMoveCalendarGroup, useUpdateCalendar } from "../hooks";
import { canEditEvents, canLeave, isOwner } from "../permissions";
import type { CalendarDetail, CalendarGroup } from "../types";
import { CategorySection } from "./CategorySection";

const roleNames: Record<string, string> = {
  OWNER: "만든 사람",
  EDITOR: "편집",
  VIEWER: "보기",
  SUBSCRIBER: "구독",
};

// /calendars/[calendarId]/settings: 기본 정보, 내 그룹 위치, 카테고리, 공유 페이지, 삭제·나가기
export function CalendarSettingsScreen() {
  const calendarId = Number(useParams<{ calendarId: string }>().calendarId);
  const validId = Number.isInteger(calendarId) && calendarId > 0;
  const calendar = useCalendar(calendarId);
  const groups = useGroups();

  if (!validId) return <ErrorScreen text="캘린더를 찾을 수 없어요." />;
  if (calendar.isPending || groups.isPending) return <Spinner fullScreen />;
  if (calendar.isError) return <ErrorScreen text={errorMessage(calendar.error)} onRetry={() => void calendar.refetch()} />;
  if (groups.isError) return <ErrorScreen text={errorMessage(groups.error)} onRetry={() => void groups.refetch()} />;

  const summary = groups.data.flatMap((group) => group.calendars).find((item) => item.id === calendarId);
  const ownedCalendars = groups.data.flatMap((group) => group.calendars).filter((item) => isOwner(item.role));
  const owner = isOwner(calendar.data.role);

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-col gap-4 px-4 py-8">
      <div className="mb-2 flex flex-col gap-1">
        <div className="flex items-center gap-3">
          <span className="h-4 w-4 shrink-0 rounded-full" style={{ backgroundColor: colorOf(summary?.color ?? calendar.data.color) }} />
          <h1 className="min-w-0 break-words text-[22px] font-semibold">{calendar.data.name} 설정</h1>
          <Link href="/calendar" className="ml-auto shrink-0 text-sm text-brand hover:underline">
            캘린더로
          </Link>
        </div>
        <p className="text-sm text-muted">내 권한: {roleNames[calendar.data.role] ?? calendar.data.role}</p>
      </div>

      <InfoSection key={calendar.data.id} calendar={calendar.data} editable={owner} />
      <GroupSection calendar={calendar.data} groups={groups.data} />
      <CategorySection calendarId={calendarId} categories={summary?.categories ?? []} editable={canEditEvents(calendar.data.role)} />
      {owner && <ShareCreateSection calendarId={calendarId} ownedCalendars={ownedCalendars} />}
      <DangerSection calendar={calendar.data} />
    </main>
  );
}

function ErrorScreen({ text, onRetry }: { text: string; onRetry?: () => void }) {
  return (
    <main className="flex flex-col items-center gap-3 px-4 py-24 text-center">
      <p className="text-sm text-zinc-600">{text}</p>
      <div className="flex gap-2">
        {onRetry && (
          <Button variant="secondary" onClick={onRetry}>
            다시 시도
          </Button>
        )}
        <Link href="/calendar" className={buttonClass("ghost")}>
          캘린더로
        </Link>
      </div>
    </main>
  );
}

// 원본 캘린더 정보. OWNER만 고치고, 다른 사람에게는 글자로 보여 준다
function InfoSection({ calendar, editable }: { calendar: CalendarDetail; editable: boolean }) {
  const update = useUpdateCalendar();
  // 원래 색이 없던 캘린더는 고르지 않으면 null 그대로 보낸다 (저장만 해도 파랑으로 굳지 않게)
  const [color, setColor] = useState<string | null>(calendar.color);
  const [nameError, setNameError] = useState<string | null>(null);
  const [timezone, setTimezone] = useState(calendar.timezone);

  if (!editable) {
    return (
      <section className={`${cardClass} flex flex-col gap-3`}>
        <h2 className="text-base font-semibold">기본 정보</h2>
        <p className="text-xs text-zinc-500">캘린더를 만든 사람만 바꿀 수 있어요.</p>
        <dl className="flex flex-col gap-2 text-sm">
          <InfoRow label="이름">{calendar.name}</InfoRow>
          <InfoRow label="설명">{calendar.description || "없음"}</InfoRow>
          <InfoRow label="기본 색">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-3 w-3 rounded-full" style={{ backgroundColor: colorOf(calendar.color) }} />
              {calendar.color ?? "기본"}
            </span>
          </InfoRow>
          <InfoRow label="시간대">{calendar.timezone}</InfoRow>
        </dl>
      </section>
    );
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") ?? "").trim();
    if (!name) {
      update.reset();
      setNameError("이름을 입력해 주세요.");
      return;
    }
    update.mutate({
      id: calendar.id,
      body: {
        name,
        description: String(form.get("description") ?? "").trim() || null,
        color,
        timezone: String(form.get("timezone") ?? "").trim() || undefined,
      },
    });
  }

  const nameServerError = fieldError(update.error, "name");

  return (
    <section className={`${cardClass} flex flex-col gap-3`}>
      <h2 className="text-base font-semibold">기본 정보</h2>
      <form
        noValidate
        onSubmit={handleSubmit}
        // 값을 다시 바꾸면 "저장했어요."를 지운다
        onChange={() => {
          if (update.isSuccess) update.reset();
        }}
        className="flex flex-col gap-3"
      >
        <TextField
          label="이름"
          name="name"
          defaultValue={calendar.name}
          maxLength={100}
          error={nameError ?? nameServerError}
          onChange={() => setNameError(null)}
        />
        <TextareaField label="설명" name="description" defaultValue={calendar.description ?? ""} />
        <div className="flex flex-col gap-1">
          <label htmlFor="calendar-color" className="text-sm font-medium text-zinc-700">
            기본 색 (이 캘린더를 쓰는 모든 사람에게 보여요)
          </label>
          <input
            id="calendar-color"
            type="color"
            value={colorOf(color)}
            onChange={(event) => setColor(event.target.value.toLowerCase())}
            className="h-9 w-12 cursor-pointer rounded-lg border border-line-strong"
          />
        </div>
        <TimezoneCombobox
          label="캘린더 시간대"
          name="timezone"
          value={timezone}
          onChange={(zone) => {
            setTimezone(zone);
            // 숨긴 입력이 바뀌어도 폼 onChange는 불리지 않는다 → "저장했어요."를 여기서 지운다
            if (update.isSuccess) update.reset();
          }}
        />
        {update.isError && !nameServerError && (
          <p role="alert" className="text-sm text-red-600">
            {errorMessage(update.error)}
          </p>
        )}
        {update.isSuccess && <p className="text-sm text-green-700">저장했어요.</p>}
        <Button type="submit" disabled={update.isPending} className="self-start">
          {update.isPending ? "저장 중…" : "저장"}
        </Button>
      </form>
    </section>
  );
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-3">
      <dt className="w-16 shrink-0 text-zinc-500">{label}</dt>
      <dd className="min-w-0 whitespace-pre-wrap break-words text-zinc-800">{children}</dd>
    </div>
  );
}

// 내 화면에서 이 캘린더를 넣어 둘 그룹 (누구나, 나에게만 적용)
// 보이는 값은 서버 값(캘린더 상세)이라 다른 기기에서 옮겨도 따라간다
// 옮기는 동안(다시 불러오기까지)은 고른 값을 보여 줘서 옛 그룹으로 튀지 않는다. 실패하면 서버 값으로 돌아간다
function GroupSection({ calendar, groups }: { calendar: CalendarDetail; groups: CalendarGroup[] }) {
  const move = useMoveCalendarGroup();
  const selected = move.isPending && move.variables ? move.variables.groupId : calendar.groupId;

  return (
    <section className={`${cardClass} flex flex-col gap-3`}>
      <h2 className="text-base font-semibold">내 그룹 위치</h2>
      <Dropdown
        label="넣어 둘 그룹"
        className="max-w-xs"
        value={selected}
        disabled={move.isPending || groups.length < 2}
        onChange={(groupId) => {
          if (groupId === selected) return;
          move.mutate({ id: calendar.id, groupId });
        }}
        options={groups.map((group) => ({ value: group.id, label: group.name }))}
      />
      {groups.length < 2 && <p className="text-xs text-zinc-500">그룹을 더 만들면 옮길 수 있어요.</p>}
      {move.isSuccess && <p className="text-sm text-green-700">옮겼어요.</p>}
      {move.isError && (
        <p role="alert" className="text-sm text-red-600">
          {errorMessage(move.error)}
        </p>
      )}
    </section>
  );
}

// OWNER: 캘린더 삭제(모든 사람에게서 사라짐) / SUBSCRIBER: 내 목록에서 빼기(구독 해지) / 그 밖: 안내만
function DangerSection({ calendar }: { calendar: CalendarDetail }) {
  const router = useRouter();
  const remove = useDeleteCalendar();
  const leave = useLeaveCalendar();
  const [confirming, setConfirming] = useState(false);
  const owner = isOwner(calendar.role);

  if (!owner && !canLeave(calendar.role)) {
    return (
      <p className={`${cardClass} text-sm text-zinc-600`}>
        {calendar.role === "VIEWER" ? "조율방에서 나가면 목록에서 빠져요." : "이 캘린더는 지금 내 목록에서 뺄 수 없어요."}
      </p>
    );
  }

  const mutation = owner ? remove : leave;

  return (
    <section className={`${cardClass} flex flex-col gap-3`}>
      <h2 className="text-base font-semibold text-red-700">{owner ? "캘린더 삭제" : "캘린더 나가기"}</h2>
      <p className="text-sm text-zinc-600">
        {owner ? "삭제하면 되돌릴 수 없어요. 조율방이 쓰는 캘린더는 삭제할 수 없어요." : "내 목록에서만 빠지고 원본 캘린더는 그대로예요."}
      </p>
      <Button variant="danger" onClick={() => setConfirming(true)} className="self-start">
        {owner ? "캘린더 삭제" : "캘린더 나가기"}
      </Button>
      {confirming && (
        <ConfirmDialog
          title={owner ? "캘린더 삭제" : "캘린더 나가기"}
          message={
            owner
              ? `'${calendar.name}' 캘린더를 삭제할까요? 일정·카테고리가 모두 지워지고, 이 캘린더를 쓰는 모든 사람에게서 사라져요. 되돌릴 수 없어요.`
              : `'${calendar.name}' 캘린더를 내 목록에서 뺄까요?`
          }
          confirmLabel={owner ? "삭제" : "나가기"}
          danger={owner}
          pending={mutation.isPending}
          error={mutation.isError ? errorMessage(mutation.error) : null}
          onConfirm={() => mutation.mutate(calendar.id, { onSuccess: () => router.replace("/calendar") })}
          onClose={() => {
            mutation.reset();
            setConfirming(false);
          }}
        />
      )}
    </section>
  );
}
