"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { restoreSession } from "@/shared/api/session";
import { buttonClass } from "@/shared/ui/Button";
import { Spinner } from "@/shared/ui/Spinner";
import { useSessionStatus } from "../hooks";

// 첫 화면: 이미 로그인돼 있으면(쿠키로 복구되면) 캘린더로 보낸다
export function LandingScreen() {
  const status = useSessionStatus();
  const router = useRouter();

  useEffect(() => {
    if (status === "unknown") void restoreSession();
    else if (status === "authenticated") router.replace("/calendar");
  }, [status, router]);

  // 로그인돼 있는지 아는 동안은 로그인 버튼을 잠깐 보였다 숨기지 않게 로딩만 보인다
  if (status === "unknown" || status === "authenticated") return <Spinner fullScreen />;

  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 px-4 py-24 text-center">
      <h1 className="text-[32px] font-bold leading-tight tracking-[-0.02em]">
        흩어진 일정을 한 캘린더에,
        <br />
        바뀌면 바로 보이게
      </h1>
      <p className="text-muted">내 캘린더 관리 · 함께 쓰는 캘린더 · 공개 캘린더 구독 · 일정 조율</p>
      <div className="flex gap-3">
        <Link href="/login" className={buttonClass()}>
          로그인
        </Link>
        <Link href="/signup" className={buttonClass("secondary")}>
          회원가입
        </Link>
      </div>
    </main>
  );
}
