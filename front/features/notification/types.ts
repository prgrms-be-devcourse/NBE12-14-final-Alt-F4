export type NotificationType =
  | "EVENT_CREATED"
  | "EVENT_UPDATED"
  | "EVENT_CANCELLED"
  | "EVENT_DELETED"
  | "MEMBER_JOINED"
  | "ROOM_CREATED"
  | "ROOM_CONFIRMED"
  | "CALENDAR_DELETED"
  | "GROUP_REQUEST_RECEIVED"
  | "GROUP_REQUEST_APPROVED"
  | "GROUP_REQUEST_REJECTED";

export type NotificationItem = {
  id: number;
  type: NotificationType;
  calendarId: number | null;
  calendarName: string | null;
  eventId: number | null;
  eventTitle: string | null;
  params: Record<string, unknown> | null;
  isRead: boolean;
  createdAt: string;
};

export type NotificationPage = {
  content: NotificationItem[];
  number: number;
  totalPages: number;
  totalElements: number;
  last: boolean;
};

export type UnreadNotificationCount = {
  count: number;
};
