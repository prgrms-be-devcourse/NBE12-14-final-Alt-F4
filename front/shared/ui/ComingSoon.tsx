// 아직 만들지 않은 화면의 자리. 담당자가 화면을 만들면 이 컴포넌트를 지운다
export function ComingSoon({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 px-4 py-24 text-center">
      <h1 className="text-xl font-semibold text-zinc-800">{title}</h1>
      <p className="text-sm text-muted">{description ?? "준비 중인 화면이에요."}</p>
    </div>
  );
}
