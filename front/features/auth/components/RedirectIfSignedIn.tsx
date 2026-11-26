"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";
import { restoreSession } from "@/shared/api/session";
import { safeNext, useSessionStatus } from "../hooks";

// 로그인·가입 화면: 이미 로그인돼 있으면(쿠키로 복구되면) 돌아갈 주소로 보낸다. 화면은 그대로 그린다
export function RedirectIfSignedIn() {
  const status = useSessionStatus();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));

  useEffect(() => {
    if (status === "unknown") void restoreSession();
    else if (status === "authenticated") router.replace(next);
  }, [status, router, next]);

  return null;
}
