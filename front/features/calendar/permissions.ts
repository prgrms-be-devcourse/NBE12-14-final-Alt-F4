import type { CalendarRole } from "./types";

// 백엔드 권한과 같다: 일정·카테고리 편집은 OWNER·EDITOR, 캘린더 수정·삭제·공유는 OWNER
// 버튼을 숨기는 용도일 뿐, 실제 검사는 백엔드가 한다
export function canEditEvents(role: CalendarRole | string | undefined): boolean {
  return role === "OWNER" || role === "EDITOR";
}

export function isOwner(role: CalendarRole | string | undefined): boolean {
  return role === "OWNER";
}

// 내 목록에서 빼기(구독 해지)는 구독(SUBSCRIBER)만 된다 (백엔드 SubscribeService.cancel)
// 조율방 공통 캘린더(VIEWER)는 방에서 나가야 빠진다 (방 나가기 API는 아직 없음)
export function canLeave(role: CalendarRole | string | undefined): boolean {
  return role === "SUBSCRIBER";
}
