"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "./api";

// 로그아웃: 서버 쿠키를 지우고, 받아 둔 데이터를 비운 뒤 로그인 화면으로
export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);

  async function run() {
    if (pending) return;
    setPending(true);
    await logout().catch(() => undefined);
    // 로그아웃하면 Providers가 캐시를 비운다. 여기서도 한 번 더 비워 이동 중에 이전 데이터가 보이지 않게 한다
    queryClient.clear();
    router.replace("/login");
  }

  return { logout: run, pending };
}
