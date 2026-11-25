"use client";

import { X } from "lucide-react";
import { useEffect, useId, useRef, type ReactNode } from "react";
import { IconButton } from "./IconButton";

type Props = {
  title: string;
  onClose: () => void;
  children: ReactNode;
  width?: "sm" | "md";
  busy?: boolean; // 저장 중: Esc·바깥 클릭으로 닫지 않는다
};

// 화면 위에 띄우는 창 (네이티브 <dialog>의 showModal)
// 브라우저가 포커스를 창 안에 가두고, 뒤 화면을 누를 수 없게 한다
// - 처음 포커스: data-autofocus가 붙은 요소 (React의 autoFocus는 창이 열리기 전에 실행돼 쓰지 않는다)
// - 닫으면 창을 열기 전에 포커스가 있던 곳으로 돌려준다
// - Esc·바깥 클릭으로 닫는다. 바깥은 누르기와 떼기가 모두 바깥일 때만 (입력 칸에서 끌어 선택하다 바깥에서 떼도 닫히지 않게)
export function Modal({ title, onClose, children, width = "md", busy = false }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const pressedOutside = useRef(false);
  const composingEscape = useRef(false);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const root = document.documentElement;
    const previousOverflow = root.style.overflow;

    dialog.showModal();
    dialog.querySelector<HTMLElement>("[data-autofocus]")?.focus();
    root.style.overflow = "hidden";

    return () => {
      root.style.overflow = previousOverflow;
      // 창이 DOM에서 빠진 뒤에 돌려준다 (아직 모달로 남아 있으면 창 밖 요소는 inert라 포커스가 가지 않는다)
      // 그 사이 다른 곳에 포커스가 갔으면(다른 창을 연 경우 등) 건드리지 않는다
      window.setTimeout(() => {
        const active = document.activeElement;
        if (previousFocus?.isConnected && (!active || active === document.body || active instanceof HTMLDialogElement)) previousFocus.focus();
      }, 0);
    };
  }, []);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onKeyDown={(event) => {
        // 한글을 조합하는 중의 Esc는 조합 취소일 뿐이다
        if (event.key === "Escape") composingEscape.current = event.nativeEvent.isComposing;
      }}
      // React는 안쪽 창(창 안의 창)의 cancel·close 이벤트를 바깥 창에도 전달한다
      // 자기 창에서 난 것만 처리해야 안쪽 창의 Esc가 바깥 창까지 닫지 않는다
      onCancel={(event) => {
        if (event.target !== event.currentTarget) return;
        // 브라우저가 스스로 닫지 않게 하고, 닫을지는 여기서 정한다
        event.preventDefault();
        if (!busy && !composingEscape.current) onClose();
      }}
      onClose={(event) => {
        // 브라우저가 강제로 닫은 경우 (Esc를 여러 번 누르는 등)
        if (event.target === event.currentTarget) onClose();
      }}
      onMouseDown={(event) => {
        pressedOutside.current = event.target === event.currentTarget;
      }}
      onClick={(event) => {
        if (pressedOutside.current && event.target === event.currentTarget && !busy) onClose();
        pressedOutside.current = false;
      }}
      className={`m-auto w-[calc(100%-2rem)] rounded-card bg-white p-0 text-foreground shadow-dialog backdrop:bg-zinc-900/30 ${
        width === "sm" ? "max-w-sm" : "max-w-lg"
      }`}
    >
      <div className="max-h-[90vh] overflow-y-auto px-6 pb-6 pt-5">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id={titleId} className="min-w-0 break-words text-[17px] font-semibold">
            {title}
          </h2>
          <IconButton label="닫기" size="sm" onClick={onClose} disabled={busy} className="-mr-2">
            <X className="h-[18px] w-[18px]" aria-hidden />
          </IconButton>
        </div>
        {children}
      </div>
    </dialog>
  );
}
