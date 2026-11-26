"use client";

import { useSyncExternalStore } from "react";

// 키보드 단축키 켜기·끄기 (WCAG 2.1.4: 한 글자 단축키는 끌 수 있어야 한다). 이 브라우저에 기억한다 (기본 켜짐)
const KEY = "live-calendar:shortcuts";
const listeners = new Set<() => void>();

function read(): boolean {
  try {
    return localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const handleStorage = (event: StorageEvent) => {
    if (event.key === KEY) listener();
  };
  window.addEventListener("storage", handleStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", handleStorage);
  };
}

export function useShortcutsEnabled(): boolean {
  return useSyncExternalStore(subscribe, read, () => true);
}

// 이벤트 핸들러에서만 부른다
export function setShortcutsEnabled(enabled: boolean) {
  try {
    if (enabled) localStorage.removeItem(KEY);
    else localStorage.setItem(KEY, "off");
  } catch {
    // 저장하지 못하면 이번 화면에서만 바뀐 것처럼 보이지 않는다 (다시 읽으면 켜짐)
  }
  listeners.forEach((listener) => listener());
}
