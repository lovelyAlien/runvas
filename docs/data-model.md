# 데이터 모델

이 문서는 백엔드 저장 모델과 모바일 응답 모델이 공유해야 하는 논리 모델을 정의합니다.

실제 DB 테이블명, 인덱스명, ORM 엔티티 구조는 `runvas-backend`에서 별도로 결정할 수 있습니다.

## Course

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 코스 ID |
| `authorId` | string | Y | 생성자 사용자 ID |
| `title` | string | Y | 코스 제목 |
| `description` | string | N | 코스 설명 |
| `path` | RoutePoint[] | Y | 경로 좌표 목록 |
| `distanceMeters` | number | Y | 총 거리, 미터 단위 |
| `estimatedDurationSeconds` | number | Y | 예상 소요 시간, 초 단위 |
| `bounds` | GeoBounds | Y | 코스를 포함하는 최소 지도 영역 |
| `visibility` | CourseVisibility | Y | 공개 범위 |
| `tags` | string[] | Y | 검색/분류용 태그 |
| `likeCount` | number | Y | 코스 좋아요 수 |
| `reportCount` | number | Y | 코스 신고 수 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |
| `updatedAt` | string | Y | ISO 8601 수정 시각 |

## RoutePoint

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | number | Y | 위도 |
| `longitude` | number | Y | 경도 |
| `sequence` | number | Y | 경로 내 순서, 0부터 시작 |

### RoutePoint 순서 규칙

- `path` 배열은 `sequence` 오름차순과 같은 순서를 가져야 합니다.
- `sequence`는 0부터 시작해 1씩 증가해야 합니다.
- 코스 수정 중 포인트 추가, 삭제, 드래그 이동이 발생하면 모바일은 저장 요청 시 전체 `path`를 다시 전송합니다.
- 백엔드는 저장 전 `sequence` 연속성, 좌표 범위, 좌표 개수 제한을 검증합니다.
- 백엔드는 저장 시 `distanceMeters`, `estimatedDurationSeconds`, `bounds`, `updatedAt`을 다시 계산합니다.

## GeoBounds

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `southWest` | GeoPoint | Y | 남서쪽 좌표 |
| `northEast` | GeoPoint | Y | 북동쪽 좌표 |

## GeoPoint

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | number | Y | 위도 |
| `longitude` | number | Y | 경도 |

## CourseVisibility

| 값 | 설명 |
| --- | --- |
| `PUBLIC` | 다른 사용자가 탐색할 수 있음 |
| `PRIVATE` | 생성자만 볼 수 있음 |

## Course 제한값

| 항목 | 값 |
| --- | --- |
| 코스 제목 길이 | 1-60자 |
| 코스 설명 길이 | 0-500자 |
| 태그 개수 | 최대 10개 |
| 태그 길이 | 1-20자 |
| 경로 좌표 개수 | 2-5000개 |
| 최소 거리 | 100m |
| 최대 거리 | 100000m |

## 수정 가능 필드

MVP에서 사용자는 본인이 생성한 코스의 아래 필드를 수정할 수 있습니다.

| 필드 | 수정 가능 여부 | 비고 |
| --- | --- | --- |
| `title` | Y | 제한값은 생성과 동일 |
| `description` | Y | 빈 문자열 또는 미전송 시 설명 없음 |
| `path` | Y | 전체 경로를 교체하는 방식으로 수정 |
| `visibility` | Y | 공개/비공개 전환 |
| `tags` | Y | 전체 태그 목록을 교체하는 방식으로 수정 |
| `distanceMeters` | N | 백엔드 계산값 |
| `estimatedDurationSeconds` | N | 백엔드 계산값 |
| `bounds` | N | 백엔드 계산값 |
| `likeCount` | N | 백엔드 계산값 |
| `reportCount` | N | 백엔드 계산값 |
| `authorId` | N | 생성 후 변경 불가 |

## User

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 사용자 ID |
| `email` | string | Y | 로그인 이메일. 본인에게만 노출 |
| `nickname` | string | Y | 공개 닉네임 |
| `profileImageUrl` | string | N | 공개 프로필 이미지 URL |
| `bio` | string | N | 공개 소개 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |
| `updatedAt` | string | Y | ISO 8601 수정 시각 |

## PublicProfile

공개 화면과 커뮤니티 응답에서는 `User` 전체가 아니라 아래 필드만 노출합니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 사용자 ID |
| `nickname` | string | Y | 공개 닉네임 |
| `profileImageUrl` | string | N | 공개 프로필 이미지 URL |
| `bio` | string | N | 공개 소개 |

## Post

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 게시글 ID |
| `author` | PublicProfile | Y | 작성자 공개 프로필 |
| `title` | string | Y | 게시글 제목 |
| `body` | string | Y | 게시글 본문 |
| `attachedCourseId` | string | N | 첨부한 공개 코스 ID |
| `tags` | string[] | Y | 검색/분류용 태그 |
| `likeCount` | number | Y | 게시글 좋아요 수 |
| `commentCount` | number | Y | 댓글 수 |
| `reportCount` | number | Y | 신고 수 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |
| `updatedAt` | string | Y | ISO 8601 수정 시각 |

## Comment

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 댓글 ID |
| `postId` | string | Y | 댓글이 달린 게시글 ID |
| `author` | PublicProfile | Y | 작성자 공개 프로필 |
| `body` | string | Y | 댓글 본문 |
| `reportCount` | number | Y | 신고 수 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |
| `updatedAt` | string | Y | ISO 8601 수정 시각 |

## LikeTargetType

| 값 | 설명 |
| --- | --- |
| `COURSE` | 공개 코스 |
| `POST` | 게시글 |

## ReportTargetType

| 값 | 설명 |
| --- | --- |
| `COURSE` | 공개 코스 |
| `POST` | 게시글 |
| `COMMENT` | 댓글 |

## ReportReason

| 값 | 설명 |
| --- | --- |
| `SPAM` | 광고, 도배, 무관한 내용 |
| `ABUSE` | 욕설, 혐오, 괴롭힘 |
| `INAPPROPRIATE` | 부적절한 콘텐츠 |
| `PRIVACY` | 개인정보 또는 민감 위치 노출 |
| `OTHER` | 기타 |

## ReportStatus

| 값 | 설명 |
| --- | --- |
| `PENDING` | 검토 대기 |
| `REVIEWED` | 검토 완료 |
| `DISMISSED` | 조치 없이 종결 |

## Report

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 신고 ID |
| `reporterId` | string | Y | 신고한 사용자 ID |
| `targetType` | ReportTargetType | Y | 신고 대상 타입 |
| `targetId` | string | Y | 신고 대상 ID |
| `reason` | ReportReason | Y | 신고 사유 |
| `message` | string | N | 신고 상세 설명 |
| `status` | ReportStatus | Y | 처리 상태 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |

## 커뮤니티 제한값

| 항목 | 값 |
| --- | --- |
| 닉네임 길이 | 2-30자 |
| 프로필 소개 길이 | 0-160자 |
| 게시글 제목 길이 | 1-80자 |
| 게시글 본문 길이 | 1-5000자 |
| 게시글 태그 개수 | 최대 10개 |
| 댓글 본문 길이 | 1-1000자 |
| 신고 상세 설명 길이 | 0-1000자 |

## 커뮤니티 규칙

- 게시글에 첨부할 수 있는 코스는 `PUBLIC` 코스만 허용합니다.
- 동일 사용자는 같은 코스 또는 게시글에 좋아요를 한 번만 누를 수 있습니다.
- 좋아요 취소 후 다시 좋아요를 누를 수 있습니다.
- 동일 사용자는 같은 대상에 여러 번 신고할 수 없습니다.
- 신고 대상은 MVP에서 자동 삭제하지 않습니다. 서버는 신고를 접수하고 `PENDING` 상태로 저장합니다.
- 작성자 본인만 본인의 코스, 게시글, 댓글을 수정하거나 삭제할 수 있습니다.
