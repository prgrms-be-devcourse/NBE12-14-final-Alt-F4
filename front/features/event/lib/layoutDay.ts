// 시간 격자의 배치 계산 (순수 함수)

// 최소 높이 20px(한 시간 48px)에 해당하는 분. 겹침은 "보이는 끝"으로 계산한다 (짧은 일정이 화면에서 겹치지 않게)
export const MIN_VISIBLE_MINUTES = 25;

export type Segment<T> = { item: T; key: string; start: number; end: number }; // 그날 0시부터의 벽시계 분
export type PlacedSegment<T> = Segment<T> & { column: number; columns: number; span: number };

const visibleEnd = (segment: { start: number; end: number }) => Math.max(segment.end, segment.start + MIN_VISIBLE_MINUTES);
const overlaps = (a: { start: number; end: number }, b: { start: number; end: number }) => a.start < visibleEnd(b) && b.start < visibleEnd(a);

// 하루치 시간 일정 배치
// 정렬(시작 → 긴 것 → key) → 이어서 겹치는 묶음 → 빈 첫 열 → 오른쪽 열에 겹치는 것이 없으면 넓히기
export function layoutDay<T>(segments: Segment<T>[]): PlacedSegment<T>[] {
  const sorted = [...segments].sort((a, b) => a.start - b.start || b.end - a.end || a.key.localeCompare(b.key));
  const result: PlacedSegment<T>[] = [];
  let cluster: Segment<T>[] = [];
  let clusterEnd = -1;

  const flush = () => {
    const columnEnds: number[] = [];
    const placed = cluster.map((segment) => {
      let column = columnEnds.findIndex((end) => end <= segment.start);
      if (column === -1) column = columnEnds.length;
      columnEnds[column] = visibleEnd(segment);
      return { ...segment, column };
    });
    const columns = columnEnds.length;
    for (const segment of placed) {
      let span = 1;
      for (let column = segment.column + 1; column < columns; column += 1) {
        if (placed.some((other) => other.column === column && overlaps(other, segment))) break;
        span += 1;
      }
      result.push({ ...segment, columns, span });
    }
  };

  for (const segment of sorted) {
    if (cluster.length > 0 && segment.start >= clusterEnd) {
      flush();
      cluster = [];
      clusterEnd = -1;
    }
    cluster.push(segment);
    clusterEnd = Math.max(clusterEnd, visibleEnd(segment));
  }
  if (cluster.length > 0) flush();
  return result;
}

export const MAX_ALL_DAY_LANES = 3;

export type Bar<T> = { item: T; key: string; first: number; last: number }; // 그 주(보기) 안의 날 번호 (0부터)
export type PlacedBar<T> = Bar<T> & { lane: number; hidden: boolean };

// 종일 줄 배치: 시작 → 긴 것 → key 순으로, 걸치는 모든 날에 비어 있는 첫 줄
// 4번째 줄부터는 숨기고, 날마다 그날을 덮는 숨은 막대 수를 "+N개"로 보인다
export function layoutAllDay<T>(bars: Bar<T>[], dayCount: number): { bars: PlacedBar<T>[]; hiddenPerDay: number[]; lanes: number } {
  const sorted = [...bars].sort((a, b) => a.first - b.first || b.last - b.first - (a.last - a.first) || a.key.localeCompare(b.key));
  const laneEnds: number[] = [];
  const placed = sorted.map((bar) => {
    let lane = laneEnds.findIndex((end) => end < bar.first);
    if (lane === -1) lane = laneEnds.length;
    laneEnds[lane] = bar.last;
    return { ...bar, lane, hidden: lane >= MAX_ALL_DAY_LANES };
  });
  const hiddenPerDay = Array.from({ length: dayCount }, (_, day) => placed.filter((bar) => bar.hidden && bar.first <= day && day <= bar.last).length);
  return { bars: placed, hiddenPerDay, lanes: Math.min(laneEnds.length, MAX_ALL_DAY_LANES) };
}
