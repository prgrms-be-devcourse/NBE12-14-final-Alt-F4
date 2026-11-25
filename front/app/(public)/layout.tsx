import { Suspense, type ReactNode } from "react";
import { Spinner } from "@/shared/ui/Spinner";
import { AppRail } from "../_components/AppRail";

// 로그인 없이도 볼 화면 묶음 (둘러보기, 공유 페이지, 초대 링크)
// 왼쪽 아이콘 줄은 로그인한 화면과 같다 (로그인돼 있으면 메뉴·내 메뉴, 아니면 로그인 버튼)
// 지금 백엔드는 이 API들도 로그인을 요구한다 → 담당자가 익명 GET을 열거나, 화면에서 "로그인 후 보기"로 안내
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<Spinner fullScreen />}>
      <div className="flex h-dvh">
        <AppRail />
        <div className="flex min-w-0 flex-1 flex-col overflow-y-auto">{children}</div>
      </div>
    </Suspense>
  );
}
