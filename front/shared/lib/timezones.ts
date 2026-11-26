import { sameTimeZone } from "./date";

// 시간대 검색 칸의 데이터 (브라우저 Intl로 만든 한국어 이름)
// - 브라우저 목록(Intl.supportedValuesOf)에는 "UTC"가 없고, 옛 이름(Asia/Calcutta 등)이 나온다
// - 한국어 도시 이름(서울·도쿄·런던)은 Intl이 주지 않아 검색용 별칭 표를 둔다
// - 약 420개 × formatter 3개와 나라 표를 처음 열 때 한 번 만들고 모듈 변수에 두고 다시 쓴다
//   (모듈 변수는 이벤트 핸들러에서 부르는 함수 안에서만 채운다)

export type ZoneEntry = {
  id: string; // IANA 이름 (예: America/New_York)
  title: string; // 첫 줄 (예: "뉴욕, 미국")
  description: string; // 둘째 줄 (예: "미 동부 시간 - 뉴욕 (GMT-05:00)")
  display: string; // 닫힌 칸 표시 (예: "(GMT-05:00) 미 동부 시간 - 뉴욕")
  offsetMinutes: number;
  search: string; // 소문자 검색어
};

const ALIASES: Array<[string, string]> = [
  ["Asia/Seoul", "서울"],
  ["Asia/Tokyo", "도쿄"],
  ["Asia/Shanghai", "상하이 베이징"],
  ["Asia/Hong_Kong", "홍콩"],
  ["Asia/Taipei", "타이베이"],
  ["Asia/Singapore", "싱가포르"],
  ["Asia/Bangkok", "방콕"],
  ["Asia/Ho_Chi_Minh", "호찌민 하노이"],
  ["Asia/Kolkata", "뭄바이 델리 콜카타"],
  ["Asia/Dubai", "두바이"],
  ["Europe/London", "런던"],
  ["Europe/Paris", "파리"],
  ["Europe/Berlin", "베를린"],
  ["Europe/Rome", "로마"],
  ["Europe/Madrid", "마드리드"],
  ["Europe/Moscow", "모스크바"],
  ["America/Los_Angeles", "LA 엘에이 샌프란시스코"],
  ["America/Chicago", "시카고"],
  ["America/Toronto", "토론토"],
  ["Australia/Sydney", "시드니"],
];

type Formatters = { generic: Intl.DateTimeFormat; city: Intl.DateTimeFormat; offset: Intl.DateTimeFormat };
const formatterCache = new Map<string, Formatters | null>();
let countryByZone: Map<string, string> | null = null;
let entriesCache: { day: string; entries: ZoneEntry[] } | null = null;

function formattersOf(zone: string): Formatters | null {
  if (!formatterCache.has(zone)) {
    try {
      const make = (timeZoneName: "longGeneric" | "shortGeneric" | "shortOffset") => new Intl.DateTimeFormat("ko", { timeZone: zone, timeZoneName });
      formatterCache.set(zone, { generic: make("longGeneric"), city: make("shortGeneric"), offset: make("shortOffset") });
    } catch {
      formatterCache.set(zone, null); // 모르는 이름
    }
  }
  return formatterCache.get(zone) ?? null;
}

const partOf = (formatter: Intl.DateTimeFormat, at: Date) => formatter.formatToParts(at).find((part) => part.type === "timeZoneName")?.value ?? "";

// "GMT+9" "GMT+5:30" "GMT-6" "GMT" → { text: "GMT+09:00", minutes }
function offsetOf(raw: string): { text: string; minutes: number } {
  const match = /^GMT(?:([+-])(\d{1,2})(?::(\d{2}))?)?$/.exec(raw);
  if (!match || !match[1]) return { text: "GMT+00:00", minutes: 0 };
  const sign = match[1] === "-" ? -1 : 1;
  const hours = Number(match[2]);
  const minutes = Number(match[3] ?? 0);
  return { text: `GMT${match[1]}${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`, minutes: sign * (hours * 60 + minutes) };
}

// 나라 코드 AA~ZZ를 돌며 시간대 → 나라 이름 표를 만든다 (Intl.Locale의 getTimeZones, 없으면 timeZones 속성)
function countries(): Map<string, string> {
  if (countryByZone) return countryByZone;
  const map = new Map<string, string>();
  let names: Intl.DisplayNames | null = null;
  try {
    names = new Intl.DisplayNames("ko", { type: "region" });
  } catch {
    names = null;
  }
  for (let a = 65; a <= 90; a += 1) {
    for (let b = 65; b <= 90; b += 1) {
      const code = String.fromCharCode(a, b);
      let zones: string[] | undefined;
      try {
        const locale = new Intl.Locale(`und-${code}`) as Intl.Locale & { getTimeZones?: () => string[]; timeZones?: string[] };
        zones = locale.getTimeZones?.() ?? locale.timeZones;
      } catch {
        zones = undefined;
      }
      if (!zones?.length) continue;
      const name = names?.of(code);
      if (!name || name === code) continue; // 옛 코드(AN·FQ 등)는 이름이 없다
      for (const zone of zones) if (!map.has(zone)) map.set(zone, name);
    }
  }
  countryByZone = map;
  return map;
}

function aliasOf(zone: string): string {
  return ALIASES.filter(([id]) => sameTimeZone(id, zone)).map(([, alias]) => alias).join(" ");
}

// 시간대 하나의 항목. at: 시차 기준 날짜 (서머타임에 따라 바뀐다)
export function zoneEntry(zone: string, at: Date, withCountry = false): ZoneEntry {
  const formatters = formattersOf(zone);
  if (!formatters) {
    return { id: zone, title: zone, description: zone, display: zone, offsetMinutes: 0, search: zone.toLowerCase() };
  }
  const offset = offsetOf(partOf(formatters.offset, at));
  if (zone === "UTC" || zone === "Etc/UTC") {
    const description = `(${offset.text})`;
    return {
      id: zone,
      title: "협정 세계시(UTC)",
      description,
      display: `${description} 협정 세계시(UTC)`,
      offsetMinutes: 0,
      search: `utc 협정 세계시 gmt ${offset.text.toLowerCase()} gmt+0`,
    };
  }
  const generic = partOf(formatters.generic, at);
  const shortGeneric = partOf(formatters.city, at);
  const city = shortGeneric.startsWith("GMT") ? "" : shortGeneric.replace(/ 시간$/, "");
  const country = withCountry ? (countries().get(zone) ?? "") : "";
  const title = city ? (country && !city.includes(country) ? `${city}, ${country}` : city) : generic || zone;
  const description = city ? `${generic} - ${city} (${offset.text})` : `${generic} (${offset.text})`;
  const display = city ? `(${offset.text}) ${generic} - ${city}` : `(${offset.text}) ${generic}`;
  const short = offset.text.replace(/^GMT([+-])0?(\d+):00$/, "gmt$1$2").toLowerCase(); // gmt+9
  const searchParts = [title, description, zone.replace(/[_/]/g, " "), offset.text.slice(3), short, aliasOf(zone)].join(" ").toLowerCase();
  return { id: zone, title, description, display, offsetMinutes: offset.minutes, search: `${searchParts} ${searchParts.replace(/\s+/g, "")}` };
}

// 전체 목록 (UTC 맨 앞, 나머지는 시차 → 제목 가나다순). 같은 날에는 다시 만들지 않는다. 이벤트 핸들러에서 부른다
export function allZoneEntries(at: Date): ZoneEntry[] | null {
  if (typeof Intl.supportedValuesOf !== "function") return null;
  const day = at.toDateString();
  if (entriesCache?.day === day) return entriesCache.entries;
  const zones = Intl.supportedValuesOf("timeZone").filter((zone) => zone !== "UTC");
  const entries = zones
    .map((zone) => zoneEntry(zone, at, true))
    .sort((a, b) => a.offsetMinutes - b.offsetMinutes || a.title.localeCompare(b.title, "ko"));
  entriesCache = { day, entries: [zoneEntry("UTC", at), ...entries] };
  return entriesCache.entries;
}

export function matchesZone(entry: ZoneEntry, query: string): boolean {
  const text = query.trim().toLowerCase();
  if (!text) return true;
  return entry.search.includes(text) || entry.search.includes(text.replace(/\s+/g, ""));
}
