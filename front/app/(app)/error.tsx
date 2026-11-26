"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Button } from "@/shared/ui/Button";

// 로그인한 화면에서 렌더 중 오류가 나면 헤더는 두고 이 화면을 보여 준다
export default function AppError({ error, retry }: { error: unknown; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-3 px-4 py-24 text-center">
      <h1 className="text-lg font-semibold">문제가 생겼어요.</h1>
      <p className="text-sm text-zinc-600">잠시 후 다시 시도해 주세요.</p>
      <div className="flex gap-2">
        <Button onClick={() => retry()}>다시 시도</Button>
        <Link href="/calendar" className="rounded-md px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-100">
          캘린더로
        </Link>
      </div>
    </main>
  );
}
