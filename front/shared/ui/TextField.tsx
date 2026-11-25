import type { InputHTMLAttributes } from "react";
import { cn } from "@/shared/lib/cn";
import { borderClass, ErrorText, errorId, inputClass } from "./fields";

type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string };

// 라벨 + 입력 + 에러 문구 (에러 문구는 aria-describedby로 입력 칸에 이어 둔다)
export function TextField({ label, error, id, name, className, ...props }: Props) {
  const inputId = id ?? name;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-[13px] font-medium text-zinc-700">
        {label}
      </label>
      <input
        id={inputId}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId(inputId) : undefined}
        className={cn(inputClass, borderClass(error), className)}
        {...props}
      />
      <ErrorText id={errorId(inputId)} error={error} />
    </div>
  );
}
