"use client";

import { TZDate } from "@date-fns/tz";
import { addDays, format, isSameDay } from "date-fns";
import { useState, type ReactNode } from "react";
import { colorOf } from "@/features/calendar/color";
import { canEditEvents } from "@/features/calendar/permissions";
import type { CalendarSummary } from "@/features/calendar/types";
import { errorMessage } from "@/shared/api/errors";
import { browserTimeZone, sameTimeZone } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { Modal } from "@/shared/ui/Modal";
import { Spinner } from "@/shared/ui/Spinner";
import { isGoneError, useCreateEvent, useDeleteEvent, useEvent, useUpdateEvent } from "../hooks";
import { eventToValues, newEventValues } from "../lib/eventForm";
import { describeRepeat, hasCount, parseRrule } from "../lib/recurrence";
import type { EventUpdateBody } from "../types";
import type { EventItem, RecurrenceScope } from "../types";
import { EventForm } from "./EventForm";

// 캘린더 화면에서 여는 일정 창들: 만들기 → (일정 클릭) 상세 → 수정·삭제
export type EventDialogState =
  | { kind: "create"; day: Date; start?: string; end?: string; allDay?: boolean } // start·end: 시간 격자 빈 칸, allDay: 종일 줄
  | { kind: "detail"; event: EventItem }
  | { kind: "edit"; event: EventItem; scope?: RecurrenceScope };

type Props = {
  state: EventDialogState;
  calendars: CalendarSummary[];
  hiddenCalendarIds: ReadonlySet<number>;
  onChange: (state: EventDialogState | null) => void;
};

export function EventDialogs({ state, calendars, hiddenCalendarIds, onChange }: Props) {
  const editable = calendars.filter((calendar) => canEditEvents(calendar.role));
  const close = () => onChange(null);

  if (state.kind === "create") {
    return <CreateEventDialog day={state.day} options={{ start: state.start, end: state.end, allDay: state.allDay }} calendars={editable} hiddenCalendarIds={hiddenCalendarIds} onClose={close} />;
  }
  if (state.kind === "detail") {
    return <EventDetailDialog event={state.event} calendars={calendars} onEdit={(scope) => onChange({ kind: "edit", event: state.event, scope })} onClose={close} />;
  }
  return <EditEventDialog event={state.event} scope={state.scope} calendars={editable} onClose={close} />;
}

// 입력한 내용이 있으면 닫기 전에 버릴지 묻는다
function useDiscardGuard(onClose: () => void) {
  const [dirty, setDirty] = useState(false);
  const [asking, setAsking] = useState(false);
  const requestClose = () => (dirty ? setAsking(true) : onClose());
  const confirm = asking ? (
    <ConfirmDialog
      title="작성 중인 내용"
      message="작성 중인 내용을 버릴까요?"
      confirmLabel="버리기"
      danger
      onConfirm={onClose}
      onClose={() => setAsking(false)}
    />
  ) : null;
  return { setDirty, requestClose, confirm };
}

// 새 일정의 캘린더: 편집할 수 있는 캘린더 가운데 사이드바에서 켜 둔 첫 캘린더 (다 꺼져 있으면 첫 캘린더)
function CreateEventDialog({
  day,
  options,
  calendars,
  hiddenCalendarIds,
  onClose,
}: {
  day: Date;
  options: { start?: string; end?: string; allDay?: boolean };
  calendars: CalendarSummary[];
  hiddenCalendarIds: ReadonlySet<number>;
  onClose: () => void;
}) {
  const mutation = useCreateEvent();
  const guard = useDiscardGuard(onClose);
  const [initial] = useState(() => {
    const calendar = calendars.find((item) => !hiddenCalendarIds.has(item.id)) ?? calendars[0];
    return calendar ? newEventValues(day, calendar.id, new Date(), options) : null;
  });

  if (!initial) {
    return (
      <Modal title="새 일정" onClose={onClose} width="sm">
        <p className="text-sm text-zinc-600">일정을 넣을 수 있는 캘린더가 없어요. 왼쪽에서 캘린더를 먼저 만들어 주세요.</p>
      </Modal>
    );
  }
  return (
    <Modal title="새 일정" onClose={guard.requestClose} busy={mutation.isPending}>
      <EventForm
        initial={initial}
        calendars={calendars}
        calendarLocked={false}
        occurrenceOnly={false}
        originalRrule={null}
        submitLabel="만들기"
        pending={mutation.isPending}
        error={mutation.error}
        onSubmit={(body, calendarId) => mutation.mutate({ ...body, calendarId }, { onSuccess: onClose })}
        onCancel={guard.requestClose}
        onDirtyChange={guard.setDirty}
      />
      {guard.confirm}
    </Modal>
  );
}

// "모든 일정" 수정은 시리즈의 첫 회차 기준으로 채운다 (고른 회차로 채우면 반복 시작이 그 날로 옮겨진다)
function EditEventDialog({
  event,
  scope,
  calendars,
  onClose,
}: {
  event: EventItem;
  scope?: RecurrenceScope;
  calendars: CalendarSummary[];
  onClose: () => void;
}) {
  // "이 일정과 이후" + COUNT 반복: 첫 회차인지 보려면 시리즈 시작이 필요하다
  const countRule = scope === "THIS_AND_FOLLOWING" && hasCount(event.rrule);
  const series = useEvent(event.id, scope === "ALL" || countRule);
  const mutation = useUpdateEvent();
  const guard = useDiscardGuard(onClose);
  // "모든 일정"에서 일정 값이 바뀌면 서버가 예외 회차를 지운다 → 저장 전에 묻는다
  const [confirmBody, setConfirmBody] = useState<EventUpdateBody | null>(null);
  const source = scope === "ALL" ? series.data : countRule && !series.data ? undefined : event;
  // 남은 횟수는 계산하지 않는다: 첫 회차가 아니면 종료를 다시 고르기 전까지 막는다 (첫 회차면 서버가 ALL과 같게 처리)
  const countBlocked = countRule && series.data !== undefined && Date.parse(series.data.startAt) !== Date.parse(event.occurrenceStart);

  function save(body: EventUpdateBody) {
    mutation.mutate({ id: event.id, scope, occurrenceStart: scope ? event.occurrenceStart : undefined, body }, { onSuccess: onClose });
  }
  const title = scope === "THIS" ? "이 일정만 수정" : scope === "THIS_AND_FOLLOWING" ? "이 일정과 이후 수정" : scope === "ALL" ? "모든 일정 수정" : "일정 수정";

  if (isGoneError(mutation.error) || isGoneError(series.error)) {
    return <GoneDialog title={title} error={mutation.error ?? series.error} onClose={onClose} />;
  }

  return (
    <Modal title={title} onClose={guard.requestClose} busy={mutation.isPending}>
      {!source ? (
        series.isError ? <p className="text-sm text-red-600">{errorMessage(series.error)}</p> : <Spinner />
      ) : (
        <EventForm
          initial={eventToValues(source)}
          calendars={calendars}
          calendarLocked
          occurrenceOnly={scope === "THIS"}
          originalRrule={source.rrule}
          countBlocked={countBlocked}
          submitLabel="저장"
          pending={mutation.isPending}
          error={mutation.error}
          onSubmit={(body, _calendarId, changed) => {
            if (scope === "ALL" && source.rrule !== null && changed) setConfirmBody(body);
            else save(body);
          }}
          onCancel={guard.requestClose}
          onDirtyChange={guard.setDirty}
        />
      )}
      {guard.confirm}
      {confirmBody && (
        <ConfirmDialog
          title="모든 일정 수정"
          message="일정 시간이나 반복을 바꾸면, 이 일정만 바꾸거나 지운 회차가 원래대로 돌아가요. 저장할까요?"
          confirmLabel="저장"
          pending={mutation.isPending}
          error={mutation.isError ? errorMessage(mutation.error) : null}
          onConfirm={() => save(confirmBody)}
          onClose={() => {
            mutation.reset();
            setConfirmBody(null);
          }}
        />
      )}
    </Modal>
  );
}

// 다른 사람이 먼저 지운 일정: 목록은 이미 다시 불러왔고, 닫기만 남긴다
function GoneDialog({ title, error, onClose }: { title: string; error: unknown; onClose: () => void }) {
  return (
    <Modal title={title} onClose={onClose} width="sm">
      <p role="alert" className="text-sm text-zinc-700">
        {errorMessage(error)} 다른 곳에서 지워졌을 수 있어요.
      </p>
      <div className="mt-5 flex justify-end">
        <Button onClick={onClose} data-autofocus>
          닫기
        </Button>
      </div>
    </Modal>
  );
}

const scopeLabels: Record<RecurrenceScope, string> = {
  THIS: "이 일정만",
  THIS_AND_FOLLOWING: "이 일정과 이후",
  ALL: "모든 일정",
};

function EventDetailDialog({
  event,
  calendars,
  onEdit,
  onClose,
}: {
  event: EventItem;
  calendars: CalendarSummary[];
  onEdit: (scope?: RecurrenceScope) => void;
  onClose: () => void;
}) {
  const calendar = calendars.find((item) => item.id === event.calendarId);
  const category = calendar?.categories.find((item) => item.id === event.categoryId);
  const canEdit = canEditEvents(calendar?.role);
  const isRecurring = event.rrule !== null;
  // 반복 일정은 수정·삭제 전에 범위를 고른다. 반복이 아니면 삭제는 확인 창으로 묻는다
  const [choosing, setChoosing] = useState<"edit" | "delete" | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const deletion = useDeleteEvent();
  const [myTimeZone] = useState(browserTimeZone);

  function remove(scope?: RecurrenceScope) {
    deletion.mutate({ id: event.id, scope, occurrenceStart: scope ? event.occurrenceStart : undefined }, { onSuccess: onClose });
  }

  if (isGoneError(deletion.error)) return <GoneDialog title={event.title} error={deletion.error} onClose={onClose} />;

  return (
    <Modal title={event.title} onClose={onClose} busy={deletion.isPending}>
      <dl className="flex flex-col gap-2 text-sm">
        <Row label="시간">{periodText(event)}</Row>
        {!event.isAllDay && !sameTimeZone(event.timezone, myTimeZone) && (
          <Row label="시간대">
            {event.timezone} · 그곳 시간 {zonePeriodText(event)}
            <span className="block text-xs text-zinc-500">위 시간은 내 시간대({myTimeZone}) 기준이에요.</span>
          </Row>
        )}
        <Row label="캘린더">
          <span className="inline-flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: colorOf(calendar?.color) }} />
            {calendar?.name ?? "알 수 없음"}
          </span>
        </Row>
        {category && <Row label="카테고리">{category.name}</Row>}
        {isRecurring && <Row label="반복">{repeatText(event)}</Row>}
        {event.location && <Row label="장소">{event.location}</Row>}
        {event.description && <Row label="설명">{event.description}</Row>}
      </dl>

      {deletion.isError && !confirmingDelete && (
        <p role="alert" className="mt-3 text-sm text-red-600">
          {errorMessage(deletion.error)}
        </p>
      )}

      {canEdit && choosing === null && (
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="danger" onClick={() => (isRecurring ? setChoosing("delete") : setConfirmingDelete(true))} disabled={deletion.isPending}>
            삭제
          </Button>
          <Button onClick={() => (isRecurring ? setChoosing("edit") : onEdit())}>수정</Button>
        </div>
      )}

      {choosing && (
        <div className="mt-5 flex flex-col gap-2 rounded-2xl bg-field p-3">
          <p className="text-sm font-medium">{choosing === "edit" ? "어떤 일정을 수정할까요?" : "어떤 일정을 삭제할까요?"}</p>
          {(Object.keys(scopeLabels) as RecurrenceScope[]).map((scope) => (
            <Button
              key={scope}
              variant="secondary"
              disabled={deletion.isPending}
              onClick={() => (choosing === "edit" ? onEdit(scope) : remove(scope))}
            >
              {scopeLabels[scope]}
            </Button>
          ))}
          <Button variant="ghost" onClick={() => setChoosing(null)}>
            취소
          </Button>
        </div>
      )}

      {confirmingDelete && (
        <ConfirmDialog
          title="일정 삭제"
          message={`'${event.title}' 일정을 삭제할까요?`}
          confirmLabel="삭제"
          danger
          pending={deletion.isPending}
          error={deletion.isError ? errorMessage(deletion.error) : null}
          onConfirm={() => remove()}
          onClose={() => {
            deletion.reset();
            setConfirmingDelete(false);
          }}
        />
      )}
    </Modal>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-3">
      <dt className="w-16 shrink-0 text-muted">{label}</dt>
      <dd className="min-w-0 whitespace-pre-wrap break-words text-foreground">{children}</dd>
    </div>
  );
}

// 종일: UTC 날짜 그대로(끝은 하루 뺌) / 시간: 브라우저 시간대
export function periodText(event: EventItem): string {
  if (event.isAllDay) {
    const start = new Date(event.startAt);
    const first = new Date(start.getUTCFullYear(), start.getUTCMonth(), start.getUTCDate());
    const end = new Date(event.endAt);
    const last = addDays(new Date(end.getUTCFullYear(), end.getUTCMonth(), end.getUTCDate()), -1);
    return isSameDay(first, last) || last < first
      ? `${format(first, "yyyy년 M월 d일")} (종일)`
      : `${format(first, "yyyy년 M월 d일")} – ${format(last, "M월 d일")} (종일)`;
  }
  const start = new Date(event.startAt);
  const end = new Date(event.endAt);
  return isSameDay(start, end)
    ? `${format(start, "yyyy년 M월 d일 HH:mm")} – ${format(end, "HH:mm")}`
    : `${format(start, "yyyy년 M월 d일 HH:mm")} – ${format(end, "M월 d일 HH:mm")}`;
}

// 시간 일정을 일정 시간대 기준으로 ("11월 25일 20:00 – 21:00")
function zonePeriodText(event: EventItem): string {
  try {
    const start = new TZDate(Date.parse(event.startAt), event.timezone);
    const end = new TZDate(Date.parse(event.endAt), event.timezone);
    return isSameDay(start, end) ? `${format(start, "M월 d일 HH:mm")} – ${format(end, "HH:mm")}` : `${format(start, "M월 d일 HH:mm")} – ${format(end, "M월 d일 HH:mm")}`;
  } catch {
    return "";
  }
}

// 반복 문구: 이 회차의 날짜(시간 일정은 일정 시간대, 종일은 UTC 날짜) 기준
function repeatText(event: EventItem): string {
  let startDate = event.startAt.slice(0, 10);
  if (!event.isAllDay) {
    try {
      startDate = format(new TZDate(Date.parse(event.startAt), event.timezone), "yyyy-MM-dd");
    } catch {
      // 시간대를 모르면 UTC 날짜
    }
  }
  return describeRepeat(parseRrule(event.rrule, { startDate, isAllDay: event.isAllDay, timezone: event.timezone }), startDate);
}
