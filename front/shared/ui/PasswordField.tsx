"use client";

import { useState, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import { borderClass, ErrorText, errorId, inputClass } from "./fields";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & { label: string; error?: string; hint?: ReactNode };

// 비밀번호 칸 + 보기·숨기기 버튼. hint는 칸 아래(예: 규칙 체크 목록)
export function PasswordField({ label, error, hint, id, name, className, ...props }: Props) {
  const [visible, setVisible] = useState(false);
  const inputId = id ?? name;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-medium text-zinc-700">
        {label}
      </label>
      <div className="relative">
        <input
          id={inputId}
          name={name}
          type={visible ? "text" : "password"}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId(inputId) : undefined}
          className={cn(inputClass, borderClass(error), "w-full pr-16", className)}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((previous) => !previous)}
          aria-pressed={visible}
          aria-label={visible ? "비밀번호 숨기기" : "비밀번호 보기"}
          className="absolute inset-y-0 right-0 px-3 text-xs text-zinc-500 hover:text-zinc-800"
        >
          {visible ? "숨기기" : "보기"}
        </button>
      </div>
      {hint}
      <ErrorText id={errorId(inputId)} error={error} />
    </div>
  );
}
