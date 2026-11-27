"use client";

import { Bell } from "lucide-react";
import { useState } from "react";
import { IconButton } from "@/shared/ui/IconButton";
import { NotificationDrawer } from "./NotificationDrawer";
import { useNotificationStream, useUnreadNotificationCount } from "../hooks";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const unread = useUnreadNotificationCount(true);
  const count = unread.data?.count ?? 0;

  useNotificationStream(true);

  return (
    <>
      <div className="relative">
        <IconButton
          label={count > 0 ? `알림, 읽지 않은 알림 ${count}개` : "알림"}
          onClick={() => setOpen(true)}
          aria-expanded={open}
          className="text-zinc-500 hover:bg-brand-soft hover:text-brand-ink"
        >
          <Bell className="h-5 w-5" aria-hidden />
        </IconButton>
        {count > 0 && (
          <span
            aria-hidden
            className="pointer-events-none absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full border-2 border-white bg-red-600 px-1 text-[9px] leading-none font-bold text-white"
          >
            {count > 99 ? "99+" : count}
          </span>
        )}
      </div>
      {open && <NotificationDrawer unreadCount={count} onClose={() => setOpen(false)} />}
    </>
  );
}
