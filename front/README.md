# Live-Calendar 프론트엔드

해당 README 문서에서는 프론트엔드에 해당하는 내용만 기술합니다.

프로젝트 전체에 관련된 내용은 [프로젝트 README](../README.md) 에서 확인합니다.

## 기술 스택

Next 16.4.6 (App Router, Cache Components), React 19.3.0, TypeScript, Tailwind CSS 4.3.3, TanStack Query 5, date-fns 4, @date-fns/tz 1.5, lucide-react (아이콘)

> Next 16은 예전 버전과 API가 다릅니다. 코드를 쓰기 전에 `node_modules/next/dist/docs`의 해당 문서를 먼저 확인합니다 ([AGENTS.md](AGENTS.md)).

## 로컬 실행

```bash
npm ci
npm run dev
```

- 백엔드(`http://localhost:8080`)가 켜져 있어야 합니다. API 주소는 `.env.development`에 있습니다.
- **3000번 포트를 써야 합니다.** 백엔드 CORS·WebSocket 허용 주소가 `http://localhost:3000`입니다. `docker compose up`으로 Grafana(3000번)까지 띄우면 `next dev`가 다른 포트로 떠서 로그인이 막힙니다. 프론트 작업 때는 DB만 띄웁니다: `docker compose up -d db`
- 배포 빌드는 `.env.production`(`https://dev-api.livecalendar.cc`)을 씁니다. `NEXT_PUBLIC_*` 값은 **빌드할 때 박히므로** 이 두 파일을 커밋합니다. 비밀 값은 넣지 않습니다.

## PR 전에 확인

CI와 같은 순서로 로컬에서 먼저 돌립니다.

```bash
npm run lint
npm run build
```

`npm run dev`에서 되던 화면도 `npm run build`에서 실패할 수 있습니다 (아래 Suspense 규칙).

## 폴더 구조

```text
front/
├─ app/              라우팅만. page.tsx는 화면 컴포넌트를 불러 두기만 한다
│  ├─ _components/   여러 그룹 레이아웃이 같이 쓰는 것 (AppHeader)
│  ├─ (auth)/        로그인 전 화면: login, signup
│  ├─ (public)/      로그인 없이 볼 화면: explore, shares/[shareId], invite/[token]
│  └─ (app)/         로그인이 필요한 화면: calendar, calendars/[calendarId]/settings, rooms, settings
├─ features/         기능별 코드 (백엔드 도메인 이름 그대로)
│  ├─ auth/          로그인·가입·로그아웃·AuthGuard
│  ├─ calendar/      그룹·캘린더·카테고리, 사이드바, 캘린더 설정
│  ├─ event/         일정 조회·만들기·수정·삭제, 캘린더 화면(툴바·월간 보기·일정 창)
│  ├─ room/          조율방 목록·만들기·상세
│  ├─ share/         공유 페이지 만들기
│  └─ sync/          실시간 동기화 (STOMP 연결, 신호 받으면 다시 조회)
└─ shared/           어디서나 쓰는 것
   ├─ api/           client.ts(API 호출), session.ts(토큰·갱신), errors.ts(ApiError)
   ├─ ui/            Button, TextField, PasswordField, fields(Textarea·Checkbox), Modal, ConfirmDialog, Dropdown, MenuButton, TimezoneCombobox, usePopover, Spinner, ComingSoon
   ├─ lib/           cn, date
   └─ config/        env
```

### 새 기능 추가하는 법
1. `features/<도메인>/`을 만든다 (예: `room`, `share`, `chat`). 안에는 필요한 것만 둔다.
   - `types.ts`: 백엔드 응답·요청 타입 (실제 컨트롤러 DTO 기준, 노션과 다르면 코드가 맞다)
   - `api.ts`: `api()`로 부르는 함수
   - `hooks.ts`: TanStack Query 훅과 그 기능의 query key (`xxxQueryKeys`)
   - `components/`: 화면 컴포넌트 (`"use client"`)
2. `app/(app)/<경로>/page.tsx`의 `ComingSoon`을 화면 컴포넌트로 바꾼다.
3. `features`끼리는 필요한 만큼만 가져다 쓴다. `shared`는 `features`를 가져오지 않는다.

## 규칙

### 페이지와 Suspense
- `app/**/page.tsx`는 서버 컴포넌트로 얇게 두고 `metadata`와 화면 컴포넌트만 둔다.
- `next.config.ts`의 `cacheComponents: true` 때문에, 클라이언트 컴포넌트가 `useSearchParams`·`usePathname`·`useParams`를 **`<Suspense>` 밖에서 쓰면 `next build`가 실패**합니다. 세 그룹 레이아웃(`(auth)`, `(public)`, `(app)`)이 이미 `<Suspense>`로 감싸 두었습니다. 새 그룹 레이아웃을 만들면 똑같이 감쌉니다.
- 이벤트 핸들러 안에서만 주소가 필요하면 훅 대신 `window.location`을 읽습니다.
- 오류 화면: `app/(app)/error.tsx`(헤더는 남김), `app/global-error.tsx`. Next 16의 props는 `{ error, retry }`입니다.
- 페이지 제목은 `metadata.title`에 화면 이름만 씁니다. 뒤의 "| Live Calendar"는 루트 레이아웃의 `template`이 붙입니다.
- `(app)` 아래 `page.tsx`에는 `export const instant = false;`를 둡니다. 로그인 확인 전에는 `AuthGuard`가 페이지 대신 로딩을 그려서, 없으면 개발 모드에서 "Could not validate that a segment in your UI has instant navigation" 경고가 뜹니다 (빌드에는 영향 없음).

### API 호출
- 모든 호출은 `shared/api/client.ts`의 `api()`로 합니다. 토큰을 붙이고, 만료(401 `UNAUTHORIZED`)면 한 번 갱신해 다시 보내고, 실패는 `ApiError(status, code, message, fieldErrors)`로 던집니다.
- 화면 문구는 `errorMessage(error)`로 (백엔드가 준 한국어 `detail`, 서버에 닿지 못하면 연결 문구).
- 입력 칸 하나의 서버 검증 오류는 `fieldError(error, "name")`, 특정 오류 코드는 `hasCode(error, "GROUP_NOT_FOUND")`.
- 칸 검사는 `<form noValidate>` + 보내기 전 직접 검사로 합니다 (브라우저 말풍선과 문구가 두 가지로 나오지 않게).
- 서버 데이터는 TanStack Query로 받고, 바꾼 뒤에는 `invalidateQueries`로 다시 조회합니다.

### 선택 칸·메뉴 (Dropdown·TimezoneCombobox·MenuButton)
- 네이티브 `<select>`는 쓰지 않습니다. 값 고르기는 `Dropdown`, 시간대는 `TimezoneCombobox`(한국어 검색), 동작 메뉴는 `MenuButton`(`role="menu"`)입니다.
- 셋 다 제어형(`value`·`onChange`)이고, `name`을 주면 숨긴 입력을 그려 `FormData`로 읽힙니다. 보이는 검색 칸에는 `name`이 없습니다.
- 목록은 `usePopover`로 **그 자리에** `popover="manual"`로 띄웁니다(맨 위 층이라 창의 스크롤 상자에 잘리지 않음). 포털로 body에 옮기면 모달 창 안에서 누를 수 없습니다.
- 목록이 열린 채 Esc는 목록만 닫고, 창 바깥 클릭도 첫 번은 목록만 닫습니다.
- 시간대 데이터는 브라우저 `Intl`(`shared/lib/timezones.ts`)이고, 한국어 도시 이름(서울·도쿄 등)은 작은 별칭 표로 찾습니다.

### 창 (Modal·ConfirmDialog)
- `shared/ui/Modal`은 네이티브 `<dialog>`입니다. 포커스 가두기·뒤 화면 막기는 브라우저가 하고, 닫으면 열기 전 위치로 포커스를 돌려줍니다.
- 처음 포커스는 `data-autofocus`를 붙인 요소입니다. React의 `autoFocus`는 창이 열리기 전에 실행돼 창 안에서는 듣지 않습니다.
- 저장 중에는 `busy`를 넘겨 Esc·바깥 클릭으로 닫히지 않게 합니다.
- `window.confirm`·`prompt`·`alert` 대신 `ConfirmDialog`나 창을 씁니다.

### 로그인 상태
- Access 토큰은 메모리(`shared/api/session.ts`)에만 둡니다. 새로고침하면 Refresh 쿠키로 다시 받습니다 (`restoreSession`).
- Refresh 토큰은 쓸 때마다 바뀌므로 갱신은 `refreshSession()` 하나로만 합니다 (동시 요청·여러 탭을 한 번으로 모음).
- Refresh가 401일 때만 로그아웃합니다. 네트워크 오류·5xx면 `unreachable` 상태로 두고 "다시 시도"를 보여 줍니다.
- 인증 API(`/api/v1/auth/*`)에는 Access 토큰을 붙이지 않습니다. 만료된 토큰이 붙으면 서버가 401을 내서 로그아웃이 안 됩니다.
- 로그인·로그아웃은 `BroadcastChannel`로 다른 탭에 알립니다. 로그인 상태가 끝나면 `Providers`가 TanStack Query 캐시를 비웁니다.
- 화면 보호는 `AuthGuard`(클라이언트)뿐이고 실제 보호는 백엔드 401입니다. Refresh 쿠키가 API 도메인 전용이라 Next 서버(`proxy.ts`)는 로그인 상태를 알 수 없습니다.

### 시간
- 서버 시각은 UTC 문자열입니다. 조회할 때 `from`·`to`는 `toISOString()`, `timezone`은 `browserTimeZone()`을 보냅니다.
- 종일 일정은 `startAt`·`endAt`이 UTC 자정이고 `endAt`은 끝 다음 날입니다. 시간대로 바꾸지 말고 `features/event/lib/eventDays.ts`를 씁니다.
- 반복 일정은 회차마다 `id`가 같습니다. 구분은 `occurrenceKey()`(`id:occurrenceStart`).
- **일정 폼의 날짜·시간은 일정 시간대의 벽시계 값**입니다 (`EventFormValues.timezone`). 보낼 때 `@date-fns/tz`의 `TZDate`로 순간을 만들고, 열 때 거꾸로 바꿉니다. 새 일정의 기본 시간대는 `browserTimeZone()`, 고칠 때는 원래 일정의 값입니다. 월간 보기는 브라우저 시간대로 그립니다.
- 요일은 날짜 문자열로 계산합니다 (`parse("yyyy-MM-dd")`). 순간의 `getDay()`는 브라우저 시간대라 다른 시간대 일정에서 하루 어긋납니다.
- 시간대 목록(`TimezoneCombobox`)에는 브라우저가 주지 않는 "UTC"를 넣고, 같은 시간대인지는 `sameTimeZone()`(정규화 비교)으로 봅니다.

### 반복 규칙 (`features/event/lib/recurrence.ts`)
- 화면에서는 `Repeat`(없음·목록 항목·맞춤·기존 규칙 유지)로 다루고, 보낼 때 RRULE로 바꿉니다.
- **반복·시작 날짜·종일·시간대를 안 바꿨으면 원래 규칙 문자열을 그대로 보냅니다.** 서버는 정리한 문자열끼리 비교해서 다르면 예외 회차를 지웁니다. "매주"도 `FREQ=WEEKLY` 그대로 보냅니다.
- 요일을 고르는 주 단위 규칙에는 `WKST=SU`를 붙입니다. ical4j는 WKST가 없으면 서버 JVM 언어 설정의 주 시작을 씁니다.
- 읽어 온 UNTIL은 날짜를 바꾸지 않으면 그대로 보냅니다 ("이 일정과 이후"로 자른 시리즈는 "고른 회차 1초 전"이라 다시 만들면 그 회차가 되살아납니다).
- 시작일은 규칙의 첫 회차여야 합니다(서버 400). 그래서 맞춤 창의 시작 요일은 끌 수 없고, 토·일에는 "주중 매일"이 없습니다.
- COUNT 반복의 "이 일정과 이후"(첫 회차가 아닐 때)는 남은 횟수를 계산하지 않고, 종료를 다시 고르게 막습니다.
- "모든 일정"에서 시간·종일·시간대·반복을 바꾸면 저장 전에 "회차가 원래대로 돌아가요" 확인 창을 띄웁니다.

### 캘린더 보기 (일·주·월·연도·일정)
- 보기와 날짜는 URL(`view`·`date`)에 둡니다. 보기별 제목·이동은 `features/event/lib/viewRange.ts`입니다.
- 주·일 보기의 시간 격자는 **벽시계 분**(시×60+분)으로 위치를 정합니다(서머타임 날에도 시각 줄과 맞게). 겹침·종일 줄 배치는 `lib/layoutDay.ts`의 순수 함수입니다.
- 일정 보기는 1년을 **90일씩**(백엔드 최대 93일) 받고, 경계를 걸친 일정은 `occurrenceKey`로 중복을 뺍니다.
- 단축키: D·W·M·Y·A 보기, T 오늘, J·N 다음, K·P 이전, C 새 일정. 키 위치(`event.code`)로 비교해 한글 입력 상태에서도 됩니다. 보기 메뉴에서 끌 수 있습니다(브라우저에 저장).
- "＋ 만들기" 메뉴(일정·캘린더·그룹·일정 조율방)와 미니 달력은 사이드바 맨 위입니다. 만들기의 날짜는 지금 고른 날짜(URL `date`)입니다.

### 디자인 기준 (조용한 파랑)
- 색·둥글기·그림자는 `app/globals.css`의 토큰으로 씁니다: 주 색 `brand`(#2F5BEA)·`brand-soft`·`brand-ink`, 앱 바탕 `background`(#F7F8FA), 선 `line`·`line-strong`, 입력 바탕 `field`, 보조 글자 `muted`, 둥글기 `rounded-field`(10)·`rounded-card`(20), 그림자 `shadow-card`·`shadow-raised`·`shadow-pop`·`shadow-dialog`. `blue-*`를 새로 쓰지 않습니다 (토요일 글자 `text-blue-500`만 예외).
- 화면 틀: 왼쪽 72px 아이콘 메뉴(`AppRail`) + 회색 바탕 위 흰 카드(`cardClass`, `shared/ui/card.ts`).
- 버튼은 알약 모양 `Button`(primary·secondary·ghost·danger, md·sm). 링크를 버튼처럼 보이게 할 때는 `buttonClass()`, 아이콘만 있는 버튼은 `IconButton`(`label`이 `aria-label`·`title`).
- 입력 칸은 `inputClass`(높이 40) 또는 `fieldBaseClass` + 높이. `cn`은 클래스를 합치지 않으므로 같은 속성(높이·테두리 색) 클래스를 둘 붙이지 않습니다.
- 아이콘은 `lucide-react`. 장식용은 `aria-hidden`을 붙입니다.
- 일정 막대·블록은 캘린더 색을 옅게 깐 바탕(`tintOf`)과 진한 글자(`inkOf`), 시간 일정 칩은 색 점 + 시작 시각 + 제목입니다.

### 색과 화면 상태
- 사용자 색은 `style={{ backgroundColor }}`로 넣습니다. Tailwind 4는 `bg-[${color}]` 같은 동적 클래스를 만들지 못합니다.
- 사이드바에서 끈 캘린더는 서버에 사람별로 저장합니다 (`PATCH /api/v1/calendars/{id}/display`, 그룹 목록의 `isVisible`). 다른 기기에도 실시간으로 반영됩니다.

### 실시간 동기화 (`features/sync/`)
- 로그인한 화면에서 `ws://{API}/ws`(STOMP)에 연결합니다. 연결할 때마다 그때의 Access 토큰을 붙이고, 서버가 연결을 거절하면(토큰 만료 등) 토큰을 갱신해 다시 연결합니다. 끊기면 3~6초마다 다시 연결합니다.
- 구독: `/user/queue/display`(내 화면 설정) + 그룹 목록의 캘린더마다 `/topic/calendars/{id}`. 그룹 목록이 바뀌면 구독도 맞춥니다.
- 신호에는 내용이 없습니다. 받으면 0.2~1초 뒤 한 번에 다시 조회합니다 (일정 변경은 보고 있는 기간과 겹칠 때만). 연결·재연결 직후에도 한 번 다시 조회합니다.
- 새 화면에서 서버 데이터를 바꾸면 지금처럼 mutation의 `onSuccess`에서 다시 조회합니다. 다른 사람·다른 기기 반영은 신호가 맡습니다.

### lint (CI에서 에러)
Next 16의 React Compiler 규칙이 켜져 있습니다.
- effect 안에서 바로 `setState` 하지 않기 (비동기 결과로 바꾸는 것은 괜찮음)
- 렌더 중에 ref나 모듈 변수 바꾸지 않기 (토큰은 이벤트 핸들러·fetch에서만 바꾼다)

## 화면과 API

| 화면 | 경로 | API | 상태 |
|---|---|---|---|
| 첫 화면 | `/` | `POST /api/v1/auth/refresh` | 로그인돼 있으면 `/calendar` |
| 로그인 / 회원가입 | `/login`, `/signup` | `/api/v1/auth/login`, `/signup` | ✅ |
| 캘린더 (월간) + 사이드바 | `/calendar?view=month&date=yyyy-MM-dd` | `GET /groups`, `GET /events`, `POST /calendars`, 그룹 `POST`·`PATCH`·`DELETE /groups` | ✅ 일·주·월·연도·일정 보기, 미니 달력, 만들기 메뉴, 단축키, 캘린더 만들기(그룹·색·설명), 그룹 추가·이름·삭제, 숨김 기억 |
| 일정 만들기·보기·수정·삭제 | 캘린더 화면의 창 | `POST /events`, `GET /events/{id}`, `PATCH`·`DELETE /events/{id}?scope&occurrenceStart` | ✅ 반복 범위(이 일정만·이후·전체), 구글식 폼, 일정 시간대, 날짜에 맞춘 반복 목록·맞춤 반복 |
| 캘린더 설정 | `/calendars/[calendarId]/settings` | `GET`·`PATCH`·`DELETE /calendars/{id}`, `PATCH /calendars/{id}/group`, `DELETE /calendars/{id}/subscription`, 카테고리 `POST`·`PATCH`·`DELETE /categories`, `POST /shares` | ✅ 정보 수정(시간대 목록), 그룹 옮기기, 카테고리, 공유 페이지 만들기, 삭제·나가기(구독만) |
| 둘러보기, 공유 상세 | `/explore`, `/shares/[shareId]` | shares (API 없음) | 준비 중 |
| 일정 조율 | `/rooms`, `/rooms/new`, `/rooms/[roomId]` | `GET`·`POST /rooms`, `GET /rooms/{roomId}` | ✅ 목록·만들기·상세 (공유 일정·빈 시간·확정·채팅·초대는 API 없음) |
| 초대 확인 | `/invite/[token]` | invite-links (API 없음) | 준비 중 |
| 내 설정 | `/settings` | users (수정·탈퇴 API 없음) | 준비 중 |
