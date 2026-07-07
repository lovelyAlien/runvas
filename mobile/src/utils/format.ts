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

// ISO 8601 문자열을 "방금 전" ~ "n일 전" 상대 시각으로, 그 이후는 "YYYY.MM.DD"로 표시한다.
export function formatRelativeTime(isoString: string): string {
  const elapsedSeconds = Math.max(0, (Date.now() - new Date(isoString).getTime()) / 1000);

  if (elapsedSeconds < 60) return '방금 전';
  if (elapsedSeconds < 3600) return `${Math.floor(elapsedSeconds / 60)}분 전`;
  if (elapsedSeconds < 86400) return `${Math.floor(elapsedSeconds / 3600)}시간 전`;
  if (elapsedSeconds < 86400 * 7) return `${Math.floor(elapsedSeconds / 86400)}일 전`;

  const date = new Date(isoString);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}
