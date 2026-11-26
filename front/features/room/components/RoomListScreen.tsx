"use client";

import { format } from "date-fns";
import { Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { browserTimeZone } from "@/shared/lib/date";
import { Button, buttonClass } from "@/shared/ui/Button";
import { cardClass } from "@/shared/ui/card";
import { Spinner } from "@/shared/ui/Spinner";
import { isPastRoom, roomRangeText } from "../format";
import { useRooms } from "../hooks";

// /rooms: 내가 참여 중인 조율방. 끝 날짜가 지난 방은 흐리게
export function RoomListScreen() {
  const rooms = useRooms();
  const [today] = useState(() => format(new Date(), "yyyy-MM-dd"));
  const [myTimeZone] = useState(browserTimeZone);

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-col gap-4 px-4 py-8">
      <div className="flex items-center">
        <h1 className="text-[22px] font-semibold">일정 조율</h1>
        <Link href="/rooms/new" className={cn(buttonClass(), "ml-auto")}>
          <Plus className="h-4 w-4" aria-hidden />새 조율방
        </Link>
      </div>
      {rooms.isPending && <Spinner />}
      {rooms.isError && (
        <div className="flex items-center gap-3">
          <p className="text-sm text-red-600">{errorMessage(rooms.error)}</p>
          <Button variant="secondary" onClick={() => void rooms.refetch()}>
            다시 시도
          </Button>
        </div>
      )}
      {rooms.data?.length === 0 && (
        <div className={cn(cardClass, "flex flex-col items-center gap-3 py-10 text-center")}>
          <p className="text-sm text-muted">참여 중인 조율방이 없어요.</p>
          <Link href="/rooms/new" className={buttonClass()}>
            첫 조율방 만들기
          </Link>
        </div>
      )}
      <ul className="flex flex-col gap-2">
        {rooms.data?.map((room) => {
          const past = isPastRoom(room, today);
          return (
            <li key={room.id}>
              <Link
                href={`/rooms/${room.id}`}
                className={cn(
                  "flex flex-col gap-1 rounded-2xl bg-white px-5 py-4 shadow-card transition-shadow hover:shadow-raised focus-visible:outline-2 focus-visible:outline-brand",
                  past && "opacity-60",
                )}
              >
                <span className="flex items-center gap-2 font-medium">
                  {room.name}
                  {room.isOwner && <span className="rounded-full bg-brand-soft px-2 text-[11px] font-semibold text-brand-ink">방장</span>}
                  {past && <span className="rounded-full bg-zinc-100 px-2 text-[11px] font-semibold text-zinc-600">지난 방</span>}
                </span>
                <span className="text-[13px] text-muted">{roomRangeText(room, myTimeZone)}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </main>
  );
}
