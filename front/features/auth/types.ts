export type LoginRequest = { email: string; password: string };

// 비밀번호: 8~64자, 영문·숫자·특수문자 각 1개 이상 / 닉네임: 50자 이하
export type SignupRequest = { email: string; password: string; nickname: string };

export type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number };

export type Me = { id: number; email: string | null; nickname: string };
