"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { errorMessage, fieldError, hasCode } from "@/shared/api/errors";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { PasswordField } from "@/shared/ui/PasswordField";
import { TextField } from "@/shared/ui/TextField";
import { login, signup } from "../api";
import { nextQuery, safeNext } from "../hooks";
import { isValidPassword, passwordRules } from "../password";
import type { SignupRequest } from "../types";

// 가입은 됐는데 이어진 로그인이 실패한 경우 (다시 가입하면 "이미 사용 중"이 나오므로 로그인 화면으로 보낸다)
class JoinedButLoginFailed extends Error {}

// 가입은 토큰을 주지 않아서 가입 → 로그인 순서로 부른다
async function signupAndLogin(request: SignupRequest) {
  await signup(request);
  try {
    await login({ email: request.email, password: request.password });
  } catch {
    throw new JoinedButLoginFailed();
  }
}

type LocalErrors = { email?: string; password?: string; nickname?: string };

export function SignupForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [password, setPassword] = useState("");
  const [localErrors, setLocalErrors] = useState<LocalErrors>({});
  const mutation = useMutation({
    mutationFn: signupAndLogin,
    onSuccess: () => router.replace(next),
    onError: (error) => {
      if (error instanceof JoinedButLoginFailed) router.replace(`/login?joined=1${nextQuery(next).replace("?", "&")}`);
    },
  });

  // 칸 오류: 보내기 전 검사 → 서버 검증(400) → 이미 쓰는 이메일(409)
  const emailError =
    localErrors.email ?? fieldError(mutation.error, "email") ?? (hasCode(mutation.error, "DUPLICATE_EMAIL") ? errorMessage(mutation.error) : undefined);
  const passwordError = localErrors.password ?? fieldError(mutation.error, "password");
  const nicknameError = localErrors.nickname ?? fieldError(mutation.error, "nickname");
  const hasFieldError = Boolean(emailError || passwordError || nicknameError);

  // 칸을 고치면 그 칸 오류를 지운다
  function clearErrors(field: keyof LocalErrors) {
    setLocalErrors((previous) => ({ ...previous, [field]: undefined }));
    if (mutation.isError) mutation.reset();
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const request = {
      email: String(form.get("email") ?? "").trim(),
      password: String(form.get("password") ?? ""),
      nickname: String(form.get("nickname") ?? "").trim(),
    };
    const errors: LocalErrors = {
      ...(!request.email && { email: "이메일을 입력해 주세요." }),
      ...(!isValidPassword(request.password) && { password: "비밀번호 규칙을 확인해 주세요." }),
      ...(!request.nickname && { nickname: "닉네임을 입력해 주세요." }),
    };
    setLocalErrors(errors);
    if (errors.email || errors.password || errors.nickname) return;
    mutation.mutate(request);
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Live Calendar 시작하기</h1>
      <TextField label="이메일" name="email" type="email" autoComplete="email" maxLength={255} error={emailError} onChange={() => clearErrors("email")} />
      <PasswordField
        label="비밀번호"
        name="password"
        autoComplete="new-password"
        maxLength={64}
        value={password}
        onChange={(event) => {
          setPassword(event.target.value);
          clearErrors("password");
        }}
        error={passwordError}
        hint={
          <ul className="flex flex-wrap gap-x-3 gap-y-0.5 text-xs" aria-label="비밀번호 규칙">
            {passwordRules.map((rule) => {
              const passed = rule.test(password);
              return (
                <li key={rule.label} className={cn(passed ? "text-green-700" : "text-zinc-400")}>
                  {passed ? "✓" : "·"} {rule.label}
                </li>
              );
            })}
          </ul>
        }
      />
      <TextField label="닉네임" name="nickname" maxLength={50} error={nicknameError} onChange={() => clearErrors("nickname")} />
      {mutation.isError && !hasFieldError && !(mutation.error instanceof JoinedButLoginFailed) && (
        <p role="alert" className="text-sm text-red-600">
          {errorMessage(mutation.error)}
        </p>
      )}
      <Button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? "가입 중…" : "가입하기"}
      </Button>
      <p className="text-center text-sm text-zinc-500">
        이미 계정이 있나요?{" "}
        <Link href={`/login${nextQuery(next)}`} className="text-brand hover:underline">
          로그인
        </Link>
      </p>
    </form>
  );
}
