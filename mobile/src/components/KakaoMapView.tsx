import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import { StyleSheet } from 'react-native';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import { Coordinate, GeoBounds } from '../types';

// 카카오 로그인용 REST API 키(EXPO_PUBLIC_KAKAO_APP_KEY)와는 다른 키다.
// 카카오 지도 JS SDK(dapi.kakao.com/v2/maps/sdk.js)는 JavaScript 키만 인식하며,
// REST API 키를 넣으면 지도 타일이 그려지지 않고 빈 화면으로 남는다.
const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';

export interface PublicCourseMarker {
  id: string;
  title: string;
  centerLat: number;
  centerLng: number;
}

export interface KakaoMapViewRef {
  moveToLocation: (coord: Coordinate) => void;
  addWaypoint: (coord: Coordinate, index: number) => void;
  addRouteSegment: (coords: Coordinate[]) => void;
  fitBounds: (bounds: GeoBounds) => void;
  undoLast: () => void;
  clearMap: () => void;
  showPublicCourses: (courses: PublicCourseMarker[]) => void;
  clearPublicCourses: () => void;
  setBrowseMode: (enabled: boolean) => void;
}

interface Props {
  onMapPress: (coord: Coordinate) => void;
  onMapReady?: () => void;
  onBoundsChange?: (bounds: GeoBounds) => void;
  onCourseMarkerPress?: (courseId: string) => void;
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
    .course-marker-pin {
      width: 28px;
      height: 28px;
      border-radius: 50% 50% 50% 0;
      background: #ea580c;
      border: 3px solid #fff;
      box-shadow: 0 4px 12px rgba(234,88,12,0.4);
      transform: rotate(-45deg);
      cursor: pointer;
    }
    .course-bubble {
      background: #fff;
      border: 1.5px solid #ea580c;
      border-radius: 8px;
      padding: 6px 10px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.18);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      font-size: 12px;
      max-width: 180px;
    }
    .course-bubble-title {
      font-weight: 700;
      color: #111;
      margin-bottom: 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .course-bubble-btn {
      display: block;
      padding: 3px 8px;
      background: #ea580c;
      color: #fff;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      text-align: center;
    }
    .course-wrapper {
      position: relative;
      width: 36px;
      height: 36px;
      display: flex;
      justify-content: center;
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
    var waypointMarkers = [];
    var currentLocationOverlay;
    var segmentPolylines = [];
    var routePolyline;
    var publicCourseOverlays = [];
    var publicCourses = []; // 숫자 인덱스로만 참조 — 사용자 입력값을 onclick 속성에 직접 삽입하지 않기 위함
    var activeBubbleOverlay = null;
    var isBrowseMode = false;

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

      kakao.maps.event.addListener(map, 'click', function(mouseEvent) {
        if (isBrowseMode) return;
        var latlng = mouseEvent.latLng;
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'MAP_PRESS',
          latitude: latlng.getLat(),
          longitude: latlng.getLng()
        }));
      });

      // 지도 이동 완료 후 현재 범위를 RN으로 전달
      kakao.maps.event.addListener(map, 'idle', function() {
        var bounds = map.getBounds();
        var sw = bounds.getSouthWest();
        var ne = bounds.getNorthEast();
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'MAP_BOUNDS_CHANGE',
          swLat: sw.getLat(),
          swLng: sw.getLng(),
          neLat: ne.getLat(),
          neLng: ne.getLng()
        }));
      });

      window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'MAP_READY' }));
    });

    function closeActiveBubble() {
      if (activeBubbleOverlay) {
        activeBubbleOverlay.setMap(null);
        activeBubbleOverlay = null;
      }
    }

    function onCourseMarkerClick(index) {
      var course = publicCourses[index];
      if (!course) return;
      closeActiveBubble();
      var latlng = new kakao.maps.LatLng(course.centerLat, course.centerLng);
      var rawTitle = course.title.length > 20 ? course.title.substring(0, 20) + '...' : course.title;

      // DOM 조작으로 말풍선 생성 — textContent 사용으로 사용자 입력값이 HTML로 파싱되지 않음
      var bubble = document.createElement('div');
      bubble.className = 'course-bubble';
      var titleEl = document.createElement('div');
      titleEl.className = 'course-bubble-title';
      titleEl.textContent = rawTitle;
      var btn = document.createElement('div');
      btn.className = 'course-bubble-btn';
      btn.textContent = '자세히 보기';
      btn.onclick = function() {
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'COURSE_MARKER_PRESS',
          courseId: course.id
        }));
      };
      bubble.appendChild(titleEl);
      bubble.appendChild(btn);

      activeBubbleOverlay = new kakao.maps.CustomOverlay({
        position: latlng,
        yAnchor: 3.2,
        content: bubble
      });
      activeBubbleOverlay.setMap(map);
    }

    function handleRNMessage(data) {
      var msg = JSON.parse(data);

      if (msg.type === 'ADD_WAYPOINT') {
        var latlng = new kakao.maps.LatLng(msg.latitude, msg.longitude);
        var idx = msg.index;
        var colorClass = idx === 1 ? 'start' : 'mid';
        var overlay = new kakao.maps.CustomOverlay({
          position: latlng,
          yAnchor: 1,
          content: '<div class="waypoint-pin ' + colorClass + '"><span class="num">' + idx + '</span></div>'
        });
        overlay.setMap(map);
        waypointMarkers.push(overlay);

      } else if (msg.type === 'ADD_ROUTE_SEGMENT') {
        var coords = msg.coords;
        var segPath = coords.map(function(c) {
          return new kakao.maps.LatLng(c.latitude, c.longitude);
        });
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
        var lastMarker = waypointMarkers.pop();
        if (lastMarker) lastMarker.setMap(null);
        var lastSeg = segmentPolylines.pop();
        if (lastSeg) lastSeg.setMap(null);

      } else if (msg.type === 'FIT_BOUNDS') {
        var sw = new kakao.maps.LatLng(msg.swLat, msg.swLng);
        var ne = new kakao.maps.LatLng(msg.neLat, msg.neLng);
        var llBounds = new kakao.maps.LatLngBounds(sw, ne);
        map.setBounds(llBounds);

      } else if (msg.type === 'CLEAR') {
        waypointMarkers.forEach(function(m) { m.setMap(null); });
        waypointMarkers = [];
        segmentPolylines.forEach(function(p) { p.setMap(null); });
        segmentPolylines = [];

      } else if (msg.type === 'MOVE_TO') {
        var currentLatLng = new kakao.maps.LatLng(msg.latitude, msg.longitude);
        map.setCenter(currentLatLng);
        map.setLevel(3);
        if (currentLocationOverlay) currentLocationOverlay.setMap(null);
        currentLocationOverlay = new kakao.maps.CustomOverlay({
          position: currentLatLng,
          yAnchor: 1,
          content: '<div class="current-location-wrapper"><div class="current-location-pin"></div><div class="current-location-label">내 위치</div></div>'
        });
        currentLocationOverlay.setMap(map);

      } else if (msg.type === 'SET_BROWSE_MODE') {
        isBrowseMode = msg.enabled;
        if (msg.enabled && map) {
          var modeBounds = map.getBounds();
          var modeSw = modeBounds.getSouthWest();
          var modeNe = modeBounds.getNorthEast();
          window.ReactNativeWebView.postMessage(JSON.stringify({
            type: 'MAP_BOUNDS_CHANGE',
            swLat: modeSw.getLat(),
            swLng: modeSw.getLng(),
            neLat: modeNe.getLat(),
            neLng: modeNe.getLng()
          }));
        }

      } else if (msg.type === 'SHOW_PUBLIC_COURSES') {
        publicCourseOverlays.forEach(function(o) { o.setMap(null); });
        publicCourseOverlays = [];
        publicCourses = msg.courses; // 전역 배열에 저장, onclick에서 숫자 인덱스로만 참조
        closeActiveBubble();

        msg.courses.forEach(function(course, i) {
          var latlng = new kakao.maps.LatLng(course.centerLat, course.centerLng);
          // 인라인 onclick에 숫자만 삽입 — 사용자 입력값(title, id)은 HTML 속성에 직접 넣지 않음
          var pinOverlay = new kakao.maps.CustomOverlay({
            position: latlng,
            yAnchor: 1,
            content: '<div class="course-wrapper"><div class="course-marker-pin" onclick="onCourseMarkerClick(' + i + ')"></div></div>'
          });
          pinOverlay.setMap(map);
          publicCourseOverlays.push(pinOverlay);
        });

      } else if (msg.type === 'CLEAR_PUBLIC_COURSES') {
        publicCourseOverlays.forEach(function(o) { o.setMap(null); });
        publicCourseOverlays = [];
        closeActiveBubble();
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
  ({ onMapPress, onMapReady, onBoundsChange, onCourseMarkerPress }, ref) => {
    const webViewRef = useRef<WebView>(null);

    const postMessage = (msg: object) => {
      webViewRef.current?.postMessage(JSON.stringify(msg));
    };

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
      undoLast: () => {
        postMessage({ type: 'UNDO_LAST' });
      },
      clearMap: () => {
        postMessage({ type: 'CLEAR' });
      },
      showPublicCourses: (courses: PublicCourseMarker[]) => {
        postMessage({ type: 'SHOW_PUBLIC_COURSES', courses });
      },
      clearPublicCourses: () => {
        postMessage({ type: 'CLEAR_PUBLIC_COURSES' });
      },
      setBrowseMode: (enabled: boolean) => {
        postMessage({ type: 'SET_BROWSE_MODE', enabled });
      },
    }));

    const handleMessage = (event: WebViewMessageEvent) => {
      try {
        const data = JSON.parse(event.nativeEvent.data);
        if (data.type === 'MAP_PRESS') {
          onMapPress({ latitude: data.latitude, longitude: data.longitude });
        } else if (data.type === 'MAP_READY') {
          onMapReady?.();
        } else if (data.type === 'MAP_BOUNDS_CHANGE') {
          onBoundsChange?.({
            southWest: { latitude: data.swLat, longitude: data.swLng },
            northEast: { latitude: data.neLat, longitude: data.neLng },
          });
        } else if (data.type === 'COURSE_MARKER_PRESS') {
          onCourseMarkerPress?.(data.courseId);
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
