# API 기준

이 문서는 Runvas MVP에서 `backend/`와 `mobile/`이 공유하는 HTTP API 기준입니다.

구현 중 내부 구조는 바뀔 수 있지만, 요청/응답 필드 이름, 타입, 상태 코드, 에러 코드는 이 문서를 기준으로 맞춥니다.

## 공통 규칙

- Base path는 `/api`를 사용합니다. 예: `GET /api/courses`
- 요청/응답 본문은 JSON을 사용합니다.
- 날짜/시간은 ISO 8601 UTC 문자열을 사용합니다.
- 거리는 미터 단위, 시간 길이는 초 단위를 사용합니다.
- 좌표는 `{ "latitude": number, "longitude": number }` 형태를 사용합니다.
- 인증이 필요한 API는 `Authorization: Bearer <accessToken>` 헤더를 사용합니다.
- 요청 본문에서 선택 필드를 `null`로 보내면 값을 비우는 것으로 처리합니다.
- 요청 본문에서 선택 필드를 생략하면 기존 값을 유지하거나 서버 기본값을 사용합니다.
- 목록 API는 커서 기반 페이지네이션을 사용합니다.
- 삭제 API와 좋아요 취소 API는 멱등적으로 처리합니다.

## 인증 정책

| 표기 | 의미 |
| --- | --- |
| `None` | 인증 없이 호출할 수 있습니다. |
| `Optional` | 인증 없이 호출할 수 있지만, 로그인 사용자는 개인화 필드가 추가될 수 있습니다. |
| `Required` | 유효한 `Authorization` 헤더가 필요합니다. |

## 공통 객체

### GeoPoint

```json
{
  "latitude": 37.5665,
  "longitude": 126.978
}
```

### RoutePoint

```json
{
  "latitude": 37.5665,
  "longitude": 126.978,
  "sequence": 0
}
```

### GeoBounds

```json
{
  "southWest": {
    "latitude": 37.5665,
    "longitude": 126.978
  },
  "northEast": {
    "latitude": 37.567,
    "longitude": 126.979
  }
}
```

### PublicProfile

```json
{
  "id": "user_123",
  "nickname": "Seoul Runner",
  "profileImageUrl": null,
  "bio": "Drawing routes around Seoul."
}
```

### PageInfo

```json
{
  "nextCursor": "cursor_abc"
}
```

`nextCursor`가 `null`이면 다음 페이지가 없습니다.

### User

`User` 응답은 현재 로그인한 사용자 본인에게만 반환합니다.
소셜 로그인 제공자의 내부 식별자인 `providerUserId`는 API 응답에 포함하지 않습니다.

```json
{
  "id": "user_123",
  "email": "runner@example.com",
  "provider": "KAKAO",
  "nickname": "Seoul Runner",
  "profileImageUrl": null,
  "bio": "Drawing routes around Seoul.",
  "runningPaceSecPerKm": 360,
  "createdAt": "2026-06-22T08:00:00Z",
  "updatedAt": "2026-06-22T08:00:00Z"
}
```

## 공통 에러 응답

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request body",
    "details": [
      {
        "field": "path",
        "message": "Path must contain 2-5000 points"
      }
    ]
  }
}
```

| HTTP 상태 | 코드 | 설명 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 요청 값이 형식, 타입, 제한값을 만족하지 않습니다. |
| `401` | `UNAUTHORIZED` | 인증이 필요하거나 토큰이 유효하지 않습니다. |
| `403` | `FORBIDDEN` | 인증은 되었지만 대상 리소스에 접근 권한이 없습니다. |
| `404` | `NOT_FOUND` | 대상 리소스가 없거나 접근 가능한 상태가 아닙니다. |
| `409` | `CONFLICT` | 중복 요청, 상태 충돌 등으로 처리할 수 없습니다. |
| `500` | `INTERNAL_ERROR` | 서버 내부 오류입니다. |

## 제한값

### Course

| 항목 | 값 |
| --- | --- |
| `title` | 1-60자 |
| `description` | 0-500자 또는 `null` |
| `tags` | 최대 10개 |
| `tags[]` | 1-20자 |
| `path` | 2-5000개 |
| `distanceMeters` | 100-100000. 서버 계산값이 아니라 경로 탐색 API 또는 지도 폴리라인 기준 요청값 |
| `visibility` | `PUBLIC`, `PRIVATE` |

### Community

| 항목 | 값 |
| --- | --- |
| `nickname` | 2-30자 |
| `bio` | 0-160자 또는 `null` |
| `runningPaceSecPerKm` | 120-900 (2:00/km ~ 15:00/km). 기본값 360 (6:00/km) |
| `post.title` | 1-80자 |
| `post.body` | 1-5000자 |
| `post.tags` | 최대 10개 |
| `comment.body` | 1-1000자 |
| `courseComment.body` | 1-1000자 |
| `courseComment.image` | jpg/jpeg/png/webp, 최대 5MB, 댓글당 1장 |

## Routing APIs

### POST /routes/pedestrian

두 지점 사이의 보행자 경로를 조회합니다. T-Map 보행자 경로 탐색 API를 백엔드에서만 호출하고
(클라이언트에 키를 노출하지 않기 위해), 좌표 쌍 단위로 결과를 캐싱해 외부 API 호출량(무료 한도
1,000회/일)을 아낀다. 외부 API 호출이 실패하면 두 지점을 잇는 직선 2점을 반환한다.
비로그인 사용자도 지도에서 경로를 그릴 수 있어야 하므로 인증 없이 호출할 수 있다.

#### Auth

`None`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `start` | GeoPoint | Y | 출발 지점 |
| `end` | GeoPoint | Y | 도착 지점 |

```json
{
  "start": { "latitude": 37.5665, "longitude": 126.978 },
  "end": { "latitude": 37.567, "longitude": 126.979 }
}
```

#### Response: 200 OK

```json
{
  "path": [
    { "latitude": 37.5665, "longitude": 126.978, "sequence": 0 },
    { "latitude": 37.5667, "longitude": 126.9785, "sequence": 1 },
    { "latitude": 37.567, "longitude": 126.979, "sequence": 2 }
  ]
}
```

#### Errors

- `400 VALIDATION_ERROR`: `start`/`end` 누락
- `401 UNAUTHORIZED`: 로그인하지 않음

## Course APIs

### POST /courses

코스를 생성합니다.

#### Auth

`Required`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | Y | 코스 제목 |
| `description` | string \| null | N | 코스 설명 |
| `path` | RoutePoint[] | Y | 실제 보행 경로 폴리라인 좌표 목록 |
| `waypoints` | RoutePoint[] | Y | 사용자가 지도에서 실제로 탭한 지점 (`path`와 별개, 포인트 개수 표시·코스 수정에 사용) |
| `distanceMeters` | number | Y | 경로 탐색 API 또는 지도 폴리라인 기준 총 거리 |
| `estimatedDurationSeconds` | number | Y | 경로 탐색 API 또는 모바일 표시 기준 예상 소요 시간 |
| `bounds` | GeoBounds | Y | `path`를 포함하는 최소 지도 영역 |
| `visibility` | string | Y | `PUBLIC` 또는 `PRIVATE` |
| `tags` | string[] | N | 검색/분류용 태그. 기본 `[]` |

```json
{
  "title": "Heart Run in Seoul",
  "description": "A heart-shaped running route near the river.",
  "path": [
    {
      "latitude": 37.5665,
      "longitude": 126.978,
      "sequence": 0
    },
    {
      "latitude": 37.567,
      "longitude": 126.979,
      "sequence": 1
    }
  ],
  "waypoints": [
    {
      "latitude": 37.5665,
      "longitude": 126.978,
      "sequence": 0
    },
    {
      "latitude": 37.567,
      "longitude": 126.979,
      "sequence": 1
    }
  ],
  "distanceMeters": 1240,
  "estimatedDurationSeconds": 480,
  "bounds": {
    "southWest": {
      "latitude": 37.5665,
      "longitude": 126.978
    },
    "northEast": {
      "latitude": 37.567,
      "longitude": 126.979
    }
  },
  "visibility": "PUBLIC",
  "tags": ["heart", "city"]
}
```

#### Response: 201 Created

```json
{
  "course": {
    "id": "course_123",
    "authorId": "user_123",
    "title": "Heart Run in Seoul",
    "description": "A heart-shaped running route near the river.",
    "path": [
      {
        "latitude": 37.5665,
        "longitude": 126.978,
        "sequence": 0
      },
      {
        "latitude": 37.567,
        "longitude": 126.979,
        "sequence": 1
      }
    ],
    "waypoints": [
      {
        "latitude": 37.5665,
        "longitude": 126.978,
        "sequence": 0
      },
      {
        "latitude": 37.567,
        "longitude": 126.979,
        "sequence": 1
      }
    ],
    "distanceMeters": 1240,
    "estimatedDurationSeconds": 480,
    "bounds": {
      "southWest": {
        "latitude": 37.5665,
        "longitude": 126.978
      },
      "northEast": {
        "latitude": 37.567,
        "longitude": 126.979
      }
    },
    "startAddress": "서울특별시 중구 을지로 1가",
    "visibility": "PUBLIC",
    "tags": ["heart", "city"],
    "likeCount": 0,
    "likedByMe": false,
    "bookmarkedByMe": false,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

> `startAddress`는 요청에 포함하지 않습니다. 백엔드가 저장 시 `path[0]` 좌표로 T-Map 역지오코딩을 호출해 채웁니다. 역지오코딩 실패 시 `null`을 저장합니다.

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반, 좌표 범위 오류, `sequence` 불연속, 거리 제한 위반, `bounds`가 `path`를 포함하지 않음
- `401 UNAUTHORIZED`: 로그인하지 않음

### GET /courses

공개 코스 목록을 조회합니다. 다음 세 가지 검색 모드를 지원합니다.

- **bounds 검색**: `swLat`, `swLng`, `neLat`, `neLng` 4개 모두 제공 시 해당 범위 내 코스 조회. `q`나 `tag`와 함께 사용해 추가 필터 적용 가능.
- **이름 검색**: bounds 없이 `q`만 전달 시 전체 공개 코스에서 제목 부분 일치 검색.
- **태그 검색**: bounds/q 없이 `tag`만 전달 시 해당 태그를 포함하는 코스 검색 (대소문자 구분 없음, 정확 일치).

#### Auth

`Optional`

#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `swLat` | number | N | 남서쪽 위도. bounds 검색 시 4개 모두 필요 |
| `swLng` | number | N | 남서쪽 경도 |
| `neLat` | number | N | 북동쪽 위도 |
| `neLng` | number | N | 북동쪽 경도 |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |
| `q` | string | N | 코스 제목 부분 일치 검색어. bounds 없이 단독 사용 가능 |
| `tag` | string | N | 단일 태그 필터. bounds/q 없이 단독 사용 가능 (정확 일치, 대소문자 구분 없음) |
| `sort` | string | N | `createdAtDesc`, `distanceAsc`, `distanceDesc`, `popularDesc`. 기본 `createdAtDesc` |

bounds 4개 중 일부만 전달하거나, bounds/q/tag를 모두 생략하면 `400 VALIDATION_ERROR`를 반환합니다.

#### Response: 200 OK

목록 응답에는 `path`를 포함하지 않습니다. 상세 화면 진입 시 `GET /courses/{courseId}`를 호출합니다.

```json
{
  "courses": [
    {
      "id": "course_123",
      "authorId": "user_123",
      "title": "Heart Run in Seoul",
      "description": "A heart-shaped running route near the river.",
      "distanceMeters": 1240,
      "estimatedDurationSeconds": 480,
      "bounds": {
        "southWest": {
          "latitude": 37.5665,
          "longitude": 126.978
        },
        "northEast": {
          "latitude": 37.567,
          "longitude": 126.979
        }
      },
      "startAddress": "서울특별시 중구 을지로 1가",
      "visibility": "PUBLIC",
      "tags": ["heart", "city"],
      "likeCount": 12,
      "likedByMe": false,
      "createdAt": "2026-06-22T08:00:00Z",
      "updatedAt": "2026-06-22T08:00:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: bounds 일부만 전달, bounds/q/tag 모두 생략, `limit` 범위 초과, 지원하지 않는 `sort`

### GET /courses/mine

현재 로그인한 사용자가 작성한 코스 목록을 조회합니다. `GET /courses`와 달리 `visibility`
필터가 없어 `PRIVATE` 코스도 포함됩니다(본인 것이므로). bounds 파라미터가 필요 없고,
개인 목록이라 페이지네이션은 제공하지 않습니다.

#### Auth

`Required`

#### Response: 200 OK

목록 응답에는 `path`를 포함하지 않습니다 (`GET /courses`와 동일한 모양). 상세 화면 진입 시
`GET /courses/{courseId}`를 호출합니다.

```json
{
  "courses": [
    {
      "id": "course_123",
      "authorId": "user_123",
      "title": "Heart Run in Seoul",
      "description": "A heart-shaped running route near the river.",
      "distanceMeters": 1240,
      "estimatedDurationSeconds": 480,
      "bounds": {
        "southWest": {
          "latitude": 37.5665,
          "longitude": 126.978
        },
        "northEast": {
          "latitude": 37.567,
          "longitude": 126.979
        }
      },
      "visibility": "PRIVATE",
      "tags": [],
      "likeCount": 0,
      "likedByMe": false,
      "createdAt": "2026-06-22T08:00:00Z",
      "updatedAt": "2026-06-22T08:00:00Z"
    }
  ]
}
```

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음

### GET /courses/{courseId}

코스 상세 정보를 조회합니다.

#### Auth

`Optional`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |

#### Response: 200 OK

```json
{
  "course": {
    "id": "course_123",
    "authorId": "user_123",
    "title": "Heart Run in Seoul",
    "description": "A heart-shaped running route near the river.",
    "path": [
      {
        "latitude": 37.5665,
        "longitude": 126.978,
        "sequence": 0
      },
      {
        "latitude": 37.567,
        "longitude": 126.979,
        "sequence": 1
      }
    ],
    "waypoints": [
      {
        "latitude": 37.5665,
        "longitude": 126.978,
        "sequence": 0
      },
      {
        "latitude": 37.567,
        "longitude": 126.979,
        "sequence": 1
      }
    ],
    "distanceMeters": 1240,
    "estimatedDurationSeconds": 480,
    "bounds": {
      "southWest": {
        "latitude": 37.5665,
        "longitude": 126.978
      },
      "northEast": {
        "latitude": 37.567,
        "longitude": 126.979
      }
    },
    "visibility": "PUBLIC",
    "tags": ["heart", "city"],
    "likeCount": 12,
    "likedByMe": false,
    "bookmarkedByMe": false,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

`bookmarkedByMe`는 인증된 사용자에게만 실제 값을 반환하며, 미인증 요청에서는 `false`를 반환합니다.

비공개 코스는 작성자 본인만 조회할 수 있습니다.

#### Errors

- `401 UNAUTHORIZED`: 비공개 코스 조회에 인증이 필요함
- `403 FORBIDDEN`: 비공개 코스 작성자가 아님
- `404 NOT_FOUND`: 코스가 없음

### PATCH /courses/{courseId}

코스를 수정합니다.

작성자 본인만 수정할 수 있습니다. 전송한 필드만 수정합니다.
단, `path`와 `tags`는 부분 수정이 아니라 전체 교체 방식입니다.
`path`를 전송하는 경우 `waypoints`, `distanceMeters`, `estimatedDurationSeconds`, `bounds`도
함께 전송해야 합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | N | 코스 제목 |
| `description` | string \| null | N | 코스 설명 |
| `path` | RoutePoint[] | N | 전체 실제 보행 경로 폴리라인 좌표 목록 |
| `waypoints` | RoutePoint[] | N | `path`가 바뀐 경우 함께 전송하는 사용자가 탭한 지점 |
| `distanceMeters` | number | N | 경로가 바뀐 경우 함께 전송하는 총 거리 |
| `estimatedDurationSeconds` | number | N | 경로가 바뀐 경우 함께 전송하는 예상 소요 시간 |
| `bounds` | GeoBounds | N | 경로가 바뀐 경우 함께 전송하는 최소 지도 영역 |
| `visibility` | string | N | `PUBLIC` 또는 `PRIVATE` |
| `tags` | string[] | N | 전체 태그 목록 |

```json
{
  "title": "Updated Heart Run in Seoul",
  "description": "A refined heart-shaped running route near the river.",
  "path": [
    {
      "latitude": 37.5665,
      "longitude": 126.978,
      "sequence": 0
    },
    {
      "latitude": 37.5672,
      "longitude": 126.9791,
      "sequence": 1
    }
  ],
  "distanceMeters": 1270,
  "estimatedDurationSeconds": 495,
  "bounds": {
    "southWest": {
      "latitude": 37.5665,
      "longitude": 126.978
    },
    "northEast": {
      "latitude": 37.5672,
      "longitude": 126.9791
    }
  },
  "visibility": "PRIVATE",
  "tags": ["heart", "river"]
}
```

#### Response: 200 OK

`POST /courses`의 `course`와 같은 전체 코스 객체를 반환합니다.

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반, 좌표 범위 오류, `sequence` 불연속, 거리 제한 위반, `bounds`가 `path`를 포함하지 않음
- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 코스가 없음

### DELETE /courses/{courseId}

코스를 삭제합니다.

작성자 본인만 삭제할 수 있습니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 코스가 없음

### GET /courses/{courseId}/gpx

코스를 GPX 파일로 다운로드합니다.

#### Auth

`Optional`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |

#### Response: 200 OK

| 헤더 | 값 |
| --- | --- |
| `Content-Type` | `application/gpx+xml` |
| `Content-Disposition` | `attachment; filename="course_123.gpx"` |

응답 본문은 GPX XML입니다.

#### Errors

- `401 UNAUTHORIZED`: 비공개 코스 다운로드에 인증이 필요함
- `403 FORBIDDEN`: 비공개 코스 작성자가 아님
- `404 NOT_FOUND`: 코스가 없음

### POST /courses/{courseId}/bookmarks

공개 코스를 내 저장 목록에 추가합니다.

동일 사용자의 중복 북마크 요청은 성공으로 처리하되 중복 저장하지 않습니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 공개 코스 ID |

#### Response: 200 OK

```json
{
  "bookmark": {
    "courseId": "course_123",
    "createdAt": "2026-06-22T09:10:00Z"
  }
}
```

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 공개 코스가 없음

### DELETE /courses/{courseId}/bookmarks

공개 코스를 내 저장 목록에서 제거합니다.

북마크가 없는 상태에서 취소 요청을 보내도 성공으로 처리합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 공개 코스 ID |

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 공개 코스가 없음

### GET /me/bookmarked-courses

현재 로그인한 사용자가 북마크한 공개 코스 목록을 조회합니다.

#### Auth

`Required`

#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

#### Response: 200 OK

`bookmarkedAt`은 `CourseBookmark.createdAt`을 코스 목록 표시용으로 노출한 값입니다.

```json
{
  "courses": [
    {
      "id": "course_123",
      "authorId": "user_123",
      "title": "Heart Run in Seoul",
      "description": "A heart-shaped running route near the river.",
      "distanceMeters": 1240,
      "estimatedDurationSeconds": 480,
      "bounds": {
        "southWest": {
          "latitude": 37.5665,
          "longitude": 126.978
        },
        "northEast": {
          "latitude": 37.567,
          "longitude": 126.979
        }
      },
      "visibility": "PUBLIC",
      "tags": ["heart", "city"],
      "likeCount": 12,
      "likedByMe": false,
      "bookmarkedAt": "2026-06-22T09:10:00Z",
      "createdAt": "2026-06-22T08:00:00Z",
      "updatedAt": "2026-06-22T08:00:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: `limit` 범위 초과
- `401 UNAUTHORIZED`: 로그인하지 않음

## Course Comment APIs

공개(`PUBLIC`) 코스에만 댓글을 달 수 있습니다. `PRIVATE` 코스에 댓글을 시도하면 `400 VALIDATION_ERROR`를 반환합니다.

댓글에는 러닝 인증 이미지를 최대 1장 첨부할 수 있습니다. 이미지 업로드는 별도 API 없이 댓글 작성/수정 요청에 `multipart/form-data`로 함께 보냅니다.

댓글은 최상위 댓글과 그 댓글에 달리는 대댓글(reply), 총 2단계까지만 허용합니다. 대댓글에는 다시
대댓글을 달 수 없습니다(`400 VALIDATION_ERROR`). 최상위 댓글을 삭제하면 그 댓글에 달린 대댓글도
함께 삭제됩니다(첨부 이미지 포함).

### GET /courses/{courseId}/comments

코스의 최상위 댓글 목록을 조회합니다(대댓글은 포함하지 않습니다). `PRIVATE` 코스는 작성자 본인만 조회할 수 있습니다.

#### Auth

`Optional`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |

#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

#### Response: 200 OK

```json
{
  "comments": [
    {
      "id": "course_comment_123",
      "courseId": "course_123",
      "parentCommentId": null,
      "author": {
        "id": "user_456",
        "nickname": "River Runner",
        "profileImageUrl": null,
        "bio": null
      },
      "body": "오늘 이 코스 완주했습니다!",
      "imageUrl": "http://localhost:8921/uploads/course-comments/course_123/8f1c.jpg",
      "replyCount": 2,
      "createdAt": "2026-06-22T09:30:00Z",
      "updatedAt": "2026-06-22T09:30:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

`parentCommentId`가 `null`이면 최상위 댓글입니다. `replyCount`는 해당 댓글에 달린 대댓글 수입니다
(대댓글 응답에서는 항상 `0`).

#### Errors

- `400 VALIDATION_ERROR`: `limit` 범위 초과
- `403 FORBIDDEN`: `PRIVATE` 코스에 작성자가 아닌 사용자가 접근
- `404 NOT_FOUND`: 코스가 없음

### GET /courses/{courseId}/comments/{commentId}/replies

특정 최상위 댓글에 달린 대댓글 목록을 오래된 순으로 전체 조회합니다(페이지네이션 없음, 최대 200개).

#### Auth

`Optional`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |
| `commentId` | string | 대댓글을 조회할 최상위 댓글 ID |

#### Response: 200 OK

```json
{
  "replies": [
    {
      "id": "course_comment_789",
      "courseId": "course_123",
      "parentCommentId": "course_comment_123",
      "author": {
        "id": "user_789",
        "nickname": "Han River Jogger",
        "profileImageUrl": null,
        "bio": null
      },
      "body": "저도 어제 뛰었어요!",
      "imageUrl": null,
      "replyCount": 0,
      "createdAt": "2026-06-22T10:00:00Z",
      "updatedAt": "2026-06-22T10:00:00Z"
    }
  ]
}
```

#### Errors

- `403 FORBIDDEN`: `PRIVATE` 코스에 작성자가 아닌 사용자가 접근
- `404 NOT_FOUND`: 코스 또는 댓글이 없음

### POST /courses/{courseId}/comments

코스에 댓글 또는 대댓글을 작성합니다. 대상 코스는 `PUBLIC`이어야 합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |

#### Request Body (`multipart/form-data`)

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `body` | string (form field) | Y | 댓글 본문 |
| `image` | file (form part) | N | 러닝 인증 이미지. jpg/jpeg/png/webp, 최대 5MB |
| `parentCommentId` | string (form field) | N | 대댓글을 작성할 최상위 댓글 ID. 생략하면 최상위 댓글로 작성 |

#### Response: 201 Created

`GET /courses/{courseId}/comments`의 단일 댓글 객체와 같은 `comment` 객체를 반환합니다.

```json
{
  "comment": {
    "id": "course_comment_123",
    "courseId": "course_123",
    "parentCommentId": null,
    "author": {
      "id": "user_456",
      "nickname": "River Runner",
      "profileImageUrl": null,
      "bio": null
    },
    "body": "오늘 이 코스 완주했습니다!",
    "imageUrl": "http://localhost:8921/uploads/course-comments/course_123/8f1c.jpg",
    "replyCount": 0,
    "createdAt": "2026-06-22T09:30:00Z",
    "updatedAt": "2026-06-22T09:30:00Z"
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: `body` 제한값 위반, 코스가 `PUBLIC`이 아님, 이미지 형식/용량 초과, `parentCommentId`가 다른 코스의 댓글이거나 이미 대댓글임(2단계 초과)
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 코스 또는 `parentCommentId`로 지정한 댓글이 없음

### PATCH /courses/{courseId}/comments/{commentId}

댓글을 수정합니다. 작성자 본인만 수정할 수 있습니다. 전송한 필드만 수정합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |
| `commentId` | string | 댓글 ID |

#### Request Body (`multipart/form-data`)

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `body` | string (form field) | N | 댓글 본문 |
| `image` | file (form part) | N | 새 이미지로 교체. jpg/jpeg/png/webp, 최대 5MB |
| `removeImage` | boolean (form field) | N | `true`면 기존 이미지를 제거. `image`와 동시에 보내면 `image`가 우선 |

#### Response: 200 OK

수정된 `comment` 객체를 반환합니다.

#### Errors

- `400 VALIDATION_ERROR`: `body` 제한값 위반, 이미지 형식/용량 초과
- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 코스 또는 댓글이 없음

### DELETE /courses/{courseId}/comments/{commentId}

댓글을 삭제합니다. 작성자 본인만 삭제할 수 있습니다. 삭제 시 첨부 이미지도 함께 제거합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `courseId` | string | 코스 ID |
| `commentId` | string | 댓글 ID |

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 코스 또는 댓글이 없음

## Auth APIs

### POST /auth/kakao

카카오 로그인으로 가입 또는 로그인합니다.

모바일 앱은 카카오 SDK를 통해 받은 인가 코드를 서버에 전달합니다.
서버는 인가 코드로 카카오 토큰을 발급받고, 카카오 사용자 정보를 조회한 뒤 Runvas 사용자와 연결합니다.
클라이언트는 카카오 액세스 토큰을 Runvas API 인증에 사용하지 않습니다.
Runvas API 인증에는 이 API가 발급한 자체 `accessToken`을 사용합니다.

#### Flow

1. 모바일 앱이 카카오 SDK로 로그인과 동의 절차를 시작합니다.
2. 카카오 SDK가 모바일 앱에 `authorizationCode`를 반환합니다.
3. 모바일 앱이 `POST /api/auth/kakao`로 `authorizationCode`와 `redirectUri`를 전달합니다.
4. 백엔드는 카카오 토큰 API에 `authorizationCode`, REST API 키, client secret, `redirectUri`를 전달해 카카오 액세스 토큰을 발급받습니다.
5. 백엔드는 카카오 액세스 토큰으로 카카오 사용자 정보를 조회합니다.
6. 백엔드는 `provider = KAKAO`, 카카오 사용자 ID를 기준으로 Runvas 사용자를 조회하거나 생성합니다.
7. 백엔드는 Runvas 자체 `accessToken`, `user`, `isNewUser`를 모바일 앱에 반환합니다.

카카오 액세스 토큰, 카카오 refresh token, client secret은 API 응답에 포함하지 않습니다.
카카오 사용자 ID는 `providerUserId`로 내부 저장하되 API 응답에 포함하지 않습니다.

#### Auth

`None`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | string | Y | `KAKAO` |
| `authorizationCode` | string | Y | 카카오 인가 코드 |
| `redirectUri` | string | Y | 카카오 인가 코드 요청에 사용한 모바일 앱 redirect URI |

```json
{
  "provider": "KAKAO",
  "authorizationCode": "kakao_authorization_code",
  "redirectUri": "runvas://auth/kakao"
}
```

#### Response: 200 OK

```json
{
  "accessToken": "jwt_access_token",
  "user": {
    "id": "user_123",
    "email": "runner@example.com",
    "provider": "KAKAO",
    "nickname": "Seoul Runner",
    "profileImageUrl": null,
    "bio": null,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  },
  "isNewUser": true
}
```

`accessToken`은 Runvas API용 JWT입니다.
MVP에서는 refresh token을 응답하지 않습니다.
`email`은 카카오 계정에 이메일이 없거나 사용자가 동의하지 않은 경우 `null`일 수 있습니다.
`nickname`은 카카오 프로필 닉네임을 기본값으로 사용하되, 없으면 서버가 기본 닉네임을 생성합니다.

#### Errors

- `400 VALIDATION_ERROR`: 필수 필드 누락, `provider`가 `KAKAO`가 아님
- `401 UNAUTHORIZED`: 카카오 인증 실패

### POST /auth/logout

로그아웃하고, 요청에 사용된 `accessToken`을 서버에서 무효화합니다.
무효화 범위는 로그아웃에 사용된 토큰 하나입니다 (동일 사용자의 다른 기기 로그인은 유지됩니다).

#### Auth

`Required`

#### Request Body

없음. 토큰은 `Authorization` 헤더에서 가져옵니다.

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않았거나 토큰이 유효하지 않음. 이미 로그아웃된(무효화된) 토큰으로
  재요청한 경우도 동일하게 처리합니다.

### GET /me

현재 로그인한 사용자를 조회합니다.

#### Auth

`Required`

#### Response: 200 OK

```json
{
  "user": {
    "id": "user_123",
    "email": "runner@example.com",
    "provider": "KAKAO",
    "nickname": "Seoul Runner",
    "profileImageUrl": null,
    "bio": "Drawing routes around Seoul.",
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음

### PATCH /me

현재 로그인한 사용자의 공개 프로필을 수정합니다.

#### Auth

`Required`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | string | N | 공개 닉네임 |
| `profileImageUrl` | string \| null | N | 공개 프로필 이미지 URL |
| `bio` | string \| null | N | 공개 소개 |
| `runningPaceSecPerKm` | number | N | 달리기 페이스 (초/km). 120-900 범위 |

```json
{
  "nickname": "Seoul Runner",
  "profileImageUrl": "https://example.com/profile.png",
  "bio": "Drawing routes around Seoul.",
  "runningPaceSecPerKm": 300
}
```

#### Response: 200 OK

`GET /me`와 같은 `user` 객체를 반환합니다.

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반
- `401 UNAUTHORIZED`: 로그인하지 않음
- `409 CONFLICT`: 이미 사용 중인 닉네임

## Post APIs

### GET /posts

게시글 목록을 조회합니다.

#### Auth

`Optional`

#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `q` | string | N | 제목 또는 본문 검색어 |
| `tag` | string | N | 단일 태그 필터 |
| `sort` | string | N | `createdAtDesc`, `popularDesc`. 기본 `createdAtDesc` |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

#### Response: 200 OK

```json
{
  "posts": [
    {
      "id": "post_123",
      "author": {
        "id": "user_123",
        "nickname": "Seoul Runner",
        "profileImageUrl": null,
        "bio": "Drawing routes around Seoul."
      },
      "title": "한강 하트 코스 후기",
      "body": "초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.",
      "attachedCourseId": "course_123",
      "tags": ["hangang", "heart"],
      "likeCount": 8,
      "likedByMe": false,
      "commentCount": 2,
      "createdAt": "2026-06-22T09:00:00Z",
      "updatedAt": "2026-06-22T09:00:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: `limit` 범위 초과, 지원하지 않는 `sort`

### POST /posts

게시글을 작성합니다.

`attachedCourseId`가 있으면 해당 코스는 `PUBLIC`이어야 합니다.

#### Auth

`Required`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | Y | 게시글 제목 |
| `body` | string | Y | 게시글 본문 |
| `attachedCourseId` | string \| null | N | 첨부할 공개 코스 ID |
| `tags` | string[] | N | 검색/분류용 태그. 기본 `[]` |

```json
{
  "title": "한강 하트 코스 후기",
  "body": "초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.",
  "attachedCourseId": "course_123",
  "tags": ["hangang", "heart"]
}
```

#### Response: 201 Created

`GET /posts`의 단일 게시글 객체와 같은 `post` 객체를 반환합니다.

```json
{
  "post": {
    "id": "post_123",
    "author": {
      "id": "user_123",
      "nickname": "Seoul Runner",
      "profileImageUrl": null,
      "bio": "Drawing routes around Seoul."
    },
    "title": "한강 하트 코스 후기",
    "body": "초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.",
    "attachedCourseId": "course_123",
    "tags": ["hangang", "heart"],
    "likeCount": 0,
    "likedByMe": false,
    "commentCount": 0,
    "createdAt": "2026-06-22T09:00:00Z",
    "updatedAt": "2026-06-22T09:00:00Z"
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 첨부 코스가 없음

### GET /posts/{postId}

게시글 상세를 조회합니다.

#### Auth

`Optional`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | string | 게시글 ID |

#### Response: 200 OK

`POST /posts`의 `post`와 같은 객체를 반환합니다.

#### Errors

- `404 NOT_FOUND`: 게시글이 없음

### PATCH /posts/{postId}

게시글을 수정합니다.

작성자 본인만 수정할 수 있습니다. 전송한 필드만 수정합니다.
단, `tags`는 부분 수정이 아니라 전체 교체 방식입니다.

#### Auth

`Required`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | N | 게시글 제목 |
| `body` | string | N | 게시글 본문 |
| `attachedCourseId` | string \| null | N | 첨부할 공개 코스 ID. `null`이면 첨부 해제 |
| `tags` | string[] | N | 전체 태그 목록 |

```json
{
  "title": "수정한 한강 하트 코스 후기",
  "body": "주말 오전에는 사람이 적어서 더 좋았습니다.",
  "attachedCourseId": "course_123",
  "tags": ["hangang", "heart", "morning"]
}
```

#### Response: 200 OK

수정된 `post` 객체를 반환합니다.

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반
- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 게시글 또는 첨부 코스가 없음

### DELETE /posts/{postId}

게시글을 삭제합니다.

작성자 본인만 삭제할 수 있습니다. 게시글 삭제 시 해당 게시글의 댓글도 더 이상 목록에 노출하지 않습니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | string | 게시글 ID |

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 게시글이 없음

## Comment APIs

### GET /posts/{postId}/comments

게시글 댓글 목록을 조회합니다.

#### Auth

`Optional`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | string | 게시글 ID |

#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

#### Response: 200 OK

```json
{
  "comments": [
    {
      "id": "comment_123",
      "postId": "post_123",
      "author": {
        "id": "user_456",
        "nickname": "River Runner",
        "profileImageUrl": null,
        "bio": null
      },
      "body": "이 코스 저장해두고 주말에 뛰어볼게요.",
      "createdAt": "2026-06-22T09:30:00Z",
      "updatedAt": "2026-06-22T09:30:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: `limit` 범위 초과
- `404 NOT_FOUND`: 게시글이 없음

### POST /posts/{postId}/comments

댓글을 작성합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | string | 게시글 ID |

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `body` | string | Y | 댓글 본문 |

```json
{
  "body": "이 코스 저장해두고 주말에 뛰어볼게요."
}
```

#### Response: 201 Created

```json
{
  "comment": {
    "id": "comment_123",
    "postId": "post_123",
    "author": {
      "id": "user_456",
      "nickname": "River Runner",
      "profileImageUrl": null,
      "bio": null
    },
    "body": "이 코스 저장해두고 주말에 뛰어볼게요.",
    "createdAt": "2026-06-22T09:30:00Z",
    "updatedAt": "2026-06-22T09:30:00Z"
  }
}
```

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 게시글이 없음

### PATCH /comments/{commentId}

댓글을 수정합니다.

작성자 본인만 수정할 수 있습니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `commentId` | string | 댓글 ID |

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `body` | string | Y | 댓글 본문 |

```json
{
  "body": "이 코스 저장해두고 이번 주말에 뛰어볼게요."
}
```

#### Response: 200 OK

수정된 `comment` 객체를 반환합니다.

#### Errors

- `400 VALIDATION_ERROR`: 제한값 위반
- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 댓글이 없음

### DELETE /comments/{commentId}

댓글을 삭제합니다.

작성자 본인만 삭제할 수 있습니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `commentId` | string | 댓글 ID |

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음
- `403 FORBIDDEN`: 작성자가 아님
- `404 NOT_FOUND`: 댓글이 없음

## Like APIs

### PUT /likes/{targetType}/{targetId}

코스 또는 게시글에 좋아요를 누릅니다.

동일 사용자의 중복 좋아요 요청은 성공으로 처리하되 좋아요 수를 중복 증가시키지 않습니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `targetType` | string | `courses` 또는 `posts` |
| `targetId` | string | 대상 ID |

#### Response: 200 OK

```json
{
  "targetType": "courses",
  "targetId": "course_123",
  "liked": true,
  "likeCount": 13
}
```

#### Errors

- `400 VALIDATION_ERROR`: 지원하지 않는 `targetType`
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 대상이 없음

### DELETE /likes/{targetType}/{targetId}

코스 또는 게시글 좋아요를 취소합니다.

좋아요가 없는 상태에서 취소 요청을 보내도 성공으로 처리합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `targetType` | string | `courses` 또는 `posts` |
| `targetId` | string | 대상 ID |

#### Response: 200 OK

```json
{
  "targetType": "courses",
  "targetId": "course_123",
  "liked": false,
  "likeCount": 12
}
```

#### Errors

- `400 VALIDATION_ERROR`: 지원하지 않는 `targetType`
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 대상이 없음
