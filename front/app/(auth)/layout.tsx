import { CalendarDays } from "lucide-react";
import Link from "next/link";
import { Suspense, type ReactNode } from "react";
import { RedirectIfSignedIn } from "@/features/auth/components/RedirectIfSignedIn";
import { Spinner } from "@/shared/ui/Spinner";

// 로그인 전 화면 (가운데 카드)
// Suspense: 안에서 useSearchParams 등을 쓰면 cacheComponents 때문에 Suspense 없이는 next build가 실패한다
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="flex flex-1 flex-col items-center justify-center px-4 py-12">
      <Link href="/" className="mb-7 flex items-center gap-2.5 text-lg font-bold text-foreground">
        <span className="flex h-9 w-9 items-center justify-center rounded-[10px] bg-brand text-white">
          <CalendarDays className="h-5 w-5" aria-hidden />
        </span>
        Live Calendar
      </Link>
      <div className="w-full max-w-sm rounded-card bg-white p-7 shadow-raised">
        <Suspense fallback={<Spinner />}>
          <RedirectIfSignedIn />
          {children}
        </Suspense>
      </div>
    </main>
  );
}
