"use client";

import { CheckCheck, X } from "lucide-react";
import { useEffect, useId, useRef } from "react";
import { errorMessage } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { IconButton } from "@/shared/ui/IconButton";
import { Spinner } from "@/shared/ui/Spinner";
import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications } from "../hooks";
import type { NotificationType } from "../types";

const notificationLabels: Record<NotificationType, string> = {
  EVENT_CREATED: "새 일정",
  EVENT_UPDATED: "일정 변경",
  EVENT_CANCELLED: "일정 취소",
  EVENT_DELETED: "일정 삭제",
  MEMBER_JOINED: "새 구성원",
  ROOM_CREATED: "조율방 생성",
  ROOM_CONFIRMED: "일정 확정",
  CALENDAR_DELETED: "캘린더 삭제",
  GROUP_REQUEST_RECEIVED: "그룹 요청",
  GROUP_REQUEST_APPROVED: "그룹 요청 승인",
  GROUP_REQUEST_REJECTED: "그룹 요청 거절",
};

type Props = {
  unreadCount: number;
  onClose: () => void;
};

export function NotificationDrawer({ unreadCount, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const pressedOutside = useRef(false);
  const titleId = useId();
  const query = useNotifications(true);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const root = document.documentElement;
    const previousOverflow = root.style.overflow;

    dialog.showModal();
    dialog.querySelector<HTMLElement>("[data-autofocus]")?.focus();
    root.style.overflow = "hidden";

    return () => {
      if (dialog.open) dialog.close();
      root.style.overflow = previousOverflow;
      window.setTimeout(() => {
        if (previousFocus?.isConnected) previousFocus.focus();
      }, 0);
    };
  }, []);

  const items = query.data?.pages.flatMap((page) => page.content) ?? [];
  const actionError = markRead.error ?? markAllRead.error;

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onMouseDown={(event) => {
        pressedOutside.current = event.target === event.currentTarget;
      }}
      onClick={(event) => {
        if (pressedOutside.current && event.target === event.currentTarget) onClose();
        pressedOutside.current = false;
      }}
      className="fixed inset-y-0 left-0 m-0 flex h-dvh max-h-dvh w-[min(400px,100vw)] max-w-none flex-col border-0 bg-white p-0 text-foreground shadow-dialog backdrop:bg-zinc-900/30"
    >
      <header className="flex shrink-0 items-center justify-between gap-3 border-b border-line px-5 py-4">
        <div className="min-w-0">
          <h2 id={titleId} className="text-lg font-semibold">알림</h2>
          <p className="mt-0.5 text-xs text-muted">읽지 않은 알림 {unreadCount}개</p>
        </div>
        <div className="flex items-center gap-1">
          <IconButton
            label="모두 읽음"
            size="sm"
            disabled={unreadCount === 0 || markAllRead.isPending}
            onClick={() => markAllRead.mutate()}
          >
            <CheckCheck className="h-4 w-4" aria-hidden />
          </IconButton>
          <IconButton label="알림 창 닫기" size="sm" onClick={onClose} data-autofocus>
            <X className="h-[18px] w-[18px]" aria-hidden />
          </IconButton>
        </div>
      </header>

      {actionError && <p role="alert" className="border-b border-red-200 bg-red-50 px-5 py-2 text-sm text-red-700">{errorMessage(actionError)}</p>}

      <div className="min-h-0 flex-1 overflow-y-auto">
        {query.isPending ? (
          <div className="flex justify-center py-10"><Spinner /></div>
        ) : query.isError ? (
          <div role="alert" className="flex flex-col items-center gap-3 px-5 py-10 text-center text-sm text-zinc-600">
            <p>{errorMessage(query.error)}</p>
            <Button variant="secondary" size="sm" onClick={() => void query.refetch()}>다시 불러오기</Button>
          </div>
        ) : items.length === 0 ? (
          <p className="px-5 py-12 text-center text-sm text-muted">새 알림이 없습니다.</p>
        ) : (
          <ul>
            {items.map((item) => (
              <li key={item.id} className="border-b border-line">
                <button
                  type="button"
                  onClick={() => markRead.mutate(item.id)}
                  className={`flex w-full gap-3 px-5 py-4 text-left outline-none transition-colors hover:bg-zinc-50 focus-visible:bg-brand-soft ${item.isRead ? "" : "bg-brand-soft/50"}`}
                >
                  <span aria-hidden className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${item.isRead ? "bg-transparent" : "bg-red-600"}`} />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-start justify-between gap-3">
                      <span className="text-sm font-semibold">{notificationLabels[item.type]}</span>
                      <time className="shrink-0 text-[11px] text-muted">{formatNotificationTime(item.createdAt)}</time>
                    </span>
                    <span className="mt-1 block truncate text-sm text-zinc-700">{item.eventTitle ?? "알림이 도착했어요."}</span>
                    {item.calendarName && <span className="mt-1 block truncate text-xs text-muted">{item.calendarName}</span>}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {query.hasNextPage && (
          <div className="flex justify-center px-5 py-4">
            <Button variant="secondary" size="sm" disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
              {query.isFetchingNextPage ? "불러오는 중…" : "더 불러오기"}
            </Button>
          </div>
        )}
      </div>
    </dialog>
  );
}

function formatNotificationTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("ko-KR", { month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" }).format(date);
}
