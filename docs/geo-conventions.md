# 좌표 및 지도 규칙

## 좌표계

모든 좌표는 WGS84를 사용합니다.

## 좌표 필드명

JSON에서는 다음 필드명을 사용합니다.

```json
{
  "latitude": 37.5665,
  "longitude": 126.978
}
```

약어 `lat`, `lng`, `lon`은 API 본문에서 사용하지 않습니다.

단, 지도 범위 조회 API의 query parameter는 URL 길이를 고려해 `swLat`, `swLng`, `neLat`, `neLng`를 사용합니다.

## 좌표 순서

배열 좌표 표현을 사용해야 하는 외부 라이브러리에서는 각 프로젝트의 어댑터 계층에서 변환합니다.

Runvas 공통 기준에서는 항상 다음 순서를 의미합니다.

1. latitude
2. longitude

## 단위

| 항목 | 단위 |
| --- | --- |
| 거리 | meters |
| 시간 길이 | seconds |
| 속도 | meters per second |
| 페이스 | seconds per kilometer |

## 지도 및 경로 제공자

MVP에서는 모바일 WebView 안에서 Kakao Maps JavaScript SDK로 지도를 렌더링하고, 마커와 폴리라인을 표시합니다.

두 좌표 사이의 실제 보행 경로 폴리라인은 T-MAP 보행자 경로 탐색 API를 우선 사용합니다.
요금, 사용량, 정책 이슈가 있으면 카카오모빌리티 경로 API로 교체할 수 있습니다.

경로 제공자를 교체하더라도 Runvas 공통 기준의 `path`, `distanceMeters`, `estimatedDurationSeconds`, `bounds` 필드 의미는 유지합니다.

## 거리 및 예상 소요 시간

MVP에서 백엔드는 경로 좌표를 기준으로 자체 거리 또는 예상 시간을 계산하지 않습니다.

모바일은 경로 탐색 API가 반환한 실제 보행 경로의 거리와 예상 시간을 사용합니다.
경로 탐색 API가 예상 시간을 제공하지 않으면 지도 렌더링에 사용하는 폴리라인 길이와 기본 페이스를 기준으로 표시값을 만들 수 있습니다.

서버는 코스 저장 또는 수정 요청에 포함된 `distanceMeters`, `estimatedDurationSeconds`, `path`, `bounds`를 저장합니다.
서버는 값의 필수 여부, 타입, 좌표 범위, 최소/최대 거리 같은 기본 제한만 검증합니다.

서버 응답의 `distanceMeters`와 `estimatedDurationSeconds`는 저장된 코스를 목록, 상세, GPX 다운로드에서 일관되게 표시하기 위한 값입니다.

## Bounds

코스의 `bounds`는 `path`에 포함된 모든 Route Point를 감싸는 최소 사각형입니다.

- `southWest.latitude`: path 내 최소 latitude
- `southWest.longitude`: path 내 최소 longitude
- `northEast.latitude`: path 내 최대 latitude
- `northEast.longitude`: path 내 최대 longitude

모바일은 코스 저장 또는 수정 요청에 `bounds`를 포함합니다.
백엔드는 `bounds`가 `path` 전체를 포함하는지 검증할 수 있습니다.

## 지도 범위 조회

`GET /courses`는 요청 bounds와 코스 bounds가 겹치는 공개 코스를 반환합니다.

MVP에서는 코스의 실제 선분이 화면 영역과 교차하는지까지는 검사하지 않습니다.
