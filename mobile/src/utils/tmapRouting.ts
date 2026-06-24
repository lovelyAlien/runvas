import { Coordinate } from '../types';

const TMAP_APP_KEY = process.env.EXPO_PUBLIC_TMAP_APP_KEY ?? '';
const TMAP_PEDESTRIAN_URL = `https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&appKey=${TMAP_APP_KEY}`;

/**
 * T-MAP 보행자 경로 탐색 API를 호출하여 실제 도보 경로 좌표 배열을 반환합니다.
 * 실패 시 출발지-목적지 직선 좌표를 반환합니다 (폴백).
 */
export async function fetchPedestrianRoute(
  start: Coordinate,
  end: Coordinate
): Promise<Coordinate[]> {
  try {
    const response = await fetch(TMAP_PEDESTRIAN_URL, {
      method: 'POST',
      headers: {
        appKey: TMAP_APP_KEY,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        startX: String(start.longitude),
        startY: String(start.latitude),
        endX: String(end.longitude),
        endY: String(end.latitude),
        startName: '출발지',
        endName: '도착지',
        reqCoordType: 'WGS84GEO',
        resCoordType: 'WGS84GEO',
      }),
    });

    if (!response.ok) {
      const errBody = await response.text().catch(() => '');
      console.warn('T-MAP API 응답 바디:', errBody);
      throw new Error(`T-MAP API 오류: ${response.status}`);
    }

    const data = await response.json();

    // GeoJSON FeatureCollection에서 LineString 좌표만 추출
    const coords: Coordinate[] = [];
    for (const feature of data.features ?? []) {
      if (feature.geometry?.type === 'LineString') {
        for (const [lng, lat] of feature.geometry.coordinates) {
          coords.push({ latitude: lat, longitude: lng });
        }
      }
    }

    if (coords.length === 0) throw new Error('경로 좌표 없음');
    return coords;
  } catch (error) {
    console.warn('T-MAP 경로 탐색 실패, 직선으로 대체:', error);
    // 폴백: 직선
    return [start, end];
  }
}
