# API 계약

이 문서는 MVP 기준 API 계약 초안입니다.

최종 URL prefix, 인증 방식, 페이지네이션 방식은 구현 중 확정하되, 요청/응답 필드 이름과 타입은 이 문서를 기준으로 맞춥니다.

## 공통 규칙

- 요청/응답 본문은 JSON을 사용합니다.
- 날짜/시간은 ISO 8601 문자열을 사용합니다.
- 거리는 미터 단위입니다.
- 시간 길이는 초 단위입니다.
- 좌표는 `{ "latitude": number, "longitude": number }` 형태를 사용합니다.
- 인증이 필요한 API는 `Authorization: Bearer <token>` 헤더를 사용합니다.
- 작성자 본인만 본인의 코스, 게시글, 댓글을 수정하거나 삭제할 수 있습니다.
- 커뮤니티 목록 API는 커서 기반 페이지네이션을 사용합니다.

## 에러 응답

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request body",
    "details": [
      {
        "field": "path",
        "message": "Path must contain at least 2 points"
      }
    ]
  }
}
```

## POST /courses

코스를 생성합니다.

### Request

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
  "visibility": "PUBLIC",
  "tags": ["heart", "city"]
}
```

### Response

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
    "likeCount": 0,
    "reportCount": 0,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

## GET /courses

지도 범위 기준으로 코스 목록을 조회합니다.

### Query

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `swLat` | number | Y | 남서쪽 위도 |
| `swLng` | number | Y | 남서쪽 경도 |
| `neLat` | number | Y | 북동쪽 위도 |
| `neLng` | number | Y | 북동쪽 경도 |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |
| `q` | string | N | 코스 제목 검색어 |
| `tag` | string | N | 단일 태그 필터 |
| `sort` | string | N | `createdAtDesc`, `distanceAsc`, `distanceDesc`, `popularDesc` 중 하나. 기본 `createdAtDesc` |

### Response

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
      "reportCount": 0,
      "createdAt": "2026-06-22T08:00:00Z",
      "updatedAt": "2026-06-22T08:00:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

목록 응답에는 `path`를 포함하지 않습니다. 상세 화면 진입 시 `GET /courses/{courseId}`를 호출합니다.

검색과 필터 조건이 함께 전달되면 서버는 모든 조건을 만족하는 공개 코스만 반환합니다.
`q`는 MVP에서 제목 부분 일치 검색으로 처리합니다.

## GET /courses/{courseId}

코스 상세 정보를 조회합니다.

### Response

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
    "reportCount": 0,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

## PATCH /courses/{courseId}

코스를 수정합니다.

작성자 본인만 수정할 수 있습니다.
수정 요청에 `path`가 포함되면 서버는 거리, 예상 소요 시간, bounds를 다시 계산합니다.

### Request

전송한 필드만 수정합니다.
단, `path`와 `tags`는 부분 수정이 아니라 전체 교체 방식입니다.

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
  "visibility": "PRIVATE",
  "tags": ["heart", "river"]
}
```

### Response

```json
{
  "course": {
    "id": "course_123",
    "authorId": "user_123",
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
    "tags": ["heart", "river"],
    "likeCount": 12,
    "reportCount": 0,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T09:00:00Z"
  }
}
```

## GET /courses/{courseId}/gpx

코스를 GPX 파일로 다운로드합니다.

### Response

- Content-Type: `application/gpx+xml`
- Content-Disposition: `attachment; filename="course_123.gpx"`

## POST /courses/{courseId}/copy

공개 코스를 내 코스로 복사합니다.

원본 코스는 변경하지 않습니다.
복사된 코스의 `authorId`는 요청 사용자로 설정하고, 기본 `visibility`는 `PRIVATE`입니다.

### Response

```json
{
  "course": {
    "id": "course_456",
    "authorId": "user_456",
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
    "tags": ["heart", "city"],
    "likeCount": 0,
    "reportCount": 0,
    "createdAt": "2026-06-22T09:10:00Z",
    "updatedAt": "2026-06-22T09:10:00Z"
  }
}
```

## POST /auth/signup

이메일과 비밀번호로 가입합니다.

### Request

```json
{
  "email": "runner@example.com",
  "password": "secure-password",
  "nickname": "Seoul Runner"
}
```

### Response

```json
{
  "accessToken": "jwt_access_token",
  "user": {
    "id": "user_123",
    "email": "runner@example.com",
    "nickname": "Seoul Runner",
    "profileImageUrl": null,
    "bio": null,
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

## POST /auth/login

이메일과 비밀번호로 로그인합니다.

### Request

```json
{
  "email": "runner@example.com",
  "password": "secure-password"
}
```

### Response

`POST /auth/signup`과 같은 형태를 반환합니다.

## GET /me

현재 로그인한 사용자를 조회합니다.

### Response

```json
{
  "user": {
    "id": "user_123",
    "email": "runner@example.com",
    "nickname": "Seoul Runner",
    "profileImageUrl": null,
    "bio": "Drawing routes around Seoul.",
    "createdAt": "2026-06-22T08:00:00Z",
    "updatedAt": "2026-06-22T08:00:00Z"
  }
}
```

## PATCH /me

현재 로그인한 사용자의 공개 프로필을 수정합니다.

### Request

```json
{
  "nickname": "Seoul Runner",
  "profileImageUrl": "https://example.com/profile.png",
  "bio": "Drawing routes around Seoul."
}
```

## GET /posts

게시글 목록을 조회합니다.

### Query

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `q` | string | N | 제목 또는 본문 검색어 |
| `tag` | string | N | 단일 태그 필터 |
| `sort` | string | N | `createdAtDesc`, `popularDesc` 중 하나. 기본 `createdAtDesc` |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

### Response

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
      "commentCount": 2,
      "reportCount": 0,
      "createdAt": "2026-06-22T09:00:00Z",
      "updatedAt": "2026-06-22T09:00:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

## POST /posts

게시글을 작성합니다.

`attachedCourseId`가 있으면 해당 코스는 `PUBLIC`이어야 합니다.

### Request

```json
{
  "title": "한강 하트 코스 후기",
  "body": "초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.",
  "attachedCourseId": "course_123",
  "tags": ["hangang", "heart"]
}
```

## GET /posts/{postId}

게시글 상세를 조회합니다.

### Response

`GET /posts`의 단일 게시글 객체와 같은 필드를 반환합니다.

## PATCH /posts/{postId}

게시글을 수정합니다.

작성자 본인만 수정할 수 있습니다.

### Request

전송한 필드만 수정합니다.
단, `tags`는 부분 수정이 아니라 전체 교체 방식입니다.

```json
{
  "title": "수정한 한강 하트 코스 후기",
  "body": "주말 오전에는 사람이 적어서 더 좋았습니다.",
  "attachedCourseId": "course_123",
  "tags": ["hangang", "heart", "morning"]
}
```

## DELETE /posts/{postId}

게시글을 삭제합니다.

작성자 본인만 삭제할 수 있습니다.
게시글 삭제 시 해당 게시글의 댓글도 더 이상 목록에 노출하지 않습니다.

## GET /posts/{postId}/comments

게시글 댓글 목록을 조회합니다.

### Query

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

### Response

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
      "reportCount": 0,
      "createdAt": "2026-06-22T09:30:00Z",
      "updatedAt": "2026-06-22T09:30:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

## POST /posts/{postId}/comments

댓글을 작성합니다.

### Request

```json
{
  "body": "이 코스 저장해두고 주말에 뛰어볼게요."
}
```

## PATCH /comments/{commentId}

댓글을 수정합니다.

작성자 본인만 수정할 수 있습니다.

### Request

```json
{
  "body": "이 코스 저장해두고 이번 주말에 뛰어볼게요."
}
```

## DELETE /comments/{commentId}

댓글을 삭제합니다.

작성자 본인만 삭제할 수 있습니다.

## PUT /likes/{targetType}/{targetId}

코스 또는 게시글에 좋아요를 누릅니다.

`targetType`은 `courses` 또는 `posts`를 사용합니다.
동일 사용자의 중복 좋아요 요청은 성공으로 처리하되 좋아요 수를 중복 증가시키지 않습니다.

## DELETE /likes/{targetType}/{targetId}

코스 또는 게시글 좋아요를 취소합니다.

좋아요가 없는 상태에서 취소 요청을 보내도 성공으로 처리합니다.

## POST /reports

코스, 게시글, 댓글을 신고합니다.

### Request

```json
{
  "targetType": "POST",
  "targetId": "post_123",
  "reason": "SPAM",
  "message": "Repeated promotional content."
}
```

### Response

```json
{
  "report": {
    "id": "report_123",
    "targetType": "POST",
    "targetId": "post_123",
    "reason": "SPAM",
    "status": "PENDING",
    "createdAt": "2026-06-22T10:00:00Z"
  }
}
```
