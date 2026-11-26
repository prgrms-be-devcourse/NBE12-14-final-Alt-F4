"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { eventQueryKeys } from "@/features/event/hooks";
import {
  createCalendar,
  createCategory,
  createGroup,
  deleteCalendar,
  deleteCategory,
  deleteGroup,
  fetchCalendar,
  fetchGroups,
  leaveCalendar,
  moveCalendarGroup,
  renameGroup,
  updateCalendar,
  updateCategory,
} from "./api";

export const calendarQueryKeys = {
  groups: ["calendar", "groups"] as const,
  detail: (id: number) => ["calendar", "detail", id] as const,
};

export function useGroups() {
  return useQuery({ queryKey: calendarQueryKeys.groups, queryFn: fetchGroups });
}

// 주소의 id가 숫자가 아니면 요청하지 않는다
export function useCalendar(id: number) {
  return useQuery({ queryKey: calendarQueryKeys.detail(id), queryFn: () => fetchCalendar(id), enabled: Number.isInteger(id) && id > 0 });
}

// 바꾼 뒤 다시 조회할 것: 사이드바(그룹·캘린더·카테고리), 필요하면 캘린더 상세와 일정
function useCalendarMutation<TArgs, TResult>(
  mutationFn: (args: TArgs) => Promise<TResult>,
  options: { events?: boolean; detail?: boolean } = {},
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: calendarQueryKeys.groups });
      if (options.detail) await queryClient.invalidateQueries({ queryKey: ["calendar", "detail"] });
      if (options.events) await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all });
    },
  });
}

export const useCreateGroup = () => useCalendarMutation(createGroup);
export const useRenameGroup = () => useCalendarMutation(renameGroup);
export const useDeleteGroup = () => useCalendarMutation(deleteGroup);

export const useCreateCalendar = () => useCalendarMutation(createCalendar);
export const useUpdateCalendar = () => useCalendarMutation(updateCalendar, { detail: true });
export const useMoveCalendarGroup = () => useCalendarMutation(moveCalendarGroup, { detail: true });
export const useDeleteCalendar = () => useCalendarMutation(deleteCalendar, { events: true });
export const useLeaveCalendar = () => useCalendarMutation(leaveCalendar, { events: true });

export const useCreateCategory = () => useCalendarMutation(createCategory);
export const useUpdateCategory = () => useCalendarMutation(updateCategory);
// 지운 카테고리를 단 일정은 카테고리가 비워지므로 일정도 다시 조회한다
export const useDeleteCategory = () => useCalendarMutation(deleteCategory, { events: true });
