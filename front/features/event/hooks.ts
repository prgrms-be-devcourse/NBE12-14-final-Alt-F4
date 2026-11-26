"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { hasCode } from "@/shared/api/errors";
import { browserTimeZone } from "@/shared/lib/date";
import { createEvent, deleteEvent, fetchEvent, fetchEvents, updateEvent } from "./api";

export const eventQueryKeys = {
  all: ["event"] as const,
  range: (from: string, to: string) => ["event", "range", from, to] as const,
  detail: (id: number) => ["event", "detail", id] as const,
};

// 화면에 보이는 기간의 일정 (반복 일정은 서버가 회차로 펼쳐 준다)
export function useEventsInRange(from: Date, to: Date) {
  const fromIso = from.toISOString();
  const toIso = to.toISOString();
  return useQuery({
    queryKey: eventQueryKeys.range(fromIso, toIso),
    queryFn: () => fetchEvents({ from: fromIso, to: toIso, timezone: browserTimeZone() }),
    // 달을 바꾸는 동안 이전 화면을 그대로 둔다 (빈 격자로 깜빡이지 않게)
    placeholderData: keepPreviousData,
  });
}

export function useEvent(id: number, enabled: boolean) {
  return useQuery({ queryKey: eventQueryKeys.detail(id), queryFn: () => fetchEvent(id), enabled });
}

// 일정이 바뀌면 보이는 기간의 목록을 모두 다시 조회한다 (반복·이후 수정은 여러 날이 바뀐다)
function useInvalidateEvents() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: eventQueryKeys.all });
}

export function useCreateEvent() {
  const invalidate = useInvalidateEvents();
  return useMutation({ mutationFn: createEvent, onSuccess: invalidate });
}

// 다른 사람이 먼저 지운 일정·회차: 목록을 다시 불러와 화면에서도 없앤다
export function isGoneError(error: unknown): boolean {
  return hasCode(error, "EVENT_NOT_FOUND") || hasCode(error, "OCCURRENCE_NOT_FOUND");
}

export function useUpdateEvent() {
  const invalidate = useInvalidateEvents();
  return useMutation({
    mutationFn: updateEvent,
    onSuccess: invalidate,
    onError: (error) => {
      if (isGoneError(error)) void invalidate();
    },
  });
}

export function useDeleteEvent() {
  const invalidate = useInvalidateEvents();
  return useMutation({
    mutationFn: deleteEvent,
    onSuccess: invalidate,
    onError: (error) => {
      if (isGoneError(error)) void invalidate();
    },
  });
}
