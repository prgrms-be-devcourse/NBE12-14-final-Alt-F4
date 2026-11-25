"use client";

import { useEffectEvent, useLayoutEffect, useRef, type RefObject } from "react";

type Options = {
  open: boolean;
  anchorRef: RefObject<HTMLElement | null>;
  onClose: () => void;
  maxHeight?: number; // 목록 최대 높이(px). 아래 공간이 모자라면 위로 펼친다
};

// 드롭다운·시간대 검색·만들기 메뉴가 같이 쓰는 "목록 띄우기"
// - popover="manual" 요소를 열려 있을 때만 그리고, 그린 뒤 showPopover()로 맨 위 층에 올린다
//   (창(<dialog>)의 스크롤 상자에 잘리지 않는다. 포털로 옮기지 않아야 모달 창 안에서 누를 수 있다)
// - 브라우저 기본 [popover] 스타일(inset:0, margin:auto 등)을 덮도록 위치를 style에 직접 넣는다
// - 바깥 pointerdown·바깥 스크롤·창 크기 변경에 닫거나 다시 놓는다
// - 창 바깥(배경)을 누르면 목록만 닫고, 이어지는 click 하나를 먹어 창이 같이 닫히지 않게 한다
export function usePopover({ open, anchorRef, onClose, maxHeight = 288 }: Options) {
  const popoverRef = useRef<HTMLDivElement>(null);
  const close = useEffectEvent(onClose);

  useLayoutEffect(() => {
    if (!open) return;
    const popover = popoverRef.current;
    const anchor = anchorRef.current;
    if (!popover || !anchor) return;
    if (typeof popover.showPopover === "function" && !popover.matches(":popover-open")) popover.showPopover();

    const place = () => {
      const rect = anchor.getBoundingClientRect();
      const below = window.innerHeight - rect.bottom;
      const height = Math.min(popover.scrollHeight, maxHeight);
      const up = below < height + 8 && rect.top > below;
      Object.assign(popover.style, {
        position: "fixed",
        margin: "0",
        left: `${Math.max(4, Math.min(rect.left, window.innerWidth - popover.offsetWidth - 4))}px`,
        right: "auto",
        top: up ? "auto" : `${rect.bottom + 4}px`,
        bottom: up ? `${window.innerHeight - rect.top + 4}px` : "auto",
        minWidth: `${rect.width}px`,
      });
    };
    place();

    const handleResize = () => place();
    const handleScroll = (event: Event) => {
      // 목록 안의 스크롤(항목으로 스크롤 등)은 무시한다
      if (event.target instanceof Node && popover.contains(event.target)) return;
      close();
    };
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (popover.contains(target) || anchor.contains(target)) return;
      if (target instanceof HTMLDialogElement) {
        // 창 바깥(::backdrop)을 눌렀다: 목록만 닫고, 창을 닫는 click 하나를 먹는다
        const swallow = (clickEvent: MouseEvent) => {
          clickEvent.stopPropagation();
          window.removeEventListener("click", swallow, true);
        };
        window.addEventListener("click", swallow, true);
        window.setTimeout(() => window.removeEventListener("click", swallow, true), 600);
      }
      close();
    };
    window.addEventListener("resize", handleResize);
    window.addEventListener("scroll", handleScroll, true);
    document.addEventListener("pointerdown", handlePointerDown, true);
    return () => {
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("scroll", handleScroll, true);
      document.removeEventListener("pointerdown", handlePointerDown, true);
    };
  }, [open, anchorRef, maxHeight]);

  return popoverRef;
}

// 목록 요소에 붙일 기본 모양 (popover 기본 스타일을 지우고, 안쪽 div가 모양을 맡는다)
export const popoverClass = "m-0 overflow-visible border-0 bg-transparent p-0";
export const popoverPanelClass = "max-h-72 overflow-y-auto rounded-xl bg-white p-1.5 text-sm shadow-pop";
