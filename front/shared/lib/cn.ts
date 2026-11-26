// 조건부 className을 이어 붙인다: cn("a", isOn && "b") → "a b"
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
}
