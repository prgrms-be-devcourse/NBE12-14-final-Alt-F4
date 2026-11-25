"use client";

import { useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import { popoverClass, popoverPanelClass, usePopover } from "./usePopover";

export type MenuItem = { label: string; onSelect: () => void; trailing?: string; type?: undefined } | { type: "separator" };

type Props = {
  label: ReactNode; // 버튼 안 내용
  ariaLabel?: string;
  items: MenuItem[];
  className?: string; // 버튼 모양
};

type Action = Extract<MenuItem, { onSelect: () => void }>;
const isAction = (item: MenuItem): item is Action => item.type !== "separator";

// 동작 메뉴 버튼 (값을 고르는 Dropdown과 다름, WAI-ARIA APG Menu Button)
// - 열면 첫 항목(↑로 열면 마지막 항목)에 포커스가 실제로 옮겨 간다
// - 항목을 고르면 메뉴를 닫고 버튼에 포커스를 돌려준 뒤 동작한다 (창이 닫히면 Modal이 버튼으로 포커스를 돌려준다)
export function MenuButton({ label, ariaLabel, items, className }: Props) {
  const id = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [open, setOpen] = useState(false);
  const [focusIndex, setFocusIndex] = useState(0);
  const actionIndexes = items.map((item, index) => (isAction(item) ? index : -1)).filter((index) => index >= 0);
  const popoverRef = usePopover({ open, anchorRef: buttonRef, onClose: () => setOpen(false) });

  function openMenu(at: "first" | "last") {
    setFocusIndex(at === "first" ? actionIndexes[0] : actionIndexes[actionIndexes.length - 1]);
    setOpen(true);
  }
  function closeMenu() {
    setOpen(false);
    buttonRef.current?.focus();
  }
  function run(item: Action) {
    closeMenu();
    item.onSelect();
  }

  // 열리거나 포커스 항목이 바뀌면 그 항목에 포커스 (DOM만 만짐)
  useLayoutEffect(() => {
    if (open) itemRefs.current[focusIndex]?.focus();
  }, [open, focusIndex]);

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const position = actionIndexes.indexOf(focusIndex);
    const go = (next: number) => setFocusIndex(actionIndexes[(next + actionIndexes.length) % actionIndexes.length]);
    const keys: Record<string, () => void> = {
      ArrowDown: () => go(position + 1),
      ArrowUp: () => go(position - 1),
      Home: () => go(0),
      End: () => go(actionIndexes.length - 1),
      Escape: () => closeMenu(),
    };
    if (event.key === "Tab") return setOpen(false);
    const handler = keys[event.key];
    if (!handler) return;
    event.preventDefault();
    event.stopPropagation();
    handler();
  }

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={`${id}-menu`}
        aria-label={ariaLabel}
        onClick={() => (open ? setOpen(false) : openMenu("first"))}
        onKeyDown={(event) => {
          if (open || (event.key !== "ArrowDown" && event.key !== "ArrowUp")) return;
          event.preventDefault();
          openMenu(event.key === "ArrowDown" ? "first" : "last");
        }}
        className={className}
      >
        {label}
      </button>
      {open && (
        <div ref={popoverRef} popover="manual" className={popoverClass} onMouseDown={(event) => event.preventDefault()}>
          <div id={`${id}-menu`} role="menu" aria-label={ariaLabel} onKeyDown={handleMenuKeyDown} className={cn(popoverPanelClass, "min-w-44")}>
            {items.map((item, index) =>
              isAction(item) ? (
                <button
                  key={item.label}
                  ref={(element) => {
                    itemRefs.current[index] = element;
                  }}
                  type="button"
                  role="menuitem"
                  tabIndex={-1}
                  onClick={() => run(item)}
                  onMouseEnter={() => setFocusIndex(index)}
                  className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left hover:bg-brand-soft focus:bg-brand-soft focus:outline-none"
                >
                  <span className="flex-1">{item.label}</span>
                  {item.trailing && <span className="text-xs text-zinc-400">{item.trailing}</span>}
                </button>
              ) : (
                <div key={`separator-${index}`} role="separator" className="mx-2 my-1 border-t border-line" />
              ),
            )}
          </div>
        </div>
      )}
    </>
  );
}
