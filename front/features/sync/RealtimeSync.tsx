"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { startRealtimeSync } from "./connection";

// 로그인한 화면(AuthGuard 안)에 하나만 둔다. 로그아웃하면 AuthGuard가 이 컴포넌트를 내려서 연결도 끊긴다
export function RealtimeSync() {
  const queryClient = useQueryClient();
  useEffect(() => startRealtimeSync(queryClient), [queryClient]);
  return null;
}
