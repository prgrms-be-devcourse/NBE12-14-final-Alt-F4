"use client";

import { useEffect, useState } from "react";

// 지금 시각. intervalMs마다, 그리고 화면이 다시 보일 때 새로 잡는다
// (탭을 켜 둔 채 자정이 지나도 오늘 표시와 지금 시각 선이 오늘로 옮겨 간다)
export function useNow(intervalMs = 60_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const tick = () => setNow(new Date());
    const timer = window.setInterval(tick, intervalMs);
    const handleVisibility = () => {
      if (document.visibilityState === "visible") tick();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [intervalMs]);
  return now;
}
