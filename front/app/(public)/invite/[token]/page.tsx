import type { Metadata } from "next";
import { ComingSoon } from "@/shared/ui/ComingSoon";

export const metadata: Metadata = { title: "초대 확인" };

// 조율 담당: GET /api/v1/invite-links/{token}, POST .../accept (캘린더 초대 링크와 같이 쓸지 정하기)
export default function Page() {
  return <ComingSoon title="초대 확인" description="초대 확인·수락 화면은 준비 중이에요." />;
}
