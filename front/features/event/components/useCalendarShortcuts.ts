"use client";

import { useEffect, useEffectEvent } from "react";

export type ShortcutAction = "day" | "week" | "month" | "year" | "agenda" | "today" | "next" | "previous" | "create";

// 키 위치(event.code)로 비교한다: 한글 입력 상태에서 W 자리를 누르면 key는 "ㅈ"이지만 code는 "KeyW"다
const ACTIONS: Record<string, ShortcutAction> = {
  KeyD: "day",
  KeyW: "week",
  KeyM: "month",
  KeyY: "year",
  KeyA: "agenda",
  KeyT: "today",
  KeyJ: "next",
  KeyN: "next",
  KeyK: "previous",
  KeyP: "previous",
  KeyC: "create",
};

const TEXT_INPUT_TYPES = new Set(["text", "search", "email", "url", "tel", "password", "number", "date", "time", "datetime-local", "month", "week"]);

// 글자를 입력하는 칸에 포커스가 있는지 (체크박스·버튼·라디오는 아니다)
function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable || target instanceof HTMLTextAreaElement) return true;
  if (target instanceof HTMLInputElement) return TEXT_INPUT_TYPES.has(target.type) || target.getAttribute("role") === "combobox";
  return false;
}

// 캘린더 화면의 한 글자 단축키 (D·W·M·Y·A 보기, T 오늘, J·N 다음, K·P 이전, C 새 일정)
// 동작하지 않을 때: 꺼짐, 한글 조합 중, 꾹 누름, Ctrl·Meta·Alt, 글자 입력 칸, 열린 창, 열린 목록
export function useCalendarShortcuts(enabled: boolean, onAction: (action: ShortcutAction) => void) {
  const handleKeyDown = useEffectEvent((event: KeyboardEvent) => {
    if (!enabled || event.isComposing || event.keyCode === 229 || event.repeat) return;
    if (event.ctrlKey || event.metaKey || event.altKey) return;
    const action = ACTIONS[event.code];
    if (!action || isTyping(event.target)) return;
    if (document.querySelector("dialog[open]") || document.querySelector(":popover-open") || document.querySelector('[aria-expanded="true"]')) return;
    event.preventDefault();
    onAction(action);
  });

  useEffect(() => {
    const listener = (event: KeyboardEvent) => handleKeyDown(event);
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, []);
}
