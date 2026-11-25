"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import type { FormEvent } from "react";
import { errorMessage } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { PasswordField } from "@/shared/ui/PasswordField";
import { TextField } from "@/shared/ui/TextField";
import { login } from "../api";
import { nextQuery, safeNext } from "../hooks";

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));
  const joined = searchParams.get("joined") === "1";
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: () => router.replace(next),
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    mutation.mutate({ email: String(form.get("email")), password: String(form.get("password")) });
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">다시 오신 걸 환영해요</h1>
      {joined && <p className="rounded-md bg-green-50 p-3 text-sm text-green-800">가입했어요. 로그인해 주세요.</p>}
      <TextField label="이메일" name="email" type="email" autoComplete="email" maxLength={255} required onChange={() => mutation.reset()} />
      <PasswordField label="비밀번호" name="password" autoComplete="current-password" required onChange={() => mutation.reset()} />
      {mutation.isError && (
        <p role="alert" className="text-sm text-red-600">
          {errorMessage(mutation.error)}
        </p>
      )}
      <Button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? "로그인 중…" : "로그인"}
      </Button>
      <p className="text-center text-sm text-zinc-500">
        처음이신가요?{" "}
        <Link href={`/signup${nextQuery(next)}`} className="text-brand hover:underline">
          회원가입
        </Link>
      </p>
    </form>
  );
}
