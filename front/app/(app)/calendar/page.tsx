import type { Metadata } from "next";
import { CalendarScreen } from "@/features/event/components/CalendarScreen";

export const metadata: Metadata = { title: "캘린더" };

// 로그인 확인 전에는 AuthGuard가 이 페이지 대신 로딩을 그린다 (instant 검사 제외)
export const instant = false;

export default function CalendarPage() {
  return <CalendarScreen />;
}
