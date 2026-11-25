import type { Metadata } from "next";
import { ComingSoon } from "@/shared/ui/ComingSoon";

export const metadata: Metadata = { title: "내 설정" };

// 로그인 확인 전에는 AuthGuard가 이 페이지 대신 로딩을 그린다 (instant 검사 제외)
export const instant = false;

// 회원 담당: PATCH/DELETE /api/v1/users, 숨긴 일정 조회 API 필요
export default function Page() {
  return <ComingSoon title="내 설정" description="내 정보 수정·탈퇴·숨긴 일정 화면은 준비 중이에요." />;
}
