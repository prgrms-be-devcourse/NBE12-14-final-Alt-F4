import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "페이지를 찾을 수 없어요" };

export default function NotFound() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-3 px-4 py-24 text-center">
      <h1 className="text-xl font-semibold">페이지를 찾을 수 없어요</h1>
      <Link href="/" className="text-sm text-brand hover:underline">
        처음으로
      </Link>
    </main>
  );
}
