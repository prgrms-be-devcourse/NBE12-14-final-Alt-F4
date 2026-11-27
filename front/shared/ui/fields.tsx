import type { InputHTMLAttributes, TextareaHTMLAttributes } from "react";
import { cn } from "@/shared/lib/cn";

// 입력 칸 (디자인 기준): 테두리 없는 회색 바탕, 포커스하면 흰 바탕 + 주 색 테두리·링
// 높이는 따로 붙인다 (cn은 클래스를 합치지 않아 h-10과 h-9를 함께 붙이면 어느 쪽이 이길지 알 수 없다)
export const fieldBaseClass =
  "rounded-field border bg-field px-3 text-sm text-foreground outline-none transition-colors placeholder:text-zinc-400 focus:border-brand focus:bg-white focus:ring-[3px] focus:ring-brand/15 disabled:text-zinc-400";
export const inputClass = `h-10 ${fieldBaseClass}`;

// 테두리 색은 하나만 붙인다 (같은 속성 클래스를 둘 붙이면 어느 쪽이 이길지 알 수 없다)
export const borderClass = (error?: string) => (error ? "border-red-400 bg-red-50/40" : "border-transparent");

export const errorId = (fieldId: string | undefined) => (fieldId ? `${fieldId}-error` : undefined);

function Label({ htmlFor, children }: { htmlFor?: string; children: string }) {
  return (
    <label htmlFor={htmlFor} className="text-[13px] font-medium text-zinc-700">
      {children}
    </label>
  );
}

export function ErrorText({ id, error }: { id?: string; error?: string }) {
  return error ? (
    <p id={id} className="text-xs text-red-600">
      {error}
    </p>
  ) : null;
}

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string };

export function TextareaField({ label, error, id, name, className, ...props }: TextareaProps) {
  const fieldId = id ?? name;
  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor={fieldId}>{label}</Label>
      <textarea
        id={fieldId}
        name={name}
        rows={3}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId(fieldId) : undefined}
        className={cn(fieldBaseClass, borderClass(error), "py-2", className)}
        {...props}
      />
      <ErrorText id={errorId(fieldId)} error={error} />
    </div>
  );
}

type CheckboxProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & { label: string };

export function CheckboxField({ label, id, name, ...props }: CheckboxProps) {
  const fieldId = id ?? name;
  return (
    <label htmlFor={fieldId} className="flex items-center gap-2 text-sm text-zinc-700">
      <input id={fieldId} name={name} type="checkbox" className="h-4 w-4 accent-brand" {...props} />
      {label}
    </label>
  );
}
