export type FieldError = { field: string; reason: string };

// 백엔드 에러 응답(ProblemDetail + code)을 담는다
// code는 백엔드 ErrorCode 이름 (예: UNAUTHORIZED, INVALID_INPUT, CALENDAR_NOT_FOUND)
// fieldErrors는 400 검증 오류의 errors: [{ field, reason }]
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    readonly fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// 화면에 보여 줄 문구. 서버에 닿지 못하면(fetch의 TypeError) 연결 문구, 알 수 없는 에러는 공통 문구
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return "서버에 연결할 수 없어요. 인터넷 연결을 확인해 주세요.";
  return "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
}

// 입력 칸 하나의 서버 검증 오류 (없으면 undefined)
export function fieldError(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.fieldErrors.find((item) => item.field === field)?.reason : undefined;
}

// 이 에러가 특정 코드인지 (예: GROUP_NOT_FOUND)
export function hasCode(error: unknown, code: string): boolean {
  return error instanceof ApiError && error.code === code;
}
