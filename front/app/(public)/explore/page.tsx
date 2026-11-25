import type { Metadata } from "next";
import { ComingSoon } from "@/shared/ui/ComingSoon";

export const metadata: Metadata = { title: "공개 캘린더 둘러보기" };

// 공유 담당: GET /api/v1/shares (탐색·검색), 익명 조회 허용 여부 확인 필요
export default function Page() {
  return <ComingSoon title="공개 캘린더 둘러보기" description="공유 탐색·검색 API가 준비되면 만들어요." />;
}
