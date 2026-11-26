import { api } from "@/shared/api/client";
import { announceToOtherTabs, session } from "@/shared/api/session";
import type { LoginRequest, Me, SignupRequest, TokenResponse } from "./types";

// 로그인: Access 토큰은 메모리에, Refresh 토큰은 서버가 HttpOnly 쿠키로 준다
export async function login(request: LoginRequest): Promise<void> {
  const token = await api<TokenResponse>("/api/v1/auth/login", { method: "POST", body: request });
  session.signIn(token.accessToken);
  announceToOtherTabs("login");
}

// 회원가입은 토큰을 주지 않는다 → 이어서 login()을 부른다
export function signup(request: SignupRequest): Promise<{ userId: number }> {
  return api("/api/v1/auth/signup", { method: "POST", body: request });
}

// 서버가 Refresh 쿠키를 지운다. 서버 호출이 실패해도 이 브라우저에서는 로그아웃한다
export async function logout(): Promise<void> {
  try {
    await api<void>("/api/v1/auth/logout", { method: "POST" });
  } finally {
    session.signOut(true);
    announceToOtherTabs("logout");
  }
}

export function fetchMe(): Promise<Me> {
  return api<Me>("/api/v1/users/me");
}
