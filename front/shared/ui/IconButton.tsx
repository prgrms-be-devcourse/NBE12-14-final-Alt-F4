import type { ComponentProps } from "react";
import { cn } from "@/shared/lib/cn";

type Props = ComponentProps<"button"> & { label: string; size?: "md" | "sm" };

// 아이콘만 있는 둥근 버튼. label은 화면 읽기용 이름(aria-label)과 마우스 올림 글(title)
export function IconButton({ label, size = "md", className, type = "button", title, ...props }: Props) {
  return (
    <button
      type={type}
      aria-label={label}
      title={title ?? label}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full text-zinc-600 transition-colors outline-none hover:bg-zinc-100 hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-brand/30 disabled:cursor-not-allowed disabled:opacity-40",
        size === "md" ? "h-9 w-9" : "h-7 w-7",
        className,
      )}
      {...props}
    />
  );
}
