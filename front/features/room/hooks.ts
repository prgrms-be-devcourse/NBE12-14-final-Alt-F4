"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { calendarQueryKeys } from "@/features/calendar/hooks";
import { createRoom, fetchRoom, fetchRooms } from "./api";

export const roomQueryKeys = {
  list: ["room", "list"] as const,
  detail: (roomId: string) => ["room", "detail", roomId] as const,
};

export function useRooms() {
  return useQuery({ queryKey: roomQueryKeys.list, queryFn: fetchRooms });
}

export function useRoom(roomId: string) {
  return useQuery({ queryKey: roomQueryKeys.detail(roomId), queryFn: () => fetchRoom(roomId) });
}

// 방을 만들면 공통 캘린더가 내 그룹에 생기므로 사이드바도 다시 조회한다
// 받은 응답은 상세 캐시에 넣어 두어 이동한 뒤 다시 요청하지 않는다
export function useCreateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createRoom,
    onSuccess: async (room) => {
      queryClient.setQueryData(roomQueryKeys.detail(room.id), room);
      await queryClient.invalidateQueries({ queryKey: roomQueryKeys.list });
      await queryClient.invalidateQueries({ queryKey: calendarQueryKeys.groups });
    },
  });
}
