# 코스 댓글 + 러닝 인증 이미지 첨부

## 배경

공개(`PUBLIC`) 코스 상세 화면에서 사용자가 댓글을 남기고, 댓글에 러닝 인증 사진을 첨부할 수 있게
해달라는 요청에 따라 새 API를 구현하였습니다. `PRIVATE` 코스는 커뮤니티 노출 대상이 아니므로
댓글 자체를 만들 수 없게 제한합니다.

API 계약: `docs/api-contract.md` "Course Comment APIs"
데이터 모델: `docs/data-model.md` "CourseComment"

## 설계 결정

### 댓글은 코스 종속, Post/Comment와 별개 도메인

기존 `docs/data-model.md`에는 게시글(Post) 댓글(Comment)이 이미 정의되어 있었지만, 이번 요청은
"코스 상세 화면"에 달리는 댓글이라 게시글 댓글과는 목적이 다릅니다(러닝 인증 사진 첨부가 핵심).
그래서 별도 엔티티 `CourseComment`(`courseId` 소유)로 새로 설계하고, 기존 `Comment`(postId 소유)는
건드리지 않았습니다.

### PUBLIC 코스만 댓글 허용

- 댓글 생성 시 코스가 `PUBLIC`이 아니면 `400 VALIDATION_ERROR`.
- 댓글 목록 조회 시 `PRIVATE` 코스는 작성자 본인만 조회 가능(`403 FORBIDDEN`), `PUBLIC`은 `Optional` 인증.

### 이미지 저장 — 로컬 디스크 (YAGNI)

클라우드 스토리지(S3 등) 연동은 현재 인프라에 없고, MVP 범위에서 필요하지 않다고 판단해
로컬 디스크에 저장하는 방식으로 구현했습니다. 신규 인프라가 필요해지면 `ImageStorageService`의
구현체만 교체하면 되도록 저장 로직을 서비스 클래스로 분리했습니다.

- 저장 경로: `{runvas.upload.dir}/course-comments/{courseId}/{UUID}.{ext}`
- 서빙: `WebMvcConfigurer`로 `/uploads/**` → `runvas.upload.dir` 정적 리소스 매핑
- 반환 URL: `{runvas.upload.base-url}/uploads/course-comments/{courseId}/{filename}`
- 검증: 확장자 화이트리스트(jpg/jpeg/png/webp), 5MB 이하. 원본 파일명은 저장에 쓰지 않고
  확장자만 취해 새 UUID 파일명을 생성 — 경로 조작(path traversal) 방지.
- `courseId`는 실제 존재하는 코스인지 먼저 검증한 뒤에만 저장 경로에 쓰이므로, 존재하지 않는
  임의 문자열을 디렉터리명으로 주입할 수 없습니다.

### 커서 페이지네이션

opaque cursor 대신 "이전 페이지 마지막 댓글의 `id`"를 커서로 그대로 사용하는 keyset pagination
(`createdAt desc, id desc`)으로 단순하게 구현했습니다. 커서로 들어온 id가 존재하지 않으면
`400 VALIDATION_ERROR`.

### 이미지 교체/삭제 규칙 (PATCH)

- `image`를 새로 보내면 기존 이미지 파일을 지우고 교체합니다.
- `removeImage=true`면 기존 이미지를 지우고 `null`로 설정합니다.
- 둘 다 오면 `image`가 우선합니다 (문서에 명시).

### 삭제는 멱등적으로 처리

`docs/api-contract.md`의 공통 규칙("삭제 API는 멱등적으로 처리")에 따라, 이미 삭제된 댓글에 대한
재삭제 요청은 에러 없이 204를 반환합니다(`BookmarkService.remove`와 동일한 패턴).

## 변경/신규 파일

| 파일 | 종류 | 내용 |
|------|------|------|
| `db/migration/V8__create_course_comments.sql` | 신규 | `course_comments` 테이블, `(course_id, created_at)` 인덱스 |
| `backend/community/CourseComment.java` | 신규 | JPA 엔티티 |
| `backend/community/CourseCommentRepository.java` | 신규 | keyset 커서 페이지네이션 쿼리 2종 |
| `backend/community/CourseCommentService.java` | 신규 | 목록/생성/수정/삭제, 공개 여부·작성자 검증 |
| `backend/community/CourseCommentController.java` | 신규 | `/api/courses/{courseId}/comments` multipart 엔드포인트 |
| `backend/community/dto/CourseCommentResponse.java` | 신규 | 응답 DTO (문서와 1:1) |
| `backend/community/dto/PublicProfile.java` | 신규 | 작성자 공개 프로필 DTO |
| `backend/storage/ImageStorageService.java` | 신규 | 로컬 디스크 이미지 저장/삭제, 형식·용량 검증 |
| `backend/config/UploadWebConfig.java` | 신규 | `/uploads/**` 정적 리소스 매핑 |
| `global/security/SecurityConfig.java` | 수정 | 신규 라우트 4개 인증 정책 등록 |
| `application.yml` | 수정 | `runvas.upload.dir`/`base-url`, multipart 용량 제한 |
| `test/.../community/CourseCommentControllerTest.java` | 신규 | 통합 테스트 7건 (아래 참고) |

## API

### GET /api/courses/{courseId}/comments

- Auth: Optional (`PRIVATE` 코스는 작성자만 200, 그 외는 403)
- 커서 페이지네이션 (`limit` 기본 20, 최대 50)

### POST /api/courses/{courseId}/comments

- Auth: Required, `multipart/form-data`
- `body`(text, 1-1000자, 필수), `image`(file, 선택, jpg/jpeg/png/webp, 5MB 이하)
- 코스가 `PUBLIC`이 아니면 `400 VALIDATION_ERROR`

### PATCH /api/courses/{courseId}/comments/{commentId}

- Auth: Required, 작성자 본인만, `multipart/form-data`
- `body`(선택), `image`(선택, 교체), `removeImage`(선택, `true`면 이미지 제거)

### DELETE /api/courses/{courseId}/comments/{commentId}

- Auth: Required, 작성자 본인만, 멱등적 204. 첨부 이미지 파일도 함께 삭제.

## 테스트

### 자동화 테스트 (`CourseCommentControllerTest`, Testcontainers PostgreSQL)

1. `createCommentOnPublicCourseSucceedsWithoutImage` — 이미지 없이 댓글 작성 성공, `imageUrl` 미노출
2. `createCommentOnPrivateCourseReturns400` — PRIVATE 코스 댓글 시도 시 400 VALIDATION_ERROR
3. `createCommentWithInvalidImageExtensionReturns400` — 허용되지 않는 확장자(gif) 시 400
4. `createCommentWithOversizedImageReturns400` — 6MB 이미지 시 400
5. `nonAuthorCannotUpdateOrDeleteComment` — 작성자가 아닌 사용자의 수정/삭제 시도 시 403
6. `authorCanUpdateAndDeleteOwnComment` — 작성자 본인 수정 200, 삭제 204
7. `listReturnsCommentsMatchingDocumentedShape` — 목록 응답이 문서 스펙과 필드 단위로 일치

### 검증 결과

```
./gradlew build -x test  → BUILD SUCCESSFUL
./gradlew test           → 39/39 tests passed (신규 7건 포함), 0 failures, 0 errors
```

## 알려진 한계 / 후속 과제

- 이미지 형식 검증이 확장자 기준이라, 실제 파일 시그니처(매직 바이트)까지는 검사하지 않습니다.
  Spring Security 기본 헤더(`X-Content-Type-Options: nosniff`)로 브라우저 콘텐츠 스니핑은
  방어되지만, 더 엄격한 검증이 필요해지면 Apache Tika 등으로 매직 바이트 검사를 추가할 수 있습니다.
- 로컬 디스크 저장이라 다중 인스턴스/컨테이너 재배포 환경에서는 파일이 유실될 수 있습니다.
  운영 환경 확장 시 S3 등 외부 스토리지로 교체가 필요합니다 (`ImageStorageService` 인터페이스화 고려).
