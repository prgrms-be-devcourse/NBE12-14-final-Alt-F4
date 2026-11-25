// 불러오는 중 표시. fullScreen이면 화면 가운데
export function Spinner({ fullScreen = false, label = "불러오는 중" }: { fullScreen?: boolean; label?: string }) {
  const spinner = (
    <div role="status" className="flex items-center gap-2 text-sm text-zinc-500">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-line-strong border-t-brand" />
      {label}
    </div>
  );
  if (!fullScreen) return spinner;
  return <div className="flex flex-1 items-center justify-center py-24">{spinner}</div>;
}
