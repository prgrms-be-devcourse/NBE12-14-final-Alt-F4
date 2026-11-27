"use client";

import { useState, type FormEvent } from "react";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { Dropdown } from "@/shared/ui/Dropdown";
import { fieldBaseClass } from "@/shared/ui/fields";
import { Modal } from "@/shared/ui/Modal";
import {
  defaultUntilDate,
  isLastOf,
  MAX_COUNT,
  MAX_INTERVAL,
  nthOf,
  ordinalName,
  repeatProblem,
  WEEKDAY_CODES,
  WEEKDAY_NAMES,
  weekdayOf,
  type CustomRule,
  type Weekday,
} from "../lib/recurrence";

type Props = {
  initial: CustomRule;
  startDate: string; // 폼 시간대의 시작 날짜
  onDone: (rule: CustomRule) => void;
  onClose: () => void;
};

const unitLabels: Record<CustomRule["unit"], string> = { DAY: "일", WEEK: "주", MONTH: "개월", YEAR: "년" };
// 공용 입력 칸과 같은 모양 (꺼진 칸은 글자만 흐리게)
const inputClass = cn(fieldBaseClass, "h-9 border-transparent");

// 맞춤 반복 창 (구글의 "반복 설정"과 같은 칸)
// 시작 요일은 끌 수 없다: 백엔드는 시작일이 첫 회차가 아니면 400
// EventForm의 <form> 밖에 그린다. 이 창 안의 Enter는 "완료"
export function CustomRepeatDialog({ initial, startDate, onDone, onClose }: Props) {
  const [rule, setRule] = useState(initial);
  // 숫자 칸은 지우고 다시 쓰는 동안 빈칸일 수 있어 글자로 들고 있는다
  const [intervalText, setIntervalText] = useState(String(initial.interval));
  const [untilDate, setUntilDate] = useState(initial.end.type === "UNTIL" ? initial.end.date : defaultUntilDate(startDate));
  const [countText, setCountText] = useState(String(initial.end.type === "COUNT" ? initial.end.count : 13));
  const [error, setError] = useState<string | null>(null);
  const startDay = weekdayOf(startDate);
  const weekday = WEEKDAY_NAMES[WEEKDAY_CODES.indexOf(startDay)];
  const nth = nthOf(startDate);
  const last = isLastOf(startDate);

  function toggleDay(day: Weekday) {
    if (day === startDay) return;
    setRule((previous) => ({
      ...previous,
      weekdays: previous.weekdays.includes(day) ? previous.weekdays.filter((item) => item !== day) : [...previous.weekdays, day],
    }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const end: CustomRule["end"] =
      rule.end.type === "UNTIL"
        ? // 날짜를 바꾸지 않았으면 읽어 온 원래 UNTIL(raw)을 그대로 둔다
          { type: "UNTIL", date: untilDate, raw: initial.end.type === "UNTIL" && initial.end.date === untilDate ? initial.end.raw : undefined }
        : rule.end.type === "COUNT"
          ? { type: "COUNT", count: Number(countText) }
          : { type: "NEVER" };
    const next: CustomRule = { ...rule, interval: Number(intervalText), end };
    const problem = repeatProblem({ kind: "CUSTOM", rule: next }, startDate);
    if (problem) {
      setError(problem);
      return;
    }
    onDone(next);
  }

  const setEndType = (type: CustomRule["end"]["type"]) => {
    setError(null);
    setRule((previous) => ({
      ...previous,
      end: type === "UNTIL" ? { type, date: untilDate } : type === "COUNT" ? { type, count: Number(countText) } : { type },
    }));
  };

  return (
    <Modal title="반복 설정" onClose={onClose} width="sm">
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-5">
        <div className="flex items-center gap-2">
          <label htmlFor="repeat-interval" className="text-sm text-zinc-700">
            반복 주기
          </label>
          <input
            id="repeat-interval"
            type="number"
            min={1}
            max={MAX_INTERVAL}
            value={intervalText}
            onChange={(event) => {
              setIntervalText(event.target.value);
              setError(null);
            }}
            className={cn(inputClass, "w-16")}
            data-autofocus
          />
          <Dropdown
            label="반복 단위"
            hideLabel
            className="w-24"
            value={rule.unit}
            onChange={(unit) => setRule((previous) => ({ ...previous, unit }))}
            options={(Object.keys(unitLabels) as CustomRule["unit"][]).map((unit) => ({ value: unit, label: unitLabels[unit] }))}
          />
        </div>

        {rule.unit === "WEEK" && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-[13px] font-medium text-zinc-700">반복 요일</legend>
            <div className="flex flex-wrap gap-1.5">
              {WEEKDAY_CODES.map((day, index) => {
                const selected = rule.weekdays.includes(day);
                const locked = day === startDay;
                return (
                  <button
                    key={day}
                    type="button"
                    aria-pressed={selected}
                    aria-disabled={locked}
                    title={locked ? "시작 날짜의 요일이에요" : undefined}
                    aria-label={`${WEEKDAY_NAMES[index]}요일`}
                    onClick={() => toggleDay(day)}
                    className={cn(
                      "h-8 w-8 rounded-full text-xs font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand",
                      selected ? "bg-brand text-white" : "bg-field text-zinc-700 hover:bg-zinc-200",
                      locked && "cursor-not-allowed",
                    )}
                  >
                    {WEEKDAY_NAMES[index]}
                  </button>
                );
              })}
            </div>
          </fieldset>
        )}

        {rule.unit === "MONTH" && (
          <Dropdown
            label="매월 반복 방식"
            hideLabel
            className="self-start"
            value={rule.monthly}
            onChange={(monthly) => setRule((previous) => ({ ...previous, monthly }))}
            options={[
              { value: "DAY" as const, label: `매월 ${Number(startDate.slice(8, 10))}일` },
              ...(nth <= 4 ? [{ value: "NTH" as const, label: `매월 ${ordinalName(nth)} ${weekday}요일` }] : []),
              ...(last ? [{ value: "LAST" as const, label: `매월 마지막 ${weekday}요일` }] : []),
            ]}
          />
        )}

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[13px] font-medium text-zinc-700">종료</legend>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" className="accent-brand" name="repeat-end" checked={rule.end.type === "NEVER"} onChange={() => setEndType("NEVER")} />
            없음
          </label>
          <div className="flex items-center gap-2 text-sm">
            <label className="flex w-16 items-center gap-2">
              <input type="radio" className="accent-brand" name="repeat-end" checked={rule.end.type === "UNTIL"} onChange={() => setEndType("UNTIL")} />
              날짜:
            </label>
            <input
              type="date"
              aria-label="종료 날짜"
              min={startDate}
              max="9999-12-31"
              value={untilDate}
              disabled={rule.end.type !== "UNTIL"}
              onChange={(event) => {
                setUntilDate(event.target.value);
                setError(null);
              }}
              className={inputClass}
            />
          </div>
          <div className="flex items-center gap-2 text-sm">
            <label className="flex w-16 items-center gap-2">
              <input type="radio" className="accent-brand" name="repeat-end" checked={rule.end.type === "COUNT"} onChange={() => setEndType("COUNT")} />
              다음
            </label>
            <input
              type="number"
              aria-label="반복 횟수"
              min={1}
              max={MAX_COUNT}
              value={countText}
              disabled={rule.end.type !== "COUNT"}
              onChange={(event) => {
                setCountText(event.target.value);
                setError(null);
              }}
              className={cn(inputClass, "w-20")}
            />
            <span className="text-zinc-600">회 반복</span>
          </div>
        </fieldset>

        {error && (
          <p role="alert" className="text-sm text-red-600">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit">완료</Button>
        </div>
      </form>
    </Modal>
  );
}
