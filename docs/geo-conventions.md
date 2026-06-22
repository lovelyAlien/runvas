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

Runvas 공통 계약에서는 항상 다음 순서를 의미합니다.

1. latitude
2. longitude

## 단위

| 항목 | 단위 |
| --- | --- |
| 거리 | meters |
| 시간 길이 | seconds |
| 속도 | meters per second |
| 페이스 | seconds per kilometer |

## 거리 계산

MVP 기준 총 거리는 인접한 Route Point 사이의 Haversine distance 합으로 계산합니다.

백엔드는 저장 시 `distanceMeters`를 계산하고 응답합니다.

모바일은 코스 작성 중 사용자 피드백을 위해 임시 거리 계산을 할 수 있지만, 저장 후 표시되는 최종 값은 서버 응답을 기준으로 합니다.

## 예상 소요 시간 계산

MVP에서 서버가 저장하고 응답하는 `estimatedDurationSeconds`는 공통 기준값입니다.
기본 페이스는 6분 30초/km입니다.

```text
estimatedDurationSeconds = distanceMeters / 1000 * 390
```

서버는 소수점 결과를 반올림해 정수 초로 저장합니다.

모바일은 코스 작성 중 또는 사용자 개인 설정 화면에서 사용자의 페이스를 기준으로 별도 예상 시간을 계산해 보여줄 수 있습니다.
이 값은 개인화된 표시값이며, MVP API의 `estimatedDurationSeconds`를 대체하지 않습니다.

## Bounds 계산

코스의 `bounds`는 `path`에 포함된 모든 Route Point를 감싸는 최소 사각형입니다.

- `southWest.latitude`: path 내 최소 latitude
- `southWest.longitude`: path 내 최소 longitude
- `northEast.latitude`: path 내 최대 latitude
- `northEast.longitude`: path 내 최대 longitude

## 지도 범위 조회

`GET /courses`는 요청 bounds와 코스 bounds가 겹치는 공개 코스를 반환합니다.

MVP에서는 코스의 실제 선분이 화면 영역과 교차하는지까지는 검사하지 않습니다.
