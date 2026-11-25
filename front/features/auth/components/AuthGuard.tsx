"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { restoreSession, session } from "@/shared/api/session";
import { Button } from "@/shared/ui/Button";
import { Spinner } from "@/shared/ui/Spinner";
import { useSessionStatus } from "../hooks";

// 로그인이 필요한 화면을 감싼다
// 화면을 보여 줄지만 정한다. 실제 보호는 백엔드 401이다 (Next 서버는 Refresh 쿠키를 볼 수 없어 proxy.ts는 쓰지 않는다)
export function AuthGuard({ children }: { children: ReactNode }) {
  const status = useSessionStatus();
  const router = useRouter();

  useEffect(() => {
    if (status === "unknown") {
      void restoreSession();
    } else if (status === "anonymous") {
      // 직접 로그아웃했으면 돌아갈 주소를 남기지 않는다 (다음 사람이 이전 사람의 화면으로 가지 않게)
      if (session.wasSignedOutByUser()) {
        router.replace("/login");
        return;
      }
      // 주소는 이벤트 시점에 window에서 읽는다 (usePathname은 미리 렌더에 영향을 준다)
      const next = window.location.pathname + window.location.search;
      router.replace(`/login?next=${encodeURIComponent(next)}`);
    }
  }, [status, router]);

  if (status === "unreachable") return <UnreachableScreen />;
  if (status !== "authenticated") return <Spinner fullScreen />;
  return children;
}

export function UnreachableScreen() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-3 px-4 py-24 text-center">
      <p className="text-sm text-zinc-600">서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.</p>
      <Button onClick={() => session.retry()}>다시 시도</Button>
    </main>
  );
}
