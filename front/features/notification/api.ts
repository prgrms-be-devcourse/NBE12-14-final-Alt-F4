import { API_URL } from "@/shared/config/env";
import { api } from "@/shared/api/client";
import { refreshSession, session } from "@/shared/api/session";
import type { NotificationItem, NotificationPage, UnreadNotificationCount } from "./types";

const PAGE_SIZE = 20;

export function fetchNotifications(page: number) {
  return api<NotificationPage>("/api/v1/notifications", { query: { page, size: PAGE_SIZE } });
}

export function fetchUnreadNotificationCount() {
  return api<UnreadNotificationCount>("/api/v1/notifications/unread-count");
}

export function markNotificationRead(id: number) {
  return api<void>(`/api/v1/notifications/${id}`, { method: "PATCH" });
}

export function markAllNotificationsRead() {
  return api<void>("/api/v1/notifications", { method: "PATCH" });
}

export async function connectNotificationStream(
  signal: AbortSignal,
  onNotification: (notification: NotificationItem) => void,
): Promise<void> {
  let response = await requestNotificationStream(signal);
  if (response.status === 401) {
    if (!(await refreshSession()) || signal.aborted) return;
    response = await requestNotificationStream(signal);
  }
  if (signal.aborted) return;
  if (!response.ok) throw new Error(`알림 스트림 연결 실패 (${response.status})`);
  if (!response.body) throw new Error("알림 스트림 응답을 읽을 수 없습니다.");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() ?? "";
      for (const frame of frames) {
        const notification = parseNotificationFrame(frame);
        if (notification) onNotification(notification);
      }
      if (done) return;
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
}

async function requestNotificationStream(signal: AbortSignal): Promise<Response> {
  const token = session.getAccessToken();
  if (!token) {
    if (!(await refreshSession()) || signal.aborted) return new Response(null, { status: 401 });
    return requestNotificationStream(signal);
  }
  return fetch(`${API_URL}/api/v1/notifications/stream`, {
    headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}` },
    credentials: "include",
    signal,
  });
}

function parseNotificationFrame(frame: string): NotificationItem | null {
  let eventName = "message";
  const data: string[] = [];
  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(":")) continue;
    const separator = line.indexOf(":");
    const field = separator < 0 ? line : line.slice(0, separator);
    const value = separator < 0 ? "" : line.slice(separator + 1).replace(/^ /, "");
    if (field === "event") eventName = value;
    if (field === "data") data.push(value);
  }
  if (eventName !== "notification" || data.length === 0) return null;
  try {
    return JSON.parse(data.join("\n")) as NotificationItem;
  } catch {
    return null;
  }
}
