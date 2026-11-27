"use client";

import { format } from "date-fns";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useGroups } from "@/features/calendar/hooks";
import { errorMessage, fieldError, hasCode } from "@/shared/api/errors";
import { Button, buttonClass } from "@/shared/ui/Button";
import { cardClass } from "@/shared/ui/card";
import { Dropdown } from "@/shared/ui/Dropdown";
import { Spinner } from "@/shared/ui/Spinner";
import { TextField } from "@/shared/ui/TextField";
import { TimezoneCombobox } from "@/shared/ui/TimezoneCombobox";
import { browserTimeZone } from "@/shared/lib/date";
import { useCreateRoom } from "../hooks";

type LocalErrors = { name?: string; endDate?: string };

// /rooms/new: 이름, 날짜 범위, 매일 시간대, 공통 캘린더를 넣을 내 그룹
export function RoomCreateScreen() {
  const router = useRouter();
  const groups = useGroups();
  const mutation = useCreateRoom();
  const [today] = useState(() => format(new Date(), "yyyy-MM-dd"));
  const [startDate, setStartDate] = useState(today);
  const [timezone, setTimezone] = useState(browserTimeZone);
  const [localErrors, setLocalErrors] = useState<LocalErrors>({});
  const [pickedGroupId, setPickedGroupId] = useState<number | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const request = {
      name: String(form.get("name") ?? "").trim(),
      startDate: String(form.get("startDate") ?? ""),
      endDate: String(form.get("endDate") ?? ""),
      dailyStartTime: String(form.get("dailyStartTime") ?? ""),
      dailyEndTime: String(form.get("dailyEndTime") ?? ""),
      timezone: String(form.get("timezone") ?? "").trim(),
      groupId: Number(form.get("groupId")),
    };
    const errors: LocalErrors = {
      ...(!request.name && { name: "방 이름을 입력해 주세요." }),
      ...(request.endDate < request.startDate && { endDate: "끝 날짜는 시작 날짜와 같거나 뒤여야 해요." }),
    };
    setLocalErrors(errors);
    if (errors.name || errors.endDate) {
      mutation.reset();
      return;
    }
    // 뒤로 가기를 눌렀을 때 채워진 만들기 화면으로 돌아오지 않게 replace
    mutation.mutate(request, { onSuccess: (room) => router.replace(`/rooms/${room.id}`) });
  }

  if (groups.isPending) return <Spinner fullScreen />;
  if (groups.isError) {
    return (
      <main className="flex flex-col items-center gap-3 px-4 py-24 text-center">
        <p className="text-sm text-zinc-600">{errorMessage(groups.error)}</p>
        <Button variant="secondary" onClick={() => void groups.refetch()}>
          다시 시도
        </Button>
      </main>
    );
  }

  // 고른 그룹이 없거나 사라졌으면 첫 그룹
  const groupId = groups.data.some((group) => group.id === pickedGroupId) ? (pickedGroupId as number) : (groups.data[0]?.id ?? 0);

  // 기간 오류(INVALID_ROOM_PERIOD)는 칸별 오류 없이 code로 온다 → 끝 날짜 칸 아래
  const periodError = hasCode(mutation.error, "INVALID_ROOM_PERIOD") ? errorMessage(mutation.error) : undefined;
  const nameError = localErrors.name ?? fieldError(mutation.error, "name");
  const endDateError = localErrors.endDate ?? periodError;
  const timezoneError = fieldError(mutation.error, "timezone");
  const commonError = mutation.isError && !nameError && !endDateError && !timezoneError ? errorMessage(mutation.error) : null;

  return (
    <main className="mx-auto flex w-full max-w-lg flex-col gap-4 px-4 py-8">
      <h1 className="text-[22px] font-semibold">새 조율방 만들기</h1>
      {groups.data.length === 0 ? (
        <p className={`${cardClass} text-sm text-zinc-600`}>
          공통 캘린더를 넣을 그룹이 필요해요.{" "}
          <Link href="/calendar" className="text-brand hover:underline">
            캘린더 화면
          </Link>
          에서 그룹이나 캘린더를 먼저 만들어 주세요.
        </p>
      ) : (
        <form noValidate onSubmit={handleSubmit} className={`${cardClass} flex flex-col gap-4`}>
          <TextField
            label="방 이름"
            name="name"
            maxLength={100}
            error={nameError}
            onChange={() => setLocalErrors((previous) => ({ ...previous, name: undefined }))}
          />
          <div className="grid grid-cols-2 gap-2">
            <TextField
              label="시작 날짜"
              name="startDate"
              type="date"
              max="9999-12-31"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
            />
            <TextField
              label="끝 날짜 (포함)"
              name="endDate"
              type="date"
              min={startDate}
              max="9999-12-31"
              defaultValue={today}
              error={endDateError}
              onChange={() => setLocalErrors((previous) => ({ ...previous, endDate: undefined }))}
            />
            <TextField label="매일 시작" name="dailyStartTime" type="time" defaultValue="09:00" />
            <TextField label="매일 끝" name="dailyEndTime" type="time" defaultValue="18:00" />
          </div>
          <p className="text-xs text-muted">끝 시간이 시작보다 이르거나 같으면 다음 날로 넘어가요 (같으면 24시간).</p>
          <TimezoneCombobox label="시간대" name="timezone" value={timezone} onChange={setTimezone} error={timezoneError} />
          <Dropdown
            label="공통 캘린더를 넣을 내 그룹"
            name="groupId"
            value={groupId}
            onChange={setPickedGroupId}
            options={groups.data.map((group) => ({ value: group.id, label: group.name }))}
          />
          {commonError && (
            <p role="alert" className="text-sm text-red-600">
              {commonError}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Link href="/rooms" className={buttonClass("ghost")}>
              취소
            </Link>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "만드는 중…" : "만들기"}
            </Button>
          </div>
        </form>
      )}
    </main>
  );
}
