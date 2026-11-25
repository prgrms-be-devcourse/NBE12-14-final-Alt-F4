"use client";

import { Check, ChevronDown } from "lucide-react";
import { useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import { borderClass, ErrorText, errorId, inputClass } from "./fields";
import { popoverClass, popoverPanelClass, usePopover } from "./usePopover";

export type DropdownOption<T extends string | number> =
  | { value: T; label: string; description?: string; disabled?: boolean; leading?: ReactNode; trailing?: string; type?: undefined }
  | { type: "separator" };

type Props<T extends string | number> = {
  label: string;
  hideLabel?: boolean; // 칸 위 라벨을 숨기고 화면 읽기용으로만 둔다
  options: DropdownOption<T>[];
  value: T;
  onChange: (value: T) => void;
  name?: string; // 주면 같은 값의 숨긴 입력을 그려 FormData로 읽힌다
  disabled?: boolean;
  error?: string;
  className?: string; // 바깥 div (폭 등)
  variant?: "field" | "pill"; // field: 폼의 회색 칸, pill: 툴바의 테두리 알약
};

type Choice<T extends string | number> = Extract<DropdownOption<T>, { value: T }>;
const isChoice = <T extends string | number>(option: DropdownOption<T>): option is Choice<T> => option.type !== "separator";

// 값을 고르는 칸 (네이티브 select 대신, 우리 디자인 목록)
// - 열고 닫기는 onClick 하나 (Enter·Space는 버튼의 네이티브 click으로 온다)
// - 포커스는 버튼에 두고 목록의 활성 항목은 aria-activedescendant로 알린다 (select-only combobox 패턴)
// - 열린 채 Esc는 목록만 닫는다 (keydown을 막아 바깥 <dialog>의 cancel이 생기지 않게)
export function Dropdown<T extends string | number>({ label, hideLabel, options, value, onChange, name, disabled, error, className, variant = "field" }: Props<T>) {
  const id = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const choices = options.map((option, index) => ({ option, index })).filter((item): item is { option: Choice<T>; index: number } => isChoice(item.option));
  const selected = choices.find((item) => item.option.value === value);
  const popoverRef = usePopover({ open, anchorRef: buttonRef, onClose: () => setOpen(false) });

  const enabledIndexes = choices.filter((item) => !item.option.disabled).map((item) => item.index);
  function openList() {
    setActive(selected && !selected.option.disabled ? selected.index : (enabledIndexes[0] ?? -1));
    setOpen(true);
  }
  function choose(index: number) {
    const option = options[index];
    if (!option || !isChoice(option) || option.disabled) return;
    setOpen(false);
    onChange(option.value);
  }
  function move(step: 1 | -1 | "first" | "last") {
    if (enabledIndexes.length === 0) return;
    if (step === "first") return setActive(enabledIndexes[0]);
    if (step === "last") return setActive(enabledIndexes[enabledIndexes.length - 1]);
    const position = enabledIndexes.indexOf(active);
    const next = position === -1 ? 0 : Math.min(Math.max(position + step, 0), enabledIndexes.length - 1);
    setActive(enabledIndexes[next]);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (!open) {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        openList();
      }
      return;
    }
    const keys: Record<string, () => void> = {
      ArrowDown: () => move(1),
      ArrowUp: () => move(-1),
      Home: () => move("first"),
      End: () => move("last"),
      Enter: () => choose(active),
      " ": () => choose(active),
      Escape: () => setOpen(false),
    };
    if (event.key === "Tab") return setOpen(false);
    const handler = keys[event.key];
    if (!handler) return;
    event.preventDefault();
    if (event.key === "Escape") event.stopPropagation();
    handler();
  }

  // 활성 항목이 목록 밖이면 보이게 스크롤한다 (DOM만 만짐)
  useLayoutEffect(() => {
    if (!open || active < 0) return;
    document.getElementById(`${id}-option-${active}`)?.scrollIntoView({ block: "nearest" });
  }, [open, active, id]);

  const buttonId = `${id}-button`;
  const labelId = `${id}-label`;
  const listId = `${id}-list`;

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <span id={labelId} className={hideLabel ? "sr-only" : "text-[13px] font-medium text-zinc-700"}>
        {label}
      </span>
      <button
        ref={buttonRef}
        id={buttonId}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-labelledby={`${labelId} ${buttonId}`}
        aria-activedescendant={open && active >= 0 ? `${id}-option-${active}` : undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId(buttonId) : undefined}
        disabled={disabled}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={handleKeyDown}
        className={cn(
          variant === "pill"
            ? "flex h-9 w-full items-center gap-2 rounded-full border border-line-strong bg-white pl-4 pr-3 text-[13px] font-semibold outline-none hover:bg-zinc-50 focus-visible:ring-[3px] focus-visible:ring-brand/30"
            : cn(inputClass, borderClass(error), "flex w-full items-center gap-2 text-left"),
          "disabled:cursor-not-allowed",
        )}
      >
        {selected?.option.leading}
        <span className={cn("min-w-0 flex-1 truncate", !selected && "text-zinc-400")}>{selected ? selected.option.label : "선택해 주세요"}</span>
        <ChevronDown aria-hidden className={cn("h-4 w-4 shrink-0 text-muted transition-transform", open && "rotate-180")} />
      </button>
      {name && <input type="hidden" name={name} value={String(value)} />}
      <ErrorText id={errorId(buttonId)} error={error} />
      {open && (
        <div ref={popoverRef} popover="manual" className={popoverClass} onMouseDown={(event) => event.preventDefault()}>
          <div id={listId} role="listbox" aria-labelledby={labelId} className={cn(popoverPanelClass, "w-max min-w-full max-w-[min(24rem,calc(100vw-1rem))]")}>
            {options.map((option, index) =>
              isChoice(option) ? (
                <div
                  key={`${String(option.value)}-${index}`}
                  id={`${id}-option-${index}`}
                  role="option"
                  aria-selected={option.value === value}
                  aria-disabled={option.disabled || undefined}
                  onMouseEnter={() => !option.disabled && setActive(index)}
                  onClick={() => choose(index)}
                  className={cn(
                    "flex cursor-pointer items-start gap-2 rounded-lg px-2.5 py-2",
                    index === active && "bg-brand-soft",
                    option.disabled && "cursor-not-allowed text-zinc-400",
                  )}
                >
                  <span aria-hidden className="flex h-5 w-4 shrink-0 items-center text-brand">
                    {option.value === value && <Check className="h-4 w-4" />}
                  </span>
                  {option.leading && <span className="flex h-5 shrink-0 items-center">{option.leading}</span>}
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate">{option.label}</span>
                    {option.description && <span className="truncate text-xs text-zinc-500">{option.description}</span>}
                  </span>
                  {option.trailing && <span className="shrink-0 text-xs text-zinc-400">{option.trailing}</span>}
                </div>
              ) : (
                <div key={`separator-${index}`} role="separator" className="mx-2 my-1 border-t border-line" />
              ),
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// 캘린더·카테고리 색 점 (Dropdown의 leading)
export function ColorDot({ color }: { color: string }) {
  return <span aria-hidden className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} />;
}
