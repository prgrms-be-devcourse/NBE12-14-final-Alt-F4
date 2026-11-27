import { API_URL } from "@/shared/config/env";

// 로그인 상태와 Access 토큰을 메모리에만 둔다 (localStorage는 XSS에 약하다)
// 새로고침하면 사라지므로 앱이 켜질 때 restoreSession()으로 Refresh 쿠키에서 다시 받는다
// 렌더 중에는 읽기만 하고, 쓰기는 이벤트 핸들러·fetch 계층에서만 한다 (React Compiler lint 규칙)
// - unknown: 아직 모름 (쿠키로 복구 전)
// - unreachable: 서버에 닿지 못해 모름 (네트워크·5xx). 로그아웃시키지 않고 다시 시도를 보여 준다
export type SessionStatus = "unknown" | "authenticated" | "anonymous" | "unreachable";

let accessToken: string | null = null;
let status: SessionStatus = "unknown";
let signedOutByUser = false;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

export const session = {
  getAccessToken: () => accessToken,
  getStatus: () => status,
  // 사용자가 로그아웃했는지 (세션 만료와 구분: 로그아웃이면 로그인 뒤 돌아갈 주소를 남기지 않는다)
  wasSignedOutByUser: () => signedOutByUser,
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  signIn(token: string) {
    accessToken = token;
    status = "authenticated";
    signedOutByUser = false;
    emit();
  },
  signOut(byUser = false) {
    accessToken = null;
    status = "anonymous";
    signedOutByUser = byUser;
    emit();
  },
  // 다른 탭에서 (다른 계정일 수도 있게) 로그인했다: 토큰을 버리고 쿠키로 다시 복구한다
  reset() {
    accessToken = null;
    status = "unknown";
    emit();
  },
  // 서버에 닿지 못한 뒤 "다시 시도"
  retry() {
    if (status !== "unreachable") return;
    status = "unknown";
    emit();
  },
};

function markUnreachable() {
  accessToken = null;
  status = "unreachable";
  emit();
}

type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number };

let refreshing: Promise<boolean> | null = null;

// Refresh 토큰은 쓸 때마다 바뀐다(회전). 같은 쿠키로 두 번 보내면 두 번째는 실패하므로
// 이 탭 안에서는 진행 중인 갱신 하나를 모두가 기다리고, 탭끼리는 Web Locks로 순서를 맞춘다
// (앞 탭이 받은 새 쿠키를 뒤 탭이 그대로 쓰게 된다)
export function refreshSession(): Promise<boolean> {
  refreshing ??= withRefreshLock(requestRefresh).finally(() => {
    refreshing = null;
  });
  return refreshing;
}

// 아직 모르는 상태일 때만 쿠키로 로그인 상태를 복구한다
export function restoreSession(): Promise<boolean> {
  if (status !== "unknown") return Promise.resolve(status === "authenticated");
  return refreshSession();
}

// 401(쿠키 없음·만료·폐기)일 때만 로그아웃시킨다. 네트워크 오류·5xx·429는 쿠키가 살아 있을 수 있다
async function requestRefresh(): Promise<boolean> {
  try {
    const res = await fetch(`${API_URL}/api/v1/auth/refresh`, { method: "POST", credentials: "include" });
    if (res.status === 401) {
      session.signOut();
      return false;
    }
    if (!res.ok) {
      markUnreachable();
      return false;
    }
    const body = (await res.json()) as TokenResponse;
    session.signIn(body.accessToken);
    return true;
  } catch {
    markUnreachable();
    return false;
  }
}

async function withRefreshLock(task: () => Promise<boolean>): Promise<boolean> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return await navigator.locks.request("live-calendar-auth-refresh", task);
  }
  return task();
}

// ===== 탭 사이 알림 =====
// 한 탭에서 로그아웃하면 다른 탭도 로그아웃하고, 로그인하면 다른 탭은 쿠키로 다시 복구한다
type AuthMessage = "login" | "logout";
let channel: BroadcastChannel | null | undefined;

function authChannel(): BroadcastChannel | null {
  if (channel === undefined) {
    channel = typeof window !== "undefined" && "BroadcastChannel" in window ? new BroadcastChannel("live-calendar-auth") : null;
  }
  return channel;
}

export function announceToOtherTabs(message: AuthMessage) {
  authChannel()?.postMessage(message);
}

// Providers에서 한 번 건다
export function listenToOtherTabs(): () => void {
  const target = authChannel();
  if (!target) return () => {};
  const handleMessage = (event: MessageEvent<AuthMessage>) => {
    if (event.data === "logout") session.signOut(true);
    else if (event.data === "login") session.reset();
  };
  target.addEventListener("message", handleMessage);
  return () => target.removeEventListener("message", handleMessage);
}
