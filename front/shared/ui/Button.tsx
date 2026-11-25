import type { ComponentProps } from "react";
import { cn } from "@/shared/lib/cn";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "md" | "sm";

const variants: Record<Variant, string> = {
  primary: "bg-brand text-white hover:bg-brand-hover disabled:bg-brand/40",
  secondary: "border border-line-strong bg-white text-foreground hover:bg-zinc-50 disabled:text-zinc-400",
  ghost: "text-zinc-600 hover:bg-zinc-100 hover:text-foreground disabled:text-zinc-400",
  danger: "bg-red-50 text-red-700 hover:bg-red-100 disabled:text-red-300",
};

const sizes: Record<Size, string> = {
  md: "h-10 px-5 text-sm",
  sm: "h-8 px-3.5 text-[13px]",
};

// <Link>를 버튼처럼 보이게 할 때도 같은 클래스를 쓴다
export function buttonClass(variant: Variant = "primary", size: Size = "md"): string {
  return cn(
    "inline-flex shrink-0 items-center justify-center gap-1.5 rounded-full font-semibold transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-brand/30 disabled:cursor-not-allowed",
    sizes[size],
    variants[variant],
  );
}

// React 19: ref도 일반 prop으로 받는다
type Props = ComponentProps<"button"> & { variant?: Variant; size?: Size };

// 알약 모양 버튼 (디자인 기준). 주 동작은 primary, 보조는 secondary(테두리), 취소류는 ghost, 지우기는 danger
export function Button({ variant = "primary", size = "md", className, type = "button", ...props }: Props) {
  return (
    <button
      type={type}
      className={cn(buttonClass(variant, size), className)}
      {...props}
    />
  );
}
