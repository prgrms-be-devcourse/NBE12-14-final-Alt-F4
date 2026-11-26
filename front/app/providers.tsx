"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { ApiError } from "@/shared/api/errors";
import { listenToOtherTabs, session } from "@/shared/api/session";

// 서버 데이터는 TanStack Query로 다룬다 (나중에 실시간 신호가 오면 invalidateQueries로 다시 조회)
export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            // 4xx(없음·권한 없음 등)는 다시 보내도 같으므로 한 번 더 보내지 않는다
            retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
          },
        },
      }),
  );

  // 로그인 상태가 끝나면(로그아웃·세션 만료·다른 탭의 계정 전환) 받아 둔 데이터를 모두 지운다
  // 다음 사람이 같은 브라우저로 로그인해도 이전 사람의 그룹·일정이 보이지 않게 한다. 토큰 갱신 때는 지우지 않는다
  useEffect(() => {
    let previous = session.getStatus();
    return session.subscribe(() => {
      const current = session.getStatus();
      if (previous === "authenticated" && current !== "authenticated") queryClient.clear();
      previous = current;
    });
  }, [queryClient]);

  useEffect(() => listenToOtherTabs(), []);

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
