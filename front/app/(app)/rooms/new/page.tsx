import type { Metadata } from "next";
import { RoomCreateScreen } from "@/features/room/components/RoomCreateScreen";

export const metadata: Metadata = { title: "새 조율방 만들기" };

// 로그인 확인 전에는 AuthGuard가 이 페이지 대신 로딩을 그린다 (instant 검사 제외)
export const instant = false;

export default function Page() {
  return <RoomCreateScreen />;
}
