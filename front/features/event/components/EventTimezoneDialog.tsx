"use client";

import { useState, type FormEvent } from "react";
import { browserTimeZone } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { Modal } from "@/shared/ui/Modal";
import { TimezoneCombobox } from "@/shared/ui/TimezoneCombobox";

type Props = { value: string; onDone: (timezone: string) => void; onClose: () => void };

// 일정 시간대 창. 시간대를 바꾸면 벽시계 값(예: 10:00)은 그대로 두고 시간대만 바뀐다 (구글과 같음)
// 시작·끝 시간대를 따로 쓰는 기능은 없다 (API의 시간대가 하나)
// EventForm의 <form> 밖에 그린다 (Modal은 포털이 없어, 안에 두면 Enter가 일정 폼을 저장한다)
export function EventTimezoneDialog({ value, onDone, onClose }: Props) {
  const [zone, setZone] = useState(value);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onDone(zone);
  }

  return (
    <Modal title="일정 시간대" onClose={onClose} width="sm">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TimezoneCombobox label="일정 시간대" value={zone} onChange={setZone} autoFocusInput />
        <p className="text-xs text-zinc-500">시간대를 바꾸면 입력한 시각은 그대로 두고, 그 시간대의 시각으로 저장해요.</p>
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="ghost" onClick={() => setZone(browserTimeZone())} className="mr-auto">
            현재 시간대 사용
          </Button>
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit">확인</Button>
        </div>
      </form>
    </Modal>
  );
}
