import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { hashKey, type Query, type QueryClient } from "@tanstack/react-query";
import { calendarQueryKeys } from "@/features/calendar/hooks";
import type { CalendarGroup } from "@/features/calendar/types";
import { eventQueryKeys } from "@/features/event/hooks";
import { refreshSession, session } from "@/shared/api/session";
import { API_URL } from "@/shared/config/env";

// 실시간 동기화 (백엔드 STOMP /ws). 신호에는 내용이 없다: 받으면 바뀐 것만 조회 API로 다시 불러온다
// - /user/queue/display: 내 화면 설정(표시·숨김·내 색·그룹·새 캘린더)이 바뀌었다. 내 연결에만 온다
// - /topic/calendars/{id}: 그 캘린더의 일정·캘린더·카테고리가 바뀌었다. 그 캘린더를 이용하는 모두에게 온다
// 구독할 캘린더는 그룹 목록(GET /groups)을 따른다. 목록이 바뀌면 구독도 늘리고 줄인다
// (캘린더 만들기·구독 해지·조율방 만들기는 목록을 다시 불러오므로 따로 구독하지 않아도 된다)

type CalendarSignal = {
  type: "EVENTS_CHANGED" | "CALENDAR_UPDATED" | "CALENDAR_DELETED" | "CATEGORIES_CHANGED";
  calendarId: number;
  eventId: number | null;
  from: string | null; // 바뀐 기간 (ISO). null이면 전체 (반복 일정 등)
  to: string | null;
};

const WS_URL = `${API_URL.replace(/^http/, "ws")}/ws`;
const DISPLAY_QUEUE = "/user/queue/display";
const calendarTopic = (calendarId: number) => `/topic/calendars/${calendarId}`;
const GROUPS_HASH = hashKey(calendarQueryKeys.groups);

// 다시 불러올 것을 모아 두었다가 한 번에 부른다 (여러 사람이 동시에 몰아서 부르지 않게 조금씩 흩뜨린다)
const MIN_DELAY_MS = 200;
const JITTER_MS = 800;
// 연결을 거절당해 토큰을 갱신한 뒤 이 시간 안에 또 거절당하면 토큰 문제가 아니므로 갱신하지 않는다 (Refresh 토큰을 계속 바꾸지 않게)
const REFRESH_COOLDOWN_MS = 60_000;

type Pending = { groups: boolean; calendarDetail: boolean; allEvents: boolean; ranges: Array<[number, number]> };
const emptyPending = (): Pending => ({ groups: false, calendarDetail: false, allEvents: false, ranges: [] });

// 연결을 시작하고, 끝낼 함수를 돌려준다 (로그인한 동안만 부른다)
export function startRealtimeSync(queryClient: QueryClient): () => void {
  const calendarSubscriptions = new Map<number, StompSubscription>();
  let pending = emptyPending();
  let timer: ReturnType<typeof setTimeout> | null = null;
  let needsRefresh = false;
  let lastRefreshAt = 0;
  let stopped = false;

  function flush() {
    timer = null;
    const { groups, calendarDetail, allEvents, ranges } = pending;
    pending = emptyPending();
    if (groups) void queryClient.invalidateQueries({ queryKey: calendarQueryKeys.groups });
    if (calendarDetail) void queryClient.invalidateQueries({ queryKey: ["calendar", "detail"] });
    if (allEvents) void queryClient.invalidateQueries({ queryKey: eventQueryKeys.all });
    else if (ranges.length > 0) void queryClient.invalidateQueries({ queryKey: eventQueryKeys.all, predicate: (query) => touches(query, ranges) });
  }

  function schedule() {
    // 끝낸 뒤(로그아웃) 늦게 도착한 신호는 무시한다
    if (stopped) return;
    timer ??= setTimeout(flush, MIN_DELAY_MS + Math.random() * JITTER_MS);
  }

  // 그룹 목록의 캘린더와 구독을 맞춘다. 새로 구독한 캘린더가 있으면 true
  function syncCalendarSubscriptions(): boolean {
    if (!client.connected) return false;
    const groups = queryClient.getQueryData<CalendarGroup[]>(calendarQueryKeys.groups);
    if (!groups) return false;
    const wanted = new Set(groups.flatMap((group) => group.calendars.map((calendar) => calendar.id)));
    for (const [calendarId, subscription] of calendarSubscriptions) {
      if (!wanted.has(calendarId)) {
        subscription.unsubscribe();
        calendarSubscriptions.delete(calendarId);
      }
    }
    let added = false;
    for (const calendarId of wanted) {
      if (!calendarSubscriptions.has(calendarId)) {
        calendarSubscriptions.set(calendarId, client.subscribe(calendarTopic(calendarId), handleCalendarSignal));
        added = true;
      }
    }
    return added;
  }

  function handleCalendarSignal(message: IMessage) {
    const signal = parse<CalendarSignal>(message);
    if (!signal) return;
    switch (signal.type) {
      case "EVENTS_CHANGED": {
        const from = toTime(signal.from);
        const to = toTime(signal.to);
        if (from !== null && to !== null) pending.ranges.push([from, to]);
        else pending.allEvents = true;
        break;
      }
      case "CALENDAR_DELETED":
        // 지운 캘린더는 다시 구독하지 않는다 (목록을 다시 불러오면 빠진다)
        calendarSubscriptions.get(signal.calendarId)?.unsubscribe();
        calendarSubscriptions.delete(signal.calendarId);
        pending.groups = pending.calendarDetail = pending.allEvents = true;
        break;
      case "CALENDAR_UPDATED":
        pending.groups = pending.calendarDetail = pending.allEvents = true;
        break;
      case "CATEGORIES_CHANGED":
        pending.groups = pending.allEvents = true;
        break;
      default:
        return;
    }
    schedule();
  }

  // 숨김·내 색·그룹·새 캘린더: 목록·캘린더 상세(설정 화면의 그룹)·일정을 다시 불러온다 (구독은 목록이 바뀌면 아래에서 맞춘다)
  function handleDisplaySignal() {
    pending.groups = pending.calendarDetail = pending.allEvents = true;
    schedule();
  }

  const client = new Client({
    brokerURL: WS_URL,
    // 끊기면 3~6초마다 다시 연결한다. 사람마다 간격을 달리해 서버가 다시 뜰 때 모두가 한꺼번에 붙지 않게 한다
    reconnectDelay: 3_000 + Math.random() * 3_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    // 연결(재연결)할 때마다 그때의 토큰을 붙인다
    // 토큰은 서버가 연결을 거절했을 때만 갱신한다: 미리 갱신하면 서버가 꺼진 동안 갱신이 실패해 가만히 있던 화면이 "연결할 수 없어요"로 바뀐다
    // 갱신이 실패해 토큰이 없으면 연결하지 않는다 (로그아웃되어 RealtimeSync가 곧 사라진다)
    beforeConnect: async () => {
      if (needsRefresh) {
        needsRefresh = false;
        lastRefreshAt = Date.now();
        await refreshSession();
      }
      const token = session.getAccessToken();
      if (!token) {
        await client.deactivate();
        return;
      }
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => {
      // 끊기면 서버 구독도 사라지므로 처음부터 다시 구독한다
      calendarSubscriptions.clear();
      client.subscribe(DISPLAY_QUEUE, handleDisplaySignal);
      syncCalendarSubscriptions();
      // 구독하기 전(또는 끊긴 동안)의 변경은 신호로 오지 않아서 한 번 다시 불러온다
      pending.groups = pending.allEvents = true;
      schedule();
    },
    // 서버가 연결을 거절했다 (토큰 만료 등): 다음 연결 전에 토큰을 갱신한다
    onStompError: () => {
      if (Date.now() - lastRefreshAt > REFRESH_COOLDOWN_MS) needsRefresh = true;
    },
  });

  // 그룹 목록을 새로 받으면 구독을 맞춘다. 새 캘린더를 구독했으면 그 사이의 일정 변경을 놓쳤을 수 있어 일정도 다시 부른다
  const stopWatchingGroups = queryClient.getQueryCache().subscribe((event) => {
    if (event.type !== "updated" || event.action.type !== "success" || event.query.queryHash !== GROUPS_HASH) return;
    if (syncCalendarSubscriptions()) {
      pending.allEvents = true;
      schedule();
    }
  });

  client.activate();

  return () => {
    stopped = true;
    stopWatchingGroups();
    if (timer) clearTimeout(timer);
    void client.deactivate();
  };
}

// 일정 조회 중 다시 불러올 것: 기간 조회(["event","range",from,to])는 바뀐 기간과 겹칠 때만, 나머지(일정 목록·상세)는 모두
function touches(query: Query, ranges: Array<[number, number]>): boolean {
  const [, kind, fromIso, toIso] = query.queryKey;
  if (kind !== "range" || typeof fromIso !== "string" || typeof toIso !== "string") return true;
  const from = Date.parse(fromIso);
  const to = Date.parse(toIso);
  return ranges.some(([start, end]) => start <= to && from <= end);
}

function parse<T>(message: IMessage): T | null {
  try {
    return JSON.parse(message.body) as T;
  } catch {
    return null;
  }
}

function toTime(iso: string | null): number | null {
  if (!iso) return null;
  const time = Date.parse(iso);
  return Number.isNaN(time) ? null : time;
}
