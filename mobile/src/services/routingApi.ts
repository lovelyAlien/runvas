// 보행자 경로 조회(POST /api/routes/pedestrian) — runvas/backend의 RoutingController와 연동됨
// (MapScreen.tsx의 handleMapPress에서 accessToken과 함께 호출). T-Map API 키는 백엔드에만 있고
// 모바일은 더 이상 T-Map을 직접 호출하지 않는다 (mobile/docs/implementations 참고).
import { Coordinate, RoutePoint } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function fetchPedestrianRoute(
  start: Coordinate,
  end: Coordinate,
  accessToken: string
): Promise<RoutePoint[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/routes/pedestrian`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ start, end }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { path } = (await response.json()) as { path: RoutePoint[] };
  return path;
}
