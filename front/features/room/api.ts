import { api } from "@/shared/api/client";
import type { RoomCreateRequest, RoomDetail, RoomSummary } from "./types";

// 내가 지금 참여 중인 방 (최근에 만든 방부터)
export function fetchRooms() {
  return api<RoomSummary[]>("/api/v1/rooms");
}

// 참여자만 볼 수 있다
export function fetchRoom(roomId: string) {
  return api<RoomDetail>(`/api/v1/rooms/${encodeURIComponent(roomId)}`);
}

// 만든 사람이 방장이 되고, 공통 캘린더가 함께 만들어진다
export function createRoom(body: RoomCreateRequest) {
  return api<RoomDetail>("/api/v1/rooms", { method: "POST", body });
}
