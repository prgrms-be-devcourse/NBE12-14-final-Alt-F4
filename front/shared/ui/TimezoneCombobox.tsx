"use client";

import { Check, X } from "lucide-react";
import { useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from "react";
import { cn } from "@/shared/lib/cn";
import { sameTimeZone } from "@/shared/lib/date";
import { allZoneEntries, matchesZone, zoneEntry, type ZoneEntry } from "@/shared/lib/timezones";
import { borderClass, ErrorText, errorId, inputClass } from "./fields";
import { TextField } from "./TextField";
import { popoverClass, popoverPanelClass, usePopover } from "./usePopover";

type Props = {
  label: string;
  value: string;
  onChange: (zone: string) => void;
  name?: string; // 주면 같은 값의 숨긴 입력을 그린다 (보이는 검색 칸에는 name이 없다)
  error?: string;
  autoFocusInput?: boolean; // 창의 처음 포커스 (data-autofocus)
};

// 시간대 검색 칸 (구글 캘린더처럼 한국어로 찾고, 항목은 두 줄)
// - 클릭·글자 입력·↓로만 열린다. 포커스만으로는 열지 않고 글자를 전체 선택한다
// - 목록이 열려 있으면 Enter를 막는다 (폼 안에서 저장되지 않게)
// - 한글 조합 중의 키는 아무것도 하지 않는다 (Modal의 Esc 처리와 어긋나지 않게)
export function TimezoneCombobox({ label, value, onChange, name, error, autoFocusInput }: Props) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [now] = useState(() => new Date());
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [entries, setEntries] = useState<ZoneEntry[] | null>(null);
  const [supported] = useState(() => typeof Intl.supportedValuesOf === "function");
  const popoverRef = usePopover({ open, anchorRef: inputRef, onClose: () => close() });

  const current = zoneEntry(value, now);
  // 지금 값이 목록에 없으면(고정 시차 등) 맨 앞에 더한다. 옛·새 이름은 같은 항목으로 본다
  const list = entries ? (entries.some((entry) => sameTimeZone(entry.id, value)) ? entries : [current, ...entries]) : [];
  const filtered = list.filter((entry) => matchesZone(entry, query));

  function openList(nextQuery = "") {
    const all = entries ?? allZoneEntries(now) ?? [];
    if (!entries) setEntries(all);
    const base = all.some((entry) => sameTimeZone(entry.id, value)) ? all : [current, ...all];
    const shown = base.filter((entry) => matchesZone(entry, nextQuery));
    const selectedIndex = nextQuery ? 0 : shown.findIndex((entry) => sameTimeZone(entry.id, value));
    setQuery(nextQuery);
    setActive(Math.max(selectedIndex, 0));
    setOpen(true);
  }
  function close() {
    setOpen(false);
    setQuery("");
  }
  function choose(index: number) {
    const entry = filtered[index];
    if (!entry) return;
    close();
    // 같은 시간대(옛·새 이름)를 다시 고르면 원래 문자열을 그대로 둔다
    if (!sameTimeZone(entry.id, value)) onChange(entry.id);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.nativeEvent.isComposing || event.keyCode === 229) return;
    if (!open) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        openList(query);
      }
      return;
    }
    if (event.key === "Tab") return close();
    const step = (delta: number) => setActive((previous) => Math.min(Math.max(previous + delta, 0), Math.max(filtered.length - 1, 0)));
    const keys: Record<string, () => void> = {
      ArrowDown: () => step(1),
      ArrowUp: () => step(-1),
      Home: () => setActive(0),
      End: () => setActive(Math.max(filtered.length - 1, 0)),
      Enter: () => choose(active),
      Escape: () => close(),
    };
    const handler = keys[event.key];
    if (!handler) return;
    event.preventDefault();
    if (event.key === "Escape") event.stopPropagation();
    handler();
  }

  useLayoutEffect(() => {
    if (!open) return;
    document.getElementById(`${id}-option-${active}`)?.scrollIntoView({ block: "nearest" });
  }, [open, active, id]);

  if (!supported) {
    return <TextField label={label} value={value} onChange={(event) => onChange(event.target.value)} maxLength={100} error={error} />;
  }

  const inputId = `${id}-input`;
  const listId = `${id}-list`;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-[13px] font-medium text-zinc-700">
        {label}
      </label>
      <div className="relative">
        <input
          ref={inputRef}
          id={inputId}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && filtered[active] ? `${id}-option-${active}` : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId(inputId) : undefined}
          autoComplete="off"
          spellCheck={false}
          value={open ? query : current.display}
          placeholder={current.display}
          onFocus={(event) => event.currentTarget.select()}
          onClick={() => !open && openList()}
          onChange={(event) => {
            if (!open) openList(event.target.value);
            else {
              setQuery(event.target.value);
              setActive(0);
            }
          }}
          onKeyDown={handleKeyDown}
          onBlur={() => open && close()}
          {...(autoFocusInput ? { "data-autofocus": true } : {})}
          className={cn(inputClass, borderClass(error), "w-full pr-9")}
        />
        <button
          type="button"
          aria-label="검색어 지우기"
          tabIndex={-1}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => {
            openList("");
            inputRef.current?.focus();
          }}
          className="absolute inset-y-0 right-0 flex items-center px-3 text-zinc-400 hover:text-zinc-700"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      </div>
      {name && <input type="hidden" name={name} value={value} />}
      <ErrorText id={errorId(inputId)} error={error} />
      {open && (
        <div ref={popoverRef} popover="manual" className={popoverClass} onMouseDown={(event) => event.preventDefault()}>
          <div id={listId} role="listbox" aria-label={label} className={cn(popoverPanelClass, "w-[min(28rem,calc(100vw-2rem))]")}>
            {filtered.length === 0 && <p className="px-3 py-2 text-zinc-500">찾는 시간대가 없어요.</p>}
            {filtered.map((entry, index) => {
              const selected = sameTimeZone(entry.id, value);
              return (
                <div
                  key={entry.id}
                  id={`${id}-option-${index}`}
                  role="option"
                  aria-selected={selected}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => choose(index)}
                  className={cn("flex cursor-pointer items-start gap-2 rounded-lg px-2.5 py-2", index === active && "bg-brand-soft")}
                >
                  <span aria-hidden className="flex h-5 w-4 shrink-0 items-center text-brand">
                    {selected && <Check className="h-4 w-4" />}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate">{entry.title}</span>
                    <span className="truncate text-xs text-zinc-500">{entry.description}</span>
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
