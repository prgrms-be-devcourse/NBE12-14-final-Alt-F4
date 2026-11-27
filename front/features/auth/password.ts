// 회원가입 비밀번호 규칙 (백엔드 SignupRequest와 같다)
// 8~64자, 영문·숫자·특수문자 각 1개 이상, 공백·한글 없이 ASCII 0x21~0x7E만
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d])[\x21-\x7E]+$/;

export const passwordRules: ReadonlyArray<{ label: string; test: (password: string) => boolean }> = [
  { label: "8~64자", test: (password) => password.length >= 8 && password.length <= 64 },
  { label: "영문", test: (password) => /[A-Za-z]/.test(password) },
  { label: "숫자", test: (password) => /\d/.test(password) },
  { label: "특수문자", test: (password) => /[^A-Za-z\d\s]/.test(password) },
  { label: "공백·한글 없음", test: (password) => password.length > 0 && /^[\x21-\x7E]+$/.test(password) },
];

export function isValidPassword(password: string): boolean {
  return password.length >= 8 && password.length <= 64 && PASSWORD_PATTERN.test(password);
}
