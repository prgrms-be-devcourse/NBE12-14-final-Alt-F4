"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { eventQueryKeys } from "@/features/event/hooks";
import {
  changeCalendarDisplay,
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
import type { CalendarGroup } from "./types";

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

const displayMutationKey = ["calendar", "display"] as const;

// 사이드바 체크박스: 누르자마자 목록에 반영하고(끈 캘린더의 일정은 화면에서 바로 빠진다), 실패하면 되돌린다
// 끝나면 목록과 일정을 다시 불러온다 (서버가 끈 캘린더의 일정을 빼고 주므로, 다시 켜면 일정을 새로 받아야 한다)
// 여러 번 빠르게 누르면 마지막 요청이 끝났을 때만 다시 불러온다 (중간 응답으로 체크가 깜빡이지 않게)
export function useChangeCalendarDisplay() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: displayMutationKey,
    mutationFn: changeCalendarDisplay,
    onMutate: async ({ id, isVisible }) => {
      await queryClient.cancelQueries({ queryKey: calendarQueryKeys.groups });
      const previous = queryClient.getQueryData<CalendarGroup[]>(calendarQueryKeys.groups);
      queryClient.setQueryData<CalendarGroup[]>(calendarQueryKeys.groups, (groups) =>
        groups?.map((group) => ({
          ...group,
          calendars: group.calendars.map((calendar) => (calendar.id === id ? { ...calendar, isVisible } : calendar)),
        })),
      );
      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(calendarQueryKeys.groups, context.previous);
    },
    onSettled: async () => {
      if (queryClient.isMutating({ mutationKey: displayMutationKey }) > 1) return;
      await queryClient.invalidateQueries({ queryKey: calendarQueryKeys.groups });
      await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all });
    },
  });
}

export const useCreateCategory = () => useCalendarMutation(createCategory);
export const useUpdateCategory = () => useCalendarMutation(updateCategory);
// 지운 카테고리를 단 일정은 카테고리가 비워지므로 일정도 다시 조회한다
export const useDeleteCategory = () => useCalendarMutation(deleteCategory, { events: true });
