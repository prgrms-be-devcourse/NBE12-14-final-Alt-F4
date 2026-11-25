import type { Metadata } from "next";
import { ComingSoon } from "@/shared/ui/ComingSoon";

export const metadata: Metadata = { title: "공유 캘린더" };

// 공유 담당: GET /api/v1/shares/{shareId}, 구독 POST /api/v1/calendars/{calendarId}/subscription
export default function Page() {
  return <ComingSoon title="공유 캘린더" description="공유 상세·구독 화면은 준비 중이에요." />;
}
