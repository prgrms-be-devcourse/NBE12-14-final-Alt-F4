import { API_URL } from "@/shared/config/env";
import { ApiError, type FieldError } from "./errors";
import { refreshSession, session } from "./session";

type QueryValue = string | number | boolean | null | undefined | Array<string | number>;

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Record<string, QueryValue>;
};

// 모든 API 호출은 이 함수 하나로 한다
// - Access 토큰을 Authorization 헤더에 붙인다
// - 토큰이 만료돼 401 UNAUTHORIZED가 오면 한 번만 갱신하고 다시 보낸다
// - 실패 응답은 ApiError(status, code, message, fieldErrors)로 던진다
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const res = await send(path, options);
  if (res.status === 401 && canRetryAfterRefresh(path) && (await codeOf(res.clone())) === "UNAUTHORIZED") {
    if (await refreshSession()) {
      return parse<T>(await send(path, options));
    }
  }
  return parse<T>(res);
}

function send(path: string, { method = "GET", body, query }: RequestOptions): Promise<Response> {
  const headers = new Headers();
  // 인증 API에는 토큰을 붙이지 않는다: 만료된 토큰이 붙으면 permitAll 경로여도 서버가 401을 낸다 (로그아웃이 안 됨)
  const token = isAuthPath(path) ? null : session.getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (body !== undefined) headers.set("Content-Type", "application/json");

  return fetch(`${API_URL}${path}${toQueryString(query)}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    // Refresh 쿠키(/api/v1/auth 경로)를 주고받으려면 필요하다
    credentials: "include",
  });
}

// 로그인 실패(INVALID_CREDENTIALS)·갱신 실패(INVALID_REFRESH_TOKEN)도 401이라 인증 API는 다시 보내지 않는다
function canRetryAfterRefresh(path: string): boolean {
  return !isAuthPath(path);
}

function isAuthPath(path: string): boolean {
  return path.startsWith("/api/v1/auth/");
}

async function codeOf(res: Response): Promise<string | undefined> {
  try {
    return ((await res.json()) as { code?: string }).code;
  } catch {
    return undefined;
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (res.ok) {
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  let problem: { code?: string; detail?: string; title?: string; errors?: FieldError[] } = {};
  try {
    problem = await res.json();
  } catch {
    // 본문이 JSON이 아니면 상태 코드만 쓴다
  }
  throw new ApiError(
    res.status,
    problem.code,
    problem.detail ?? problem.title ?? `요청이 실패했어요 (${res.status})`,
    problem.errors ?? [],
  );
}

// 배열은 같은 이름을 반복한다 (calendarIds=1&calendarIds=2)
function toQueryString(query?: Record<string, QueryValue>): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined) continue;
    if (Array.isArray(value)) value.forEach((item) => params.append(key, String(item)));
    else params.append(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : "";
}
