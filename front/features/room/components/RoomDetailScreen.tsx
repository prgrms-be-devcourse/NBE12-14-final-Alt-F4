"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { browserTimeZone } from "@/shared/lib/date";
import { cardClass } from "@/shared/ui/card";
import { Spinner } from "@/shared/ui/Spinner";
import { roomRangeText } from "../format";
import { useRoom } from "../hooks";

// /rooms/[roomId]: 조율 범위와 참여자. 공유 일정·빈 시간·확정·채팅·초대는 API가 생기면 붙인다
export function RoomDetailScreen() {
  const roomId = useParams<{ roomId: string }>().roomId;
  const room = useRoom(roomId);
  const [myTimeZone] = useState(browserTimeZone);

  if (room.isPending) return <Spinner fullScreen />;
  if (room.isError) {
    return (
      <main className="flex flex-col items-center gap-3 px-4 py-24 text-center">
        <p className="text-sm text-zinc-600">{errorMessage(room.error)}</p>
        <Link href="/rooms" className="text-sm text-brand hover:underline">
          목록으로
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-8">
      <div className="flex items-center gap-2">
        <h1 className="min-w-0 break-words text-[22px] font-semibold">{room.data.name}</h1>
        {room.data.isOwner && <span className="rounded-full bg-brand-soft px-2 text-xs font-semibold text-brand-ink">방장</span>}
        <Link href="/rooms" className="ml-auto shrink-0 text-sm text-brand hover:underline">
          목록으로
        </Link>
      </div>
      <p className="text-sm text-zinc-600">{roomRangeText(room.data, myTimeZone)}</p>
      <p className="text-sm">
        <Link href="/calendar" className="text-brand hover:underline">
          캘린더에서 보기
        </Link>
        <span className="text-zinc-500"> · 확정한 일정은 사이드바의 이 방 캘린더에 보여요.</span>
      </p>

      <section className={`${cardClass} flex flex-col gap-3`}>
        <h2 className="text-base font-semibold">참여자 {room.data.members.length}명</h2>
        <ul className="flex flex-col gap-1">
          {room.data.members.map((member) => (
            <li key={member.tag} className="flex items-center gap-2 text-sm">
              <span>
                {member.nickname}
                <span className="text-zinc-400">#{member.tag}</span>
              </span>
              {member.isOwner && <span className="rounded-full bg-zinc-100 px-2 text-[11px] font-semibold text-zinc-600">방장</span>}
            </li>
          ))}
        </ul>
      </section>

      <section className={`${cardClass} text-sm text-muted`}>
        일정 공유, 겹치지 않는 시간, 일정 확정, 채팅, 초대는 준비 중이에요.
      </section>
    </main>
  );
}
