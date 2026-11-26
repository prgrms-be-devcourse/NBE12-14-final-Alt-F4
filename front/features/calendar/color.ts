// 색이 없는 캘린더·카테고리의 기본 색
export const DEFAULT_COLOR = "#3b82f6";

export function colorOf(color: string | null | undefined): string {
  return color || DEFAULT_COLOR;
}

// 일정 막대·블록의 옅은 바탕과 진한 글자: 어떤 색이든 같은 규칙으로 섞는다
export const tintOf = (color: string) => `color-mix(in srgb, ${color} 14%, white)`;
export const inkOf = (color: string) => `color-mix(in srgb, ${color} 70%, black)`;

// 새 캘린더에서 고르는 색 (첫 번째가 기본 색). 흰 배경에서 잘 보이도록 노랑·초록·청록·분홍은 한 단계 진하게
export const CALENDAR_COLORS: ReadonlyArray<{ value: string; name: string }> = [
  { value: DEFAULT_COLOR, name: "파랑" },
  { value: "#ef4444", name: "빨강" },
  { value: "#f97316", name: "주황" },
  { value: "#ca8a04", name: "노랑" },
  { value: "#16a34a", name: "초록" },
  { value: "#0d9488", name: "청록" },
  { value: "#8b5cf6", name: "보라" },
  { value: "#db2777", name: "분홍" },
];
