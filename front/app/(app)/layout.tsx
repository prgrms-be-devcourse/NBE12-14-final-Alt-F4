import { Suspense, type ReactNode } from "react";
import { AuthGuard } from "@/features/auth/components/AuthGuard";
import { RealtimeSync } from "@/features/sync/RealtimeSync";
import { Spinner } from "@/shared/ui/Spinner";
import { AppRail } from "../_components/AppRail";

// 로그인이 필요한 화면 묶음: 왼쪽 아이콘 줄 + 화면 (화면 쪽만 스크롤)
// Suspense: 안에서 usePathname·useParams·useSearchParams를 쓰므로 없으면 next build가 실패한다 (cacheComponents)
// 로그인 확인 전에는 페이지 대신 로딩을 그린다 → 이 아래 page.tsx마다 `export const instant = false` (README 참고)
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<Spinner fullScreen />}>
      <AuthGuard>
        <RealtimeSync />
        <div className="flex h-dvh">
          <AppRail />
          <div className="flex min-w-0 flex-1 flex-col overflow-y-auto">{children}</div>
        </div>
      </AuthGuard>
    </Suspense>
  );
}
