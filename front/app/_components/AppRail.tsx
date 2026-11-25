"use client";

import { Bell, CalendarDays, Compass, LogIn, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ComponentType } from "react";
import { useMe, useSessionStatus } from "@/features/auth/hooks";
import { useLogout } from "@/features/auth/useLogout";
import { restoreSession } from "@/shared/api/session";
import { cn } from "@/shared/lib/cn";
import { MenuButton } from "@/shared/ui/MenuButton";

const menus: { href: string; label: string; icon: ComponentType<{ className?: string; "aria-hidden"?: boolean }> }[] = [
  { href: "/calendar", label: "캘린더", icon: CalendarDays },
  { href: "/rooms", label: "조율", icon: Users },
  { href: "/explore", label: "둘러보기", icon: Compass },
];

// 왼쪽 아이콘 줄 (디자인 기준: 앱 이동은 여기, 화면 위쪽은 각 화면의 툴바가 쓴다)
// 로그인한 화면과 로그인 없이 보는 화면(둘러보기 등)이 같이 쓴다
// 로그인 없이 보는 화면에서는 쿠키로 로그인 상태를 복구해 보고, 로그인하지 않았으면 로그인 버튼을 보인다
export function AppRail() {
  const pathname = usePathname();
  const router = useRouter();
  const status = useSessionStatus();
  const me = useMe();
  const { logout, pending } = useLogout();

  useEffect(() => {
    if (status === "unknown") void restoreSession();
  }, [status]);

  const nickname = me.data?.nickname || "";
  return (
    <nav aria-label="앱 메뉴" className="flex w-[72px] shrink-0 flex-col items-center gap-1.5 border-r border-line bg-white py-4">
      <Link
        href={status === "authenticated" ? "/calendar" : "/"}
        aria-label="Live Calendar 처음으로"
        className="mb-4 flex h-9 w-9 items-center justify-center rounded-[10px] bg-brand text-white outline-none focus-visible:ring-[3px] focus-visible:ring-brand/30"
      >
        <CalendarDays className="h-5 w-5" aria-hidden />
      </Link>
      {menus.map(({ href, label, icon: Icon }) => {
        const active = pathname.startsWith(href);
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "flex w-14 flex-col items-center gap-1 rounded-xl py-2 text-[11px] outline-none focus-visible:ring-[3px] focus-visible:ring-brand/30",
              active ? "bg-brand-soft font-semibold text-brand-ink" : "font-medium text-zinc-600 hover:bg-zinc-100",
            )}
          >
            <Icon className="h-5 w-5" aria-hidden />
            {label}
          </Link>
        );
      })}
      <div className="flex-1" />
      {status === "authenticated" ? (
        <>
          {/* 알림 API가 정해지면 패널을 붙인다 */}
          <button
            type="button"
            disabled
            title="알림 (준비 중)"
            aria-label="알림 (준비 중)"
            className="flex h-10 w-10 items-center justify-center rounded-xl text-zinc-400"
          >
            <Bell className="h-5 w-5" aria-hidden />
          </button>
          <MenuButton
            ariaLabel={nickname ? `${nickname} 메뉴` : "내 메뉴"}
            label={<span aria-hidden>{nickname.slice(0, 1) || "나"}</span>}
            className="mt-1.5 h-9 w-9 rounded-full bg-[#dde5ff] text-[13px] font-bold text-brand-ink outline-none hover:brightness-95 focus-visible:ring-[3px] focus-visible:ring-brand/30"
            items={[
              { label: nickname ? `${nickname} · 내 설정` : "내 설정", onSelect: () => router.push("/settings") },
              { type: "separator" },
              { label: pending ? "로그아웃 중…" : "로그아웃", onSelect: () => void logout() },
            ]}
          />
        </>
      ) : status === "anonymous" ? (
        <Link
          href="/login"
          className="flex w-14 flex-col items-center gap-1 rounded-xl py-2 text-[11px] font-semibold text-brand hover:bg-brand-soft"
        >
          <LogIn className="h-5 w-5" aria-hidden />
          로그인
        </Link>
      ) : null}
    </nav>
  );
}
