"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import {
  connectNotificationStream,
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "./api";

export const notificationQueryKeys = {
  all: ["notification"] as const,
  list: ["notification", "list"] as const,
  unreadCount: ["notification", "unread-count"] as const,
};

export function useUnreadNotificationCount(enabled: boolean) {
  return useQuery({
    queryKey: notificationQueryKeys.unreadCount,
    queryFn: fetchUnreadNotificationCount,
    enabled,
    refetchInterval: 60_000,
  });
}

export function useNotifications(enabled: boolean) {
  return useInfiniteQuery({
    queryKey: notificationQueryKeys.list,
    queryFn: ({ pageParam }) => fetchNotifications(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    enabled,
  });
}

function useInvalidateNotifications() {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: notificationQueryKeys.list }),
      queryClient.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount }),
    ]);
  };
}

export function useMarkNotificationRead() {
  const invalidate = useInvalidateNotifications();
  return useMutation({ mutationFn: markNotificationRead, onSuccess: invalidate });
}

export function useMarkAllNotificationsRead() {
  const invalidate = useInvalidateNotifications();
  return useMutation({ mutationFn: markAllNotificationsRead, onSuccess: invalidate });
}

export function useNotificationStream(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let stopped = false;

    async function run() {
      let retryDelay = 1000;
      while (!stopped && !controller.signal.aborted) {
        try {
          await connectNotificationStream(controller.signal, () => {
            void Promise.all([
              queryClient.invalidateQueries({ queryKey: notificationQueryKeys.list }),
              queryClient.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount }),
            ]);
          });
          retryDelay = 1000;
        } catch {
          // 연결이 끊겨도 로그인 상태가 유지되는 동안 다시 연결한다
        }
        if (stopped || controller.signal.aborted) break;
        await waitToReconnect(retryDelay, controller.signal);
        retryDelay = Math.min(retryDelay * 2, 30_000);
      }
    }

    void run();
    return () => {
      stopped = true;
      controller.abort();
    };
  }, [enabled, queryClient]);
}

function waitToReconnect(delay: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) return resolve();
    const finish = () => {
      window.clearTimeout(timeout);
      signal.removeEventListener("abort", finish);
      resolve();
    };
    const timeout = window.setTimeout(finish, delay);
    signal.addEventListener("abort", finish, { once: true });
  });
}
