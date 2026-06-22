# GPX 내보내기 규칙

Runvas는 저장된 코스를 외부 러닝 앱에서 사용할 수 있도록 GPX 파일로 내보냅니다.

## 형식

- GPX 버전: 1.1
- 인코딩: UTF-8
- 좌표계: WGS84
- 트랙 이름: 코스 제목

## 구조

GPX는 하나의 `trk`와 하나의 `trkseg`를 포함합니다.

각 Route Point는 `trkpt`로 변환합니다.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Runvas">
  <trk>
    <name>Heart Run in Seoul</name>
    <trkseg>
      <trkpt lat="37.5665" lon="126.978"></trkpt>
      <trkpt lat="37.567" lon="126.979"></trkpt>
    </trkseg>
  </trk>
</gpx>
```

## 좌표 변환

| Runvas 필드 | GPX 속성 |
| --- | --- |
| `latitude` | `lat` |
| `longitude` | `lon` |

## 파일명

기본 파일명은 다음 형식을 사용합니다.

```text
{courseId}.gpx
```

예시:

```text
course_123.gpx
```

## 제외 항목

MVP GPX에는 다음 값을 포함하지 않습니다.

- 시간 정보
- 고도 정보
- 심박수
- 케이던스
- 속도

