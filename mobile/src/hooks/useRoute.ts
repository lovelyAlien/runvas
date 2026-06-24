import { useState, useCallback, useMemo } from 'react';
import { getDistance } from 'geolib';
import { Coordinate, RouteStats, RoutePoint, GeoBounds } from '../types';

const RUNNING_PACE_MIN_PER_KM = 6; // 6분/km 기준

export function useRoute() {
  // 사용자가 탭한 웨이포인트 (마커 표시용)
  const [waypoints, setWaypoints] = useState<Coordinate[]>([]);
  // 각 구간의 실제 도보 경로 좌표 (T-MAP 응답, 폴리라인용)
  const [segments, setSegments] = useState<Coordinate[][]>([]);

  // 폴리라인에 사용할 전체 경로 좌표 (segments를 펼침)
  const routeCoords = useMemo(() => segments.flat(), [segments]);

  // 첫 번째 포인트 추가 (경로 없음, 마커만)
  const addFirstPoint = useCallback((coord: Coordinate) => {
    setWaypoints([coord]);
    setSegments([]);
  }, []);

  // 두 번째 이후 포인트 추가 (T-MAP API 응답 좌표와 함께)
  const addSegment = useCallback((waypoint: Coordinate, segmentCoords: Coordinate[]) => {
    setWaypoints((prev) => [...prev, waypoint]);
    setSegments((prev) => [...prev, segmentCoords]);
  }, []);

  // 마지막 포인트 + 마지막 구간 제거
  const undoLast = useCallback(() => {
    setWaypoints((prev) => prev.slice(0, -1));
    setSegments((prev) => prev.slice(0, -1));
  }, []);

  // 전체 초기화
  const clearRoute = useCallback(() => {
    setWaypoints([]);
    setSegments([]);
  }, []);

  // 실제 경로 거리 기반 통계 (직선 아닌 실제 보행 거리). 시간은 seconds 단위로 보관합니다
  // (docs/geo-conventions.md: 시간 길이 단위 = seconds).
  const stats: RouteStats = useMemo(() => {
    if (routeCoords.length < 2) {
      return { distanceMeters: 0, estimatedDurationSeconds: 0, pointCount: waypoints.length };
    }
    let total = 0;
    for (let i = 1; i < routeCoords.length; i++) {
      total += getDistance(routeCoords[i - 1], routeCoords[i]);
    }
    const estimatedDurationSeconds = Math.round((total / 1000) * RUNNING_PACE_MIN_PER_KM * 60);
    return { distanceMeters: total, estimatedDurationSeconds, pointCount: waypoints.length };
  }, [routeCoords, waypoints.length]);

  // GPX 내보내기·향후 코스 저장(POST /api/courses)에 필요한 RoutePoint[] (sequence 0부터 연속).
  const toRoutePoints = useCallback((): RoutePoint[] => {
    return routeCoords.map((coord, index) => ({ ...coord, sequence: index }));
  }, [routeCoords]);

  // path를 포함하는 최소 지도 영역. 오늘은 호출하는 곳이 없지만 향후 코스 저장 시 필요합니다.
  const getBounds = useCallback((): GeoBounds | null => {
    if (routeCoords.length === 0) return null;
    let minLat = routeCoords[0].latitude;
    let maxLat = routeCoords[0].latitude;
    let minLng = routeCoords[0].longitude;
    let maxLng = routeCoords[0].longitude;
    for (const { latitude, longitude } of routeCoords) {
      minLat = Math.min(minLat, latitude);
      maxLat = Math.max(maxLat, latitude);
      minLng = Math.min(minLng, longitude);
      maxLng = Math.max(maxLng, longitude);
    }
    return {
      southWest: { latitude: minLat, longitude: minLng },
      northEast: { latitude: maxLat, longitude: maxLng },
    };
  }, [routeCoords]);

  return {
    waypoints,
    routeCoords,
    stats,
    addFirstPoint,
    addSegment,
    undoLast,
    clearRoute,
    toRoutePoints,
    getBounds,
  };
}
