import type { Metadata } from "next";
import { RoomDetailScreen } from "@/features/room/components/RoomDetailScreen";

export const metadata: Metadata = { title: "조율방" };

// 로그인 확인 전에는 AuthGuard가 이 페이지 대신 로딩을 그린다 (instant 검사 제외)
export const instant = false;

export default function Page() {
  return <RoomDetailScreen />;
}
