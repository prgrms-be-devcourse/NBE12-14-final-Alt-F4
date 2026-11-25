"use client";

import { AlignLeft, CalendarDays, Clock, MapPin, Tag } from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import { colorOf } from "@/features/calendar/color";
import { CreateCategoryDialog } from "@/features/calendar/components/CreateCategoryDialog";
import type { CalendarSummary } from "@/features/calendar/types";
import { errorMessage, fieldError } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { browserTimeZone, sameTimeZone } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { ColorDot, Dropdown } from "@/shared/ui/Dropdown";
import { ErrorText, fieldBaseClass, inputClass } from "@/shared/ui/fields";
import { collapsedEndDate, dayDiff, moveStart, scheduleChanged, valuesToBody, type EventFormValues } from "../lib/eventForm";
import {
  defaultCustomRule,
  describeRepeat,
  presetAvailable,
  presetLabel,
  PRESETS,
  presetToCustom,
  repeatProblem,
  simplifyCustom,
  type CustomRule,
  type Repeat,
} from "../lib/recurrence";
import type { EventUpdateBody } from "../types";
import { CategoryChips } from "./CategoryChips";
import { CustomRepeatDialog } from "./CustomRepeatDialog";
import { EventTimezoneDialog } from "./EventTimezoneDialog";


type Props = {
  initial: EventFormValues;
  calendars: CalendarSummary[]; // 일정을 넣을 수 있는 캘린더 (OWNER·EDITOR)
  calendarLocked: boolean; // 수정할 때는 캘린더를 바꿀 수 없다 (API에 calendarId가 없음)
  occurrenceOnly: boolean; // "이 일정만" 수정: 종일 여부·카테고리·반복은 못 바꾼다 (서버가 시간대·규칙을 무시)
  originalRrule: string | null; // 반복을 건드리지 않으면 이 문자열을 그대로 보낸다
  countBlocked?: boolean; // "이 일정과 이후" + COUNT 반복(첫 회차 아님): 종료를 다시 고르기 전까지 막는다
  submitLabel: string;
  pending: boolean;
  error: unknown;
  // scheduleChanged: 일정 값(시간·종일·시간대·반복)이 바뀌었는지 ("모든 일정"에서 예외 회차가 지워지는지 묻는 데 쓴다)
  onSubmit: (body: EventUpdateBody, calendarId: number, scheduleChanged: boolean) => void;
  onCancel: () => void;
  onDirtyChange?: (dirty: boolean) => void; // 처음 값과 달라졌는지 (창을 닫을 때 버릴지 묻는다)
};

const validDate = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value);
const fieldClass = cn(inputClass, "border-transparent");
// 날짜·시간 칸은 한 줄에 넷이 서도록 조금 낮게
const timeFieldClass = cn(fieldBaseClass, "h-9 border-transparent");
// 글자 크기는 쓰는 곳에서 붙인다 (시간 줄 13px, 위치·설명 14px)
const linkButtonClass = "rounded-md hover:text-foreground focus-visible:outline-2 focus-visible:outline-brand";
const timeLinkClass = cn(linkButtonClass, "text-[13px] text-zinc-600");
const iconClass = "h-5 w-5";

// 구글 캘린더의 빠른 만들기 창처럼: 큰 제목 → 시간 줄(종일·시간대·반복) → 캘린더 → 카테고리 → 위치·설명(눌러서 펼침)
// 시간대 창·맞춤 반복 창·새 카테고리 창은 <form> 밖에 그린다 (Modal은 포털이 없어 안에 두면 창 안의 Enter가 이 폼을 저장한다)
export function EventForm({
  initial,
  calendars,
  calendarLocked,
  occurrenceOnly,
  originalRrule,
  countBlocked = false,
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
  onDirtyChange,
}: Props) {
  const [values, setValues] = useState(initial);
  const [formError, setFormError] = useState<string | null>(null);
  // 끝 날짜 칸: 이틀 이상 차이 나는 일정만 처음부터 펼친다. 다음 날까지인 일정은 접힌 채 "(다음 날)"
  // 한 번 펼치면 계속 펼쳐 둔다 (입력 중에 칸이 사라져 포커스를 잃지 않게)
  const [showEndDate, setShowEndDate] = useState(dayDiff(initial.startDate, initial.endDate) >= 2);
  const [showLocation, setShowLocation] = useState(initial.location !== "");
  const [showDescription, setShowDescription] = useState(initial.description !== "");
  const [dialog, setDialog] = useState<"timezone" | "custom" | "category" | null>(null);
  const [myTimeZone] = useState(browserTimeZone);

  const calendar = calendars.find((item) => item.id === values.calendarId);
  const categories = calendar?.categories ?? [];
  // 시작 날짜를 고치는 중(빈칸)이면 처음 날짜로 문구를 만든다
  const labelDate = validDate(values.startDate) ? values.startDate : initial.startDate;
  const repeatChanged = JSON.stringify(values.repeat) !== JSON.stringify(initial.repeat);
  const countStillBlocked = countBlocked && !repeatChanged;
  const problem = repeatProblem(values.repeat, labelDate);

  // 이벤트 핸들러에서만 부른다
  function update(next: EventFormValues) {
    setValues(next);
    setFormError(null);
    onDirtyChange?.(JSON.stringify(next) !== JSON.stringify(initial));
  }
  const set = <K extends keyof EventFormValues>(key: K, value: EventFormValues[K]) => update({ ...values, [key]: value });
  const endCollapsed = !values.isAllDay && !showEndDate;

  // 시작을 옮기면 끝도 같은 길이로 옮긴다 (moveStart). 이틀 이상 차이가 나면 끝 날짜 칸을 펼친다
  function changeStart(field: "startDate" | "startTime", value: string) {
    const next = moveStart(values, field, value);
    if (dayDiff(next.startDate, next.endDate) >= 2) setShowEndDate(true);
    update(next);
  }

  // 끝 날짜 칸이 접혀 있으면 끝 시간으로 끝 날짜를 정한다 (시작보다 이르거나 같으면 다음 날)
  function changeEndTime(endTime: string) {
    update(endCollapsed ? { ...values, endTime, endDate: collapsedEndDate(values.startDate, values.startTime, endTime) } : { ...values, endTime });
  }

  function changeEndDate(endDate: string) {
    if (dayDiff(values.startDate, endDate) >= 2) setShowEndDate(true);
    update({ ...values, endDate });
  }

  // 종일을 끌 때: 여러 날이면 끝 날짜 칸을 펼치고, 아니면 접힌 칸 규칙으로 끝 날짜를 다시 정한다
  function changeAllDay(isAllDay: boolean) {
    if (isAllDay || showEndDate) return update({ ...values, isAllDay });
    if (dayDiff(values.startDate, values.endDate) >= 2) {
      setShowEndDate(true);
      return update({ ...values, isAllDay });
    }
    update({ ...values, isAllDay, endDate: collapsedEndDate(values.startDate, values.startTime, values.endTime) });
  }
  const nextDayEnd = endCollapsed && dayDiff(values.startDate, values.endDate) === 1;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (countStillBlocked) {
      setFormError("남은 횟수를 계산하지 않아요. 종료(없음·날짜·횟수)를 다시 골라 주세요.");
      return;
    }
    const result = valuesToBody(values, initial, originalRrule);
    if ("error" in result) {
      setFormError(result.error);
      return;
    }
    onSubmit(result.body, values.calendarId, scheduleChanged(values, initial));
  }

  // 반복 select: 날짜에 맞춘 목록 + (있으면) 맞춤 규칙·기존 규칙 줄 + "맞춤…"
  const repeatValue = repeatKey(values.repeat);
  function handleRepeatChange(key: string) {
    if (key === "OPEN_CUSTOM") {
      setDialog("custom");
      return;
    }
    if (key === "NONE") set("repeat", { kind: "NONE" });
    else if (key.startsWith("PRESET:")) set("repeat", { kind: "PRESET", preset: key.slice(7) as (typeof PRESETS)[number] });
  }
  const customInitial: CustomRule =
    values.repeat.kind === "CUSTOM"
      ? values.repeat.rule
      : values.repeat.kind === "PRESET"
        ? presetToCustom(values.repeat.preset, labelDate)
        : defaultCustomRule(labelDate);

  // 서버 칸 오류는 그 칸 아래에, 나머지는 아래 공통 자리에
  const titleError = fieldError(error, "title");
  const locationError = fieldError(error, "location");
  const timeError = ["startAt", "endAt", "startDate", "endDate"].map((field) => fieldError(error, field)).find(Boolean);
  const hasFieldError = Boolean(titleError || locationError || timeError);
  const commonError = formError ?? (error && !hasFieldError ? errorMessage(error) : null);
  const otherZone = !sameTimeZone(values.timezone, myTimeZone);

  return (
    <>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-[18px]">
        <div className="flex flex-col gap-1">
          <input
            name="title"
            aria-label="제목"
            placeholder="제목 추가"
            value={values.title}
            onChange={(event) => set("title", event.target.value)}
            maxLength={200}
            aria-invalid={titleError ? true : undefined}
            aria-describedby={titleError ? "event-title-error" : undefined}
            data-autofocus
            className="border-b-2 border-line-strong bg-transparent pb-2.5 pt-1 text-2xl font-semibold outline-none transition-colors placeholder:font-medium placeholder:text-zinc-400 focus:border-brand"
          />
          <ErrorText id="event-title-error" error={titleError} />
        </div>

        <Row icon={<Clock className={iconClass} aria-hidden />}>
          <div className="flex flex-wrap items-center gap-1.5">
            <input
              type="date"
              aria-label="시작 날짜"
              max="9999-12-31"
              value={values.startDate}
              onChange={(event) => changeStart("startDate", event.target.value)}
              className={timeFieldClass}
            />
            {!values.isAllDay && (
              <input
                type="time"
                aria-label="시작 시간"
                value={values.startTime}
                onChange={(event) => changeStart("startTime", event.target.value)}
                className={timeFieldClass}
              />
            )}
            <span className="text-zinc-400">–</span>
            {(values.isAllDay || showEndDate) && (
              <input
                type="date"
                aria-label="끝 날짜"
                max="9999-12-31"
                value={values.endDate}
                onChange={(event) => changeEndDate(event.target.value)}
                className={timeFieldClass}
              />
            )}
            {!values.isAllDay && (
              <input type="time" aria-label="끝 시간" value={values.endTime} onChange={(event) => changeEndTime(event.target.value)} className={timeFieldClass} />
            )}
            {nextDayEnd && <span className="text-xs text-muted">(다음 날)</span>}
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <label className="flex items-center gap-2 text-[13px] text-zinc-700">
              <input type="checkbox" checked={values.isAllDay} disabled={occurrenceOnly} onChange={(event) => changeAllDay(event.target.checked)} className="h-4 w-4 accent-brand" />
              종일
            </label>
            {!values.isAllDay && !showEndDate && (
              <button type="button" onClick={() => setShowEndDate(true)} className={timeLinkClass}>
                끝 날짜 지정
              </button>
            )}
            {!values.isAllDay &&
              (occurrenceOnly ? (
                // "이 일정만"은 서버가 시간대를 무시한다. 폼 시각이 이 시간대 기준이라 글자로만 보여 준다
                <span className="text-[13px] text-muted">시간대: {values.timezone}</span>
              ) : (
                <button type="button" onClick={() => setDialog("timezone")} className={otherZone ? cn(linkButtonClass, "text-[13px] font-medium text-brand-ink") : timeLinkClass}>
                  {otherZone ? `시간대: ${values.timezone}` : "시간대"}
                </button>
              ))}
          </div>

          <Dropdown
            label="반복"
            hideLabel
            className="self-start"
            value={repeatValue}
            disabled={occurrenceOnly}
            onChange={handleRepeatChange}
            options={[
              { value: "NONE", label: "반복 안 함" },
              ...PRESETS.filter((preset) => presetAvailable(preset, labelDate) || repeatValue === `PRESET:${preset}`).map((preset) => ({
                value: `PRESET:${preset}`,
                label: presetLabel(preset, labelDate),
              })),
              ...(values.repeat.kind === "CUSTOM" || values.repeat.kind === "KEEP" ? [{ value: repeatValue, label: describeRepeat(values.repeat, labelDate) }] : []),
              { type: "separator" as const },
              { value: "OPEN_CUSTOM", label: "맞춤…" },
            ]}
          />
          <ErrorText error={problem ?? (countStillBlocked ? "남은 횟수를 계산하지 않아요. 종료(없음·날짜·횟수)를 다시 골라 주세요." : undefined)} />
          <ErrorText error={timeError} />
        </Row>

        <Row icon={<CalendarDays className={iconClass} aria-hidden />}>
          <Dropdown
            label="캘린더"
            hideLabel
            value={values.calendarId}
            disabled={calendarLocked}
            onChange={(calendarId) => update({ ...values, calendarId, categoryId: null })}
            options={calendars.map((item) => ({ value: item.id, label: item.name, leading: <ColorDot color={colorOf(item.color)} /> }))}
          />
        </Row>

        <Row icon={<Tag className={iconClass} aria-hidden />}>
          <CategoryChips
            categories={categories}
            value={values.categoryId}
            disabled={occurrenceOnly}
            onChange={(categoryId) => set("categoryId", categoryId)}
            onCreate={() => setDialog("category")}
          />
        </Row>

        <Row icon={<MapPin className={iconClass} aria-hidden />}>
          {showLocation ? (
            <>
              <input
                aria-label="위치"
                placeholder="위치"
                value={values.location}
                onChange={(event) => set("location", event.target.value)}
                maxLength={255}
                autoFocus={initial.location === ""}
                aria-invalid={locationError ? true : undefined}
                className={fieldClass}
              />
              <ErrorText error={locationError} />
            </>
          ) : (
            <button type="button" onClick={() => setShowLocation(true)} className={cn(linkButtonClass, "self-start py-2 text-sm text-muted")}>
              위치 추가
            </button>
          )}
        </Row>

        <Row icon={<AlignLeft className={iconClass} aria-hidden />}>
          {showDescription ? (
            <textarea
              aria-label="설명"
              placeholder="설명"
              rows={3}
              value={values.description}
              onChange={(event) => set("description", event.target.value)}
              autoFocus={initial.description === ""}
              className={cn(fieldBaseClass, "border-transparent py-2")}
            />
          ) : (
            <button type="button" onClick={() => setShowDescription(true)} className={cn(linkButtonClass, "self-start py-2 text-sm text-muted")}>
              설명 추가
            </button>
          )}
        </Row>

        {occurrenceOnly && <p className="text-xs text-muted">이 일정만 바꿀 때는 제목·설명·장소·시간만 바꿀 수 있어요.</p>}
        {commonError && (
          <p role="alert" className="text-sm text-red-600">
            {commonError}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-1.5">
          <Button variant="ghost" onClick={onCancel}>
            취소
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "저장 중…" : submitLabel}
          </Button>
        </div>
      </form>

      {dialog === "timezone" && (
        <EventTimezoneDialog
          value={values.timezone}
          onDone={(timezone) => {
            set("timezone", timezone);
            setDialog(null);
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "category" && calendar && (
        <CreateCategoryDialog
          calendarId={calendar.id}
          calendarName={calendar.name}
          onCreated={(categoryId) => {
            set("categoryId", categoryId);
            setDialog(null);
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "custom" && (
        <CustomRepeatDialog
          initial={customInitial}
          startDate={labelDate}
          onDone={(rule) => {
            set("repeat", simplifyCustom(rule, labelDate));
            setDialog(null);
          }}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  );
}

function repeatKey(repeat: Repeat): string {
  if (repeat.kind === "PRESET") return `PRESET:${repeat.preset}`;
  return repeat.kind;
}

// 아이콘 + 내용 한 줄 (아이콘 칸 폭을 맞춰 글자가 한 줄로 서게)
function Row({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex gap-3.5">
      <span className="flex w-6 shrink-0 justify-center pt-2.5 text-muted">{icon}</span>
      <div className="flex min-w-0 flex-1 flex-col gap-2">{children}</div>
    </div>
  );
}
