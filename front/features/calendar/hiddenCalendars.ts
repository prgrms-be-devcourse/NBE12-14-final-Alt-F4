"use client";

import { useSyncExternalStore } from "react";

// 사이드바에서 끈 캘린더를 이 브라우저에 사람별로 기억한다 (서버의 표시·숨김 API가 생기면 그쪽으로 옮긴다)
// localStorage는 막혀 있거나 비어 있을 수 있어서 읽기·쓰기를 모두 try로 감싸고, 실패하면 기억하지 않는 것으로 동작한다
const EMPTY: ReadonlySet<number> = new Set();
const listeners = new Set<() => void>();
let cache: { key: string; ids: ReadonlySet<number> } | null = null;

const keyOf = (userId: number) => `live-calendar:hidden:${userId}`;

function read(userId: number | undefined): ReadonlySet<number> {
  if (userId === undefined) return EMPTY;
  const key = keyOf(userId);
  if (cache?.key === key) return cache.ids;
  let ids: ReadonlySet<number> = EMPTY;
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(key) ?? "[]");
    if (Array.isArray(parsed)) ids = new Set(parsed.filter((value): value is number => Number.isInteger(value)));
  } catch {
    // 읽지 못하면 모두 보인다
  }
  cache = { key, ids };
  return ids;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  // 다른 탭에서 바꾼 값도 따라간다
  const handleStorage = (event: StorageEvent) => {
    if (event.key?.startsWith("live-calendar:hidden:")) {
      cache = null;
      listener();
    }
  };
  window.addEventListener("storage", handleStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", handleStorage);
  };
}

export function useHiddenCalendarIds(userId: number | undefined): ReadonlySet<number> {
  return useSyncExternalStore(subscribe, () => read(userId), () => EMPTY);
}

// 이벤트 핸들러에서만 부른다
export function toggleHiddenCalendar(userId: number, calendarId: number) {
  const next = new Set(read(userId));
  if (next.has(calendarId)) next.delete(calendarId);
  else next.add(calendarId);
  cache = { key: keyOf(userId), ids: next };
  try {
    localStorage.setItem(keyOf(userId), JSON.stringify([...next]));
  } catch {
    // 저장하지 못해도 이 화면에서는 바뀐 대로 보인다
  }
  listeners.forEach((listener) => listener());
}
