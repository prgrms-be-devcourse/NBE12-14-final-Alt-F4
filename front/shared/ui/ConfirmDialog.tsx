"use client";

import type { ReactNode } from "react";
import { Button } from "./Button";
import { Modal } from "./Modal";

type Props = {
  title: string;
  message: ReactNode;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
  danger?: boolean;
  pending?: boolean;
  error?: string | null;
};

// window.confirm 대신 쓰는 확인 창
// 처음 포커스는 "취소" (위험한 동작이 Enter 한 번으로 실행되지 않게). 실패하면 창 안에 문구를 보이고 닫지 않는다
export function ConfirmDialog({ title, message, confirmLabel, onConfirm, onClose, danger = false, pending = false, error }: Props) {
  return (
    <Modal title={title} onClose={onClose} width="sm" busy={pending}>
      <div className="text-sm leading-6 text-zinc-700">{message}</div>
      {error && (
        <p role="alert" className="mt-3 text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={pending} data-autofocus>
          취소
        </Button>
        <Button onClick={onConfirm} disabled={pending} className={danger ? "bg-red-600 text-white hover:bg-red-700 disabled:bg-red-300" : undefined}>
          {pending ? "처리 중…" : confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
