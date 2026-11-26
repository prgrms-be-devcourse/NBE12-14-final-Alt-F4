"use client";

import { isSameMonth } from "date-fns";
import { useState } from "react";
import { MiniMonth } from "./MiniMonth";

type Props = { year: number; today: Date; onDayClick: (day: Date) => void };

// 연도 보기: 미니 달력 12개 (일정은 그리지 않는다, 구글과 같음). 날짜를 누르면 그날 일 보기
// 달마다 따로 방향키로 움직이고, 달 끝을 넘으면 그 달 안에서 멈춘다 (Tab으로 다음 달)
export function YearView({ year, today, onDayClick }: Props) {
  const [focusDay, setFocusDay] = useState<Date | null>(null);
  const months = Array.from({ length: 12 }, (_, index) => new Date(year, index, 1));
  return (
    <div className="min-h-0 flex-1 overflow-y-auto p-6">
      <div className="grid grid-cols-2 gap-x-8 gap-y-6 md:grid-cols-3 xl:grid-cols-4">
        {months.map((month) => (
          <MiniMonth
            key={month.getMonth()}
            month={month}
            today={today}
            showNav={false}
            title={`${month.getMonth() + 1}월`}
            onPick={onDayClick}
            focusDay={focusDay && isSameMonth(focusDay, month) ? focusDay : null}
            onFocusDay={(day) => {
              if (isSameMonth(day, month)) setFocusDay(day);
            }}
          />
        ))}
      </div>
    </div>
  );
}
