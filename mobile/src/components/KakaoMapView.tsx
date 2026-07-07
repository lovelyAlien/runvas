import React, { useRef, useImperativeHandle, forwardRef, useEffect } from 'react';
import { AppState, AppStateStatus, StyleSheet } from 'react-native';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import { Coordinate, GeoBounds, RoutePoint } from '../types';

// 카카오 로그인용 REST API 키(EXPO_PUBLIC_KAKAO_APP_KEY)와는 다른 키다.
// 카카오 지도 JS SDK(dapi.kakao.com/v2/maps/sdk.js)는 JavaScript 키만 인식하며,
// REST API 키를 넣으면 지도 타일이 그려지지 않고 빈 화면으로 남는다.
const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';

export interface KakaoMapViewRef {
  moveToLocation: (coord: Coordinate) => void;
  addWaypoint: (coord: Coordinate, index: number) => void; // 마커만 추가
  addRouteSegment: (coords: Coordinate[]) => void; // 실제 경로 폴리라인 추가
  fitBounds: (bounds: GeoBounds) => void; // 카메라를 주어진 영역에 맞춤 (저장된 코스 보기, 코스 상세 보기용)
  getBounds: () => Promise<GeoBounds>; // 현재 지도에 보이는 영역 조회 (코스 조회 버튼용)
  previewCourse: (path: RoutePoint[]) => void; // 조회한 공개 코스의 경로를 지도에 미리보기로 표시 (카메라 이동 없음)
  showCourseWaypoints: (waypoints: RoutePoint[]) => void; // 코스 상세 보기 시 경로 순서(번호 핀)를 표시
  clearCoursePreview: () => void; // 코스 탐색 종료/재시작 시 미리보기 경로와 순서 핀을 지움
  undoLast: () => void;
  clearMap: () => void;
}

interface Props {
  onMapPress: (coord: Coordinate) => void;
  onMapReady?: () => void; // 지도가 로드 완료된 뒤에만 안전하게 메시지를 보낼 수 있다
}

function buildMapHtml(appKey: string): string {
  return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body, #map { width: 100%; height: 100%; }
    .current-location-pin {
      position: relative;
      width: 28px;
      height: 28px;
      border-radius: 50% 50% 50% 0;
      background: #2563EB;
      border: 3px solid #FFFFFF;
      box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
      transform: rotate(-45deg);
    }
    .current-location-pin::after {
      content: '';
      position: absolute;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #FFFFFF;
      left: 7px;
      top: 7px;
    }
    .current-location-label {
      position: absolute;
      top: 26px;
      left: 50%;
      transform: translateX(-50%);
      padding: 3px 7px;
      border-radius: 999px;
      background: rgba(17, 24, 39, 0.82);
      color: #FFFFFF;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      font-size: 11px;
      font-weight: 700;
      white-space: nowrap;
    }
    .current-location-wrapper {
      position: relative;
      width: 64px;
      height: 48px;
      display: flex;
      justify-content: center;
      pointer-events: none;
    }
    .waypoint-pin {
      width: 32px;
      height: 32px;
      border-radius: 50% 50% 50% 0;
      border: 3px solid #fff;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      transform: rotate(-45deg);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .waypoint-pin.start { background: #16a34a; }
    .waypoint-pin.mid   { background: #2563EB; }
    .waypoint-pin.end   { background: #dc2626; }
    .waypoint-pin .num {
      transform: rotate(45deg);
      color: #fff;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      font-size: 13px;
      font-weight: 800;
      line-height: 1;
    }
  </style>
</head>
<body>
  <div id="map"></div>
  <script type="text/javascript"
    src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false">
  </script>
  <script>
    var map;
    var waypointMarkers = []; // 사용자가 탭한 마커
    var currentLocationOverlay; // 현재 위치 표시용 핀
    var segmentPolylines = []; // 각 구간별 폴리라인
    var routePolyline;         // 전체 경로 폴리라인
    var previewPolyline;       // 코스 조회로 선택한 공개 코스 미리보기 폴리라인 (사용자가 그리는 경로와 별개)
    var courseWaypointMarkers = []; // 코스 상세 보기 시 표시하는 경로 순서 번호 핀

    kakao.maps.load(function() {
      var container = document.getElementById('map');
      map = new kakao.maps.Map(container, {
        center: new kakao.maps.LatLng(37.5665, 126.9780),
        level: 5
      });

      routePolyline = new kakao.maps.Polyline({
        strokeWeight: 5,
        strokeColor: '#2563EB',
        strokeOpacity: 0.9,
        strokeStyle: 'solid'
      });
      routePolyline.setMap(map);

      // 지도 탭 → RN으로 좌표 전송
      kakao.maps.event.addListener(map, 'click', function(mouseEvent) {
        var latlng = mouseEvent.latLng;
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'MAP_PRESS',
          latitude: latlng.getLat(),
          longitude: latlng.getLng()
        }));
      });

      // WebView 초기 레이아웃이 안정되기 전에 지도가 생성되면 카카오맵 SDK가
      // 실제보다 작은 컨테이너 크기를 캐싱해 이후 재렌더링 시 하단에 흰 공백이 남는다.
      // 레이아웃이 안정된 뒤 한 번 더 relayout을 걸어 캐시된 크기를 갱신한다.
      setTimeout(function() { map.relayout(); }, 300);

      // 지도 로드 완료 알림 — RN이 이걸 받기 전에 보낸 메시지는 map이 아직 없어 무시될 수 있다.
      window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'MAP_READY' }));
    });

    function handleRNMessage(data) {
      var msg = JSON.parse(data);

      if (msg.type === 'ADD_WAYPOINT') {
        var latlng = new kakao.maps.LatLng(msg.latitude, msg.longitude);
        var idx = msg.index; // 1-based
        var colorClass = idx === 1 ? 'start' : 'mid';
        var overlay = new kakao.maps.CustomOverlay({
          position: latlng,
          yAnchor: 1,
          content: '<div class="waypoint-pin ' + colorClass + '"><span class="num">' + idx + '</span></div>'
        });
        overlay.setMap(map);
        waypointMarkers.push(overlay);

      } else if (msg.type === 'ADD_ROUTE_SEGMENT') {
        // T-MAP에서 받은 상세 경로 좌표로 폴리라인 추가
        var coords = msg.coords; // [{latitude, longitude}, ...]
        var segPath = coords.map(function(c) {
          return new kakao.maps.LatLng(c.latitude, c.longitude);
        });

        // 새 구간 폴리라인 생성
        var seg = new kakao.maps.Polyline({
          path: segPath,
          strokeWeight: 5,
          strokeColor: '#2563EB',
          strokeOpacity: 0.9,
          strokeStyle: 'solid'
        });
        seg.setMap(map);
        segmentPolylines.push(seg);

      } else if (msg.type === 'UNDO_LAST') {
        // 마지막 마커 제거
        var lastMarker = waypointMarkers.pop();
        if (lastMarker) lastMarker.setMap(null);
        // 마지막 구간 폴리라인 제거
        var lastSeg = segmentPolylines.pop();
        if (lastSeg) lastSeg.setMap(null);

      } else if (msg.type === 'FIT_BOUNDS') {
        // 캐시된 컨테이너 크기 기준으로 bounds를 맞추면 하단이 잘려 흰 공백이 생길 수 있어
        // 크기를 다시 계산한 뒤 bounds를 적용한다.
        map.relayout();
        var sw = new kakao.maps.LatLng(msg.swLat, msg.swLng);
        var ne = new kakao.maps.LatLng(msg.neLat, msg.neLng);
        var llBounds = new kakao.maps.LatLngBounds(sw, ne);
        map.setBounds(llBounds);

      } else if (msg.type === 'GET_BOUNDS') {
        var currentBounds = map.getBounds();
        var boundsSw = currentBounds.getSouthWest();
        var boundsNe = currentBounds.getNorthEast();
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'BOUNDS_RESULT',
          swLat: boundsSw.getLat(),
          swLng: boundsSw.getLng(),
          neLat: boundsNe.getLat(),
          neLng: boundsNe.getLng()
        }));

      } else if (msg.type === 'PREVIEW_COURSE') {
        if (previewPolyline) {
          previewPolyline.setMap(null);
          previewPolyline = null;
        }
        courseWaypointMarkers.forEach(function(m) { m.setMap(null); });
        courseWaypointMarkers = [];
        var previewPath = msg.coords.map(function(c) {
          return new kakao.maps.LatLng(c.latitude, c.longitude);
        });
        previewPolyline = new kakao.maps.Polyline({
          path: previewPath,
          strokeWeight: 3,
          strokeColor: '#F97316',
          strokeOpacity: 0.9,
          strokeStyle: 'solid'
        });
        previewPolyline.setMap(map);

      } else if (msg.type === 'SHOW_COURSE_WAYPOINTS') {
        courseWaypointMarkers.forEach(function(m) { m.setMap(null); });
        courseWaypointMarkers = [];
        msg.waypoints.forEach(function(wp, i) {
          var latlng = new kakao.maps.LatLng(wp.latitude, wp.longitude);
          var colorClass = i === 0 ? 'start' : (i === msg.waypoints.length - 1 ? 'end' : 'mid');
          var overlay = new kakao.maps.CustomOverlay({
            position: latlng,
            yAnchor: 1,
            content: '<div class="waypoint-pin ' + colorClass + '"><span class="num">' + (i + 1) + '</span></div>'
          });
          overlay.setMap(map);
          courseWaypointMarkers.push(overlay);
        });

      } else if (msg.type === 'CLEAR_COURSE_PREVIEW') {
        if (previewPolyline) {
          previewPolyline.setMap(null);
          previewPolyline = null;
        }
        courseWaypointMarkers.forEach(function(m) { m.setMap(null); });
        courseWaypointMarkers = [];

      } else if (msg.type === 'CLEAR') {
        waypointMarkers.forEach(function(m) { m.setMap(null); });
        waypointMarkers = [];
        segmentPolylines.forEach(function(p) { p.setMap(null); });
        segmentPolylines = [];

      } else if (msg.type === 'MOVE_TO') {
        // 캐시된 컨테이너 크기 기준으로 확대/이동하면 하단이 잘려 흰 공백이 생길 수 있어
        // 크기를 다시 계산한 뒤 이동한다.
        map.relayout();
        var currentLatLng = new kakao.maps.LatLng(msg.latitude, msg.longitude);
        map.setCenter(currentLatLng);
        map.setLevel(3);

        if (currentLocationOverlay) {
          currentLocationOverlay.setMap(null);
        }

        currentLocationOverlay = new kakao.maps.CustomOverlay({
          position: currentLatLng,
          yAnchor: 1,
          content: '<div class="current-location-wrapper"><div class="current-location-pin"></div><div class="current-location-label">내 위치</div></div>'
        });
        currentLocationOverlay.setMap(map);

      } else if (msg.type === 'RELAYOUT') {
        // 안드로이드 권한 다이얼로그 등 다른 Activity가 잠깐 떴다가 사라지면
        // WebView의 하드웨어 가속 서페이스가 무효화되어 지도 하단이 흰 공백으로 남을 수 있다.
        // 앱이 다시 포그라운드로 돌아올 때 강제로 relayout해 복구한다.
        map.relayout();
      }
    }

    document.addEventListener('message', function(e) { handleRNMessage(e.data); });
    window.addEventListener('message', function(e) { handleRNMessage(e.data); });
  </script>
</body>
</html>
  `.trim();
}

const KakaoMapView = forwardRef<KakaoMapViewRef, Props>(
  ({ onMapPress, onMapReady }, ref) => {
    const webViewRef = useRef<WebView>(null);
    const boundsResolverRef = useRef<((bounds: GeoBounds) => void) | null>(null);

    const postMessage = (msg: object) => {
      webViewRef.current?.postMessage(JSON.stringify(msg));
    };

    // 위치 권한 다이얼로그 등으로 앱이 background/inactive를 거쳐 다시 active로 돌아오면
    // 안드로이드 WebView의 하드웨어 가속 서페이스가 무효화되어 지도 하단이 흰 공백으로
    // 남을 수 있다. 포그라운드 복귀 시점에 지도를 강제로 relayout해 복구한다.
    useEffect(() => {
      let previousState: AppStateStatus = AppState.currentState;
      const subscription = AppState.addEventListener('change', (nextState: AppStateStatus) => {
        const wasBackgrounded = previousState !== 'active';
        if (wasBackgrounded && nextState === 'active') {
          postMessage({ type: 'RELAYOUT' });
        }
        previousState = nextState;
      });
      return () => subscription.remove();
    }, []);

    useImperativeHandle(ref, () => ({
      moveToLocation: (coord: Coordinate) => {
        postMessage({ type: 'MOVE_TO', ...coord });
      },
      addWaypoint: (coord: Coordinate, index: number) => {
        postMessage({ type: 'ADD_WAYPOINT', ...coord, index });
      },
      addRouteSegment: (coords: Coordinate[]) => {
        postMessage({ type: 'ADD_ROUTE_SEGMENT', coords });
      },
      fitBounds: (bounds: GeoBounds) => {
        postMessage({
          type: 'FIT_BOUNDS',
          swLat: bounds.southWest.latitude,
          swLng: bounds.southWest.longitude,
          neLat: bounds.northEast.latitude,
          neLng: bounds.northEast.longitude,
        });
      },
      getBounds: () => {
        return new Promise<GeoBounds>((resolve, reject) => {
          const timeoutId = setTimeout(() => {
            boundsResolverRef.current = null;
            reject(new Error('지도 범위를 가져오지 못했습니다.'));
          }, 5000);
          boundsResolverRef.current = (bounds) => {
            clearTimeout(timeoutId);
            resolve(bounds);
          };
          postMessage({ type: 'GET_BOUNDS' });
        });
      },
      previewCourse: (path: RoutePoint[]) => {
        postMessage({
          type: 'PREVIEW_COURSE',
          coords: path.map((p) => ({ latitude: p.latitude, longitude: p.longitude })),
        });
      },
      showCourseWaypoints: (waypoints: RoutePoint[]) => {
        postMessage({
          type: 'SHOW_COURSE_WAYPOINTS',
          waypoints: waypoints.map((w) => ({ latitude: w.latitude, longitude: w.longitude })),
        });
      },
      clearCoursePreview: () => {
        postMessage({ type: 'CLEAR_COURSE_PREVIEW' });
      },
      undoLast: () => {
        postMessage({ type: 'UNDO_LAST' });
      },
      clearMap: () => {
        postMessage({ type: 'CLEAR' });
      },
    }));

    const handleMessage = (event: WebViewMessageEvent) => {
      try {
        const data = JSON.parse(event.nativeEvent.data);
        if (data.type === 'MAP_PRESS') {
          onMapPress({ latitude: data.latitude, longitude: data.longitude });
        } else if (data.type === 'MAP_READY') {
          onMapReady?.();
        } else if (data.type === 'BOUNDS_RESULT') {
          boundsResolverRef.current?.({
            southWest: { latitude: data.swLat, longitude: data.swLng },
            northEast: { latitude: data.neLat, longitude: data.neLng },
          });
          boundsResolverRef.current = null;
        }
      } catch (_) {}
    };

    return (
      <WebView
        ref={webViewRef}
        style={styles.map}
        source={{ html: buildMapHtml(KAKAO_JS_KEY) }}
        originWhitelist={['*']}
        javaScriptEnabled
        onMessage={handleMessage}
      />
    );
  }
);

export default KakaoMapView;

const styles = StyleSheet.create({
  map: { flex: 1 },
});
