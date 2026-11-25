import { api } from "@/shared/api/client";
import type { CalendarCreateRequest, CalendarDetail, CalendarGroup, CalendarUpdateRequest } from "./types";

// ===== 그룹 (내 개인 폴더) =====

// 내 그룹 → 캘린더 → 카테고리를 한 번에
export function fetchGroups(): Promise<CalendarGroup[]> {
  return api<CalendarGroup[]>("/api/v1/groups");
}

export function createGroup(name: string) {
  return api<{ id: number }>("/api/v1/groups", { method: "POST", body: { name } });
}

export function renameGroup({ id, name }: { id: number; name: string }) {
  return api<{ id: number }>(`/api/v1/groups/${id}`, { method: "PATCH", body: { name } });
}

// 캘린더가 들어 있는 그룹은 409로 막힌다
export function deleteGroup(id: number) {
  return api<void>(`/api/v1/groups/${id}`, { method: "DELETE" });
}

// ===== 캘린더 =====

export function createCalendar(request: CalendarCreateRequest) {
  return api<CalendarDetail>("/api/v1/calendars", { method: "POST", body: request });
}

export function fetchCalendar(id: number) {
  return api<CalendarDetail>(`/api/v1/calendars/${id}`);
}

// OWNER만
export function updateCalendar({ id, body }: { id: number; body: CalendarUpdateRequest }) {
  return api<CalendarDetail>(`/api/v1/calendars/${id}`, { method: "PATCH", body });
}

// 내 화면에서 넣어 둘 그룹만 바꾼다 (다른 사람에게는 영향 없음)
export function moveCalendarGroup({ id, groupId }: { id: number; groupId: number }) {
  return api<CalendarDetail>(`/api/v1/calendars/${id}/group`, { method: "PATCH", body: { groupId } });
}

// OWNER만. 일정·카테고리·다른 사람의 이용 관계까지 모두 지운다 (되돌릴 수 없음)
export function deleteCalendar(id: number) {
  return api<void>(`/api/v1/calendars/${id}`, { method: "DELETE" });
}

// OWNER가 아닌 사람이 이 캘린더를 내 목록에서 빼기 (구독 해지)
export function leaveCalendar(id: number) {
  return api<void>(`/api/v1/calendars/${id}/subscription`, { method: "DELETE" });
}

// ===== 카테고리 (OWNER·EDITOR) =====

export function createCategory(body: { calendarId: number; name: string; color: string | null }) {
  return api<{ id: number }>("/api/v1/categories", { method: "POST", body });
}

export function updateCategory({ id, name, color }: { id: number; name: string; color: string | null }) {
  return api<{ id: number }>(`/api/v1/categories/${id}`, { method: "PATCH", body: { name, color } });
}

// 이 카테고리를 단 일정은 남고 카테고리만 비워진다
export function deleteCategory(id: number) {
  return api<void>(`/api/v1/categories/${id}`, { method: "DELETE" });
}
