// POST /api/v1/shares: 내가 OWNER인 캘린더 여러 개를 묶은 공유 페이지
// 공유 페이지를 보는 API(탐색·검색·상세)와 구독 등록은 아직 없다 (공유 담당)
export type ShareCreateRequest = {
  title: string; // 255자 이하
  content: string | null;
  calendarIds: number[]; // 1개 이상, 모두 내가 OWNER
  isPublic: boolean;
};

export type ShareCreateResponse = { shareId: number };
