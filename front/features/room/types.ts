// 조율방. id는 숫자가 아니라 방 코드(문자열, Sqids)
// 조율 범위: startDate~endDate(마지막 날 포함)의 매일 dailyStartTime~dailyEndTime (timezone 기준 현지 값)
// 끝 시간이 시작보다 이르거나 같으면 다음 날로 넘어간다 (22:00~02:00, 00:00~00:00 = 24시간)
export type RoomSummary = {
  id: string;
  name: string;
  startDate: string; // yyyy-MM-dd
  endDate: string;
  dailyStartTime: string; // HH:mm(:ss)
  dailyEndTime: string;
  timezone: string;
  isOwner: boolean;
};

export type RoomMember = {
  tag: string; // 회원 태그. 화면에는 "닉네임#태그"
  nickname: string;
  isOwner: boolean;
  joinedAt: string;
};

export type RoomDetail = RoomSummary & {
  calendarId: number; // 확정 일정이 들어가는 공통 캘린더 (내 사이드바에도 보인다)
  ownerTag: string;
  members: RoomMember[];
  createdAt: string;
};

export type RoomCreateRequest = {
  name: string;
  startDate: string;
  endDate: string;
  dailyStartTime: string; // "09:00"
  dailyEndTime: string;
  timezone: string;
  groupId: number; // 공통 캘린더를 넣을 내 그룹
};
