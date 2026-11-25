"use client";

import { Plus } from "lucide-react";
import { useId } from "react";
import { colorOf, inkOf, tintOf } from "@/features/calendar/color";
import type { CategorySummary } from "@/features/calendar/types";
import { cn } from "@/shared/lib/cn";

type Props = {
  categories: CategorySummary[];
  value: number | null;
  disabled: boolean;
  onChange: (categoryId: number | null) => void;
  onCreate: () => void;
};

// 일정 폼의 카테고리 고르기 (시안처럼 알약 칩 한 줄)
// 겉은 칩, 속은 숨긴 radio: 방향키로 옮기고 Tab 한 번으로 빠져나간다 (ColorPicker와 같은 방식)
// 고른 칩은 카테고리 색을 옅게 깐 바탕 + 진한 글자. 끈 상태("이 일정만")에서는 "＋ 새 카테고리"를 숨긴다
export function CategoryChips({ categories, value, disabled, onChange, onCreate }: Props) {
  const name = useId();
  const options = [{ id: null, name: "없음", color: null }, ...categories];

  return (
    <fieldset disabled={disabled}>
      <legend className="sr-only">카테고리</legend>
      {/* 첫 줄 가운데를 줄 아이콘(높이 40 칸 기준)과 맞춘다 */}
      <div className="flex flex-wrap items-center gap-1.5 py-[5px]">
        {options.map((option) => {
          const checked = option.id === value;
          const color = option.id === null ? null : colorOf(option.color);
          return (
            <label key={option.id ?? "none"}>
              <input type="radio" name={name} checked={checked} onChange={() => onChange(option.id)} className="peer sr-only" />
              <span
                className={cn(
                  "flex h-[30px] cursor-pointer items-center gap-1.5 rounded-full border px-3 text-[12.5px] transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand peer-disabled:cursor-not-allowed peer-disabled:opacity-60",
                  checked
                    ? cn("border-transparent font-semibold", !color && "bg-brand-soft text-brand-ink")
                    : "border-line-strong bg-white text-zinc-700 hover:bg-zinc-50",
                )}
                style={checked && color ? { backgroundColor: tintOf(color), color: inkOf(color) } : undefined}
              >
                {color && <span aria-hidden className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: color }} />}
                {option.name}
              </span>
            </label>
          );
        })}
        {!disabled && (
          <button
            type="button"
            onClick={onCreate}
            className="flex h-[30px] items-center gap-1 rounded-full border border-dashed border-zinc-300 pl-2.5 pr-3 text-[12.5px] text-zinc-600 transition-colors hover:bg-zinc-50 hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />새 카테고리
          </button>
        )}
      </div>
    </fieldset>
  );
}
