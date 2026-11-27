"use client";

import { useId } from "react";
import { cn } from "@/shared/lib/cn";
import { CALENDAR_COLORS } from "../color";

type Props = { value: string; onChange: (color: string) => void };

// 미리 정한 색 8개 + 직접 고르기. 미리 정한 색은 숨긴 radio라 방향키로 옮기고 Tab 한 번으로 빠져나간다
// 고른 표시는 색과 상관없는 링. 값은 늘 소문자 #rrggbb
export function ColorPicker({ value, onChange }: Props) {
  const name = useId();
  const current = value.toLowerCase();
  const isPreset = CALENDAR_COLORS.some((color) => color.value === current);

  return (
    <fieldset className="flex flex-col gap-1">
      <legend className="mb-1 text-sm font-medium text-zinc-700">색</legend>
      <div className="flex flex-wrap items-center gap-2.5">
        {CALENDAR_COLORS.map((color) => (
          <label key={color.value} title={color.name} className="cursor-pointer">
            <input
              type="radio"
              name={name}
              value={color.value}
              checked={current === color.value}
              onChange={() => onChange(color.value)}
              aria-label={color.name}
              className="peer sr-only"
            />
            <span
              className="block h-7 w-7 rounded-full peer-checked:ring-2 peer-checked:ring-zinc-900 peer-checked:ring-offset-2 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4 peer-focus-visible:outline-brand"
              style={{ backgroundColor: color.value }}
            />
          </label>
        ))}
        <input
          type="color"
          value={current}
          onChange={(event) => onChange(event.target.value.toLowerCase())}
          aria-label="직접 고르기"
          title="직접 고르기"
          className={cn("h-7 w-9 cursor-pointer rounded-lg border border-line-strong", !isPreset && "ring-2 ring-zinc-900 ring-offset-2")}
        />
      </div>
    </fieldset>
  );
}
