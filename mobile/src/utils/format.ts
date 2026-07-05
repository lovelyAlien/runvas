// 360 → "6:00/km", 270 → "4:30/km"
export function formatPace(secondsPerKm: number): string {
  const minutes = Math.floor(secondsPerKm / 60);
  const seconds = secondsPerKm % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}/km`;
}

export function formatDistance(meters: number): string {
  if (meters < 1000) return `${meters}m`;
  return `${(meters / 1000).toFixed(2)}km`;
}

// 시간 길이는 항상 seconds 단위로 보관하고(docs/geo-conventions.md), 표시할 때만 분/시간으로 환산한다.
export function formatDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  if (minutes === 0) return '-';
  if (minutes < 60) return `${minutes}분`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`;
}

// 2026-07-05 → "2026.07.05" — 게시글 작성 화면의 기본 제목([후기] 코스명 - 날짜)에 사용.
export function formatDateYYYYMMDD(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}.${m}.${d}`;
}
