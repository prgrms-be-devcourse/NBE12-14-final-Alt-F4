// GET /api/v1/groups 응답: 내 그룹 → 그 그룹에 둔 내 캘린더 → 캘린더의 카테고리
export type CalendarRole = "OWNER" | "EDITOR" | "VIEWER" | "SUBSCRIBER";

export type CategorySummary = { id: number; name: string; color: string | null };

export type CalendarSummary = {
  id: number; // 캘린더 ID (일정 API의 calendarId)
  name: string;
  color: string | null; // 내 화면 색이 있으면 그것, 없으면 원본 색
  role: CalendarRole;
  categories: CategorySummary[];
};

export type CalendarGroup = {
  id: number;
  name: string;
  displayOrder: number | null;
  calendars: CalendarSummary[];
};

// GET·PATCH /api/v1/calendars/{id} 응답. color는 원본 색 (내 화면 색은 GET /groups)
export type CalendarDetail = {
  id: number;
  name: string;
  description: string | null;
  color: string | null;
  timezone: string;
  role: CalendarRole;
  groupId: number;
};

// PATCH /api/v1/calendars/{id}: 모든 칸을 보낸다 (description·color를 빼면 비워진다, timezone을 빼면 그대로)
export type CalendarUpdateRequest = { name: string; description: string | null; color: string | null; timezone?: string };

// POST /api/v1/calendars (이름만 필수)
// groupId를 빼면 내 첫 그룹(사이드바 맨 위)에 넣고, 그룹이 하나도 없으면 서버가 "내 캘린더" 그룹을 만든다
export type CalendarCreateRequest = {
  name: string;
  description?: string;
  color?: string;
  timezone?: string;
  groupId?: number;
};
