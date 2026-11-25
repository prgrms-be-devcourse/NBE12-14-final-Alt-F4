"use client";

import { useQuery } from "@tanstack/react-query";
import { useSyncExternalStore } from "react";
import { session, type SessionStatus } from "@/shared/api/session";
import { fetchMe } from "./api";

export const authQueryKeys = {
  me: ["auth", "me"] as const,
};

// 로그인 상태. 서버 렌더·첫 렌더에서는 "unknown" (토큰은 브라우저에서만 안다)
export function useSessionStatus(): SessionStatus {
  return useSyncExternalStore(session.subscribe, session.getStatus, () => "unknown");
}

export function useMe() {
  const status = useSessionStatus();
  return useQuery({ queryKey: authQueryKeys.me, queryFn: fetchMe, enabled: status === "authenticated" });
}

// 로그인 뒤 돌아갈 주소: 같은 사이트 경로만 허용한다
// 글자로만 검사하면 "/\\evil.com"처럼 브라우저가 외부 주소로 읽는 값이 통과하므로 URL로 해석해 origin을 비교한다
const SAME_SITE = "http://same-site.invalid";

export function safeNext(next: string | null): string {
  if (!next) return "/calendar";
  try {
    const url = new URL(next, SAME_SITE);
    if (url.origin === SAME_SITE) return url.pathname + url.search + url.hash;
  } catch {
    // 해석할 수 없는 값은 버린다
  }
  return "/calendar";
}

// 로그인·가입 화면 사이를 오갈 때 돌아갈 주소를 이어 준다 (기본 /calendar면 붙이지 않는다)
export function nextQuery(next: string): string {
  return next === "/calendar" ? "" : `?next=${encodeURIComponent(next)}`;
}
