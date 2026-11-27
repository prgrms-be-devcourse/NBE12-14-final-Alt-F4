"use client";

// 루트 레이아웃까지 실패했을 때. 전역 CSS가 없으므로 스타일을 직접 넣는다
export default function GlobalError({ retry }: { error: unknown; retry: () => void }) {
  return (
    <html lang="ko">
      <body style={{ fontFamily: "system-ui, sans-serif", textAlign: "center", padding: "96px 16px", color: "#18181b" }}>
        <title>문제가 생겼어요 | Live Calendar</title>
        <h1 style={{ fontSize: 18, fontWeight: 600 }}>문제가 생겼어요.</h1>
        <p style={{ fontSize: 14, color: "#52525b" }}>잠시 후 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={() => retry()}
          style={{ marginTop: 12, padding: "8px 12px", borderRadius: 6, border: "none", background: "#2563eb", color: "white", fontSize: 14 }}
        >
          다시 시도
        </button>
      </body>
    </html>
  );
}
