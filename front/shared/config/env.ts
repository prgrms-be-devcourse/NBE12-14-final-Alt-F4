// 빌드할 때 값이 박힌다 (.env.development / .env.production).
// CI는 값 없이 빌드하므로, 없으면 로컬 주소를 쓰고 실패하지 않는다
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
