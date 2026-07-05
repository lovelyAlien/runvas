# 커뮤니티 게시글 Post/Comment/Like 백엔드 구현 + 모바일 연동 설계

작성일: 2026-07-05
관련 문서: `docs/api-contract.md` §Post APIs/§Comment APIs/§Like APIs, `docs/data-model.md` §Post/§Comment/
§PublicProfile/§LikeTargetType, `docs/product-scope.md` (게시글/댓글 작성·조회·수정·삭제, 좋아요),
`docs/superpowers/specs/2026-07-05-community-post-mobile-ui-design.md` (선행 작업 — 모바일 UI + mock)

## 배경

`docs/api-contract.md`, `docs/data-model.md`에는 Post/Comment/Like API 계약이 이미 완전히 정의되어
있다. 모바일은 이미 UI(BoardScreen, CourseBoardScreen, PostCreateScreen, PostDetailScreen)를 갖추고
`postApi.ts`/`commentApi.ts`/`likeApi.ts`의 in-memory mock으로 동작 중이다
(`mobile/docs/implementations/community-post-mobile-ui.md` 참고).

이번 작업은 `backend/`에 문서 계약 전체를 구현하고, 모바일 mock 3개 파일을 실제 `fetch` 호출로
교체해 두 layer를 연동한다. `community` 패키지에는 현재 `Like`/`LikeRepository`/`LikeTargetType`만
있고 Post/Comment는 아직 없다.

## 범위

- 백엔드: `docs/api-contract.md`에 정의된 Post/Comment/Like API 전체(조회/작성/수정/삭제, 좋아요
  토글) 구현. Course 모듈과 동일한 코드 패턴(`ApiException`/`ErrorCode`, `CurrentUserProvider`,
  트랜잭션 경계)을 따른다.
- 모바일: 화면이 실제로 호출하는 함수만 mock → 실제 fetch로 교체 (`getPosts`, `getPost`,
  `createPost`, `getComments`, `createComment`, `putLike`, `deleteLike`). 게시글/댓글 수정·삭제는
  백엔드엔 구현하지만 대응하는 모바일 화면이 없으므로 모바일 서비스 함수는 추가하지 않는다
  (화면이 생길 때 추가).

## 범위 밖

- 게시글/댓글 수정·삭제 모바일 UI/화면.
- 커서 기반 페이지네이션 실제 구현 — Course 목록과 동일하게 `nextCursor`는 항상 `null` (메모리
  필터링 + `limit`만 적용).
- 코스 좋아요(`LikeTargetType.COURSE`) 모바일 UI — 백엔드 Like API는 `courses`/`posts` 둘 다
  지원하도록 문서 그대로 구현하지만, 모바일에서는 게시글 좋아요만 호출한다.
- 게시글 태그 입력 UI, 태그/검색어 필터 UI (선행 모바일 UI 설계와 동일하게 범위 밖).

## 백엔드 설계

### 엔티티 (`com.runvas.backend.community`)

**`Post`**

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | `String` (UUID, `GenerationType.UUID`) | Course와 동일 패턴 |
| `authorId` | `String` (UUID 원문, prefix 없음) | `CurrentUserProvider.requireUserId()` 저장값 — Course.authorId와 동일한 저장 방식 |
| `title` | `String`, 1-80자 | |
| `body` | `String`, 1-5000자, `columnDefinition = "TEXT"` | |
| `attachedCourseId` | `String`, nullable | |
| `tags` | `Set<String>` `@ElementCollection` (`post_tags` 테이블), 최대 10개 | Course.tags와 동일 패턴 |
| `likeCount` | `Integer`, 기본 0 | 비정규화 카운터 (Course.likeCount와 동일 이유) |
| `commentCount` | `Integer`, 기본 0 | 비정규화 카운터 — 댓글 생성/삭제 시 갱신 |
| `createdAt`/`updatedAt` | `Instant` | |

**`Comment`**

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | `String` (UUID) | |
| `postId` | `String` | FK 컬럼(연관관계 매핑 대신 단순 컬럼 — Course가 연관관계를 안 쓰는 것과 일관) |
| `authorId` | `String` | |
| `body` | `String`, 1-1000자 | |
| `createdAt`/`updatedAt` | `Instant` | |

### PublicProfile 매핑

`docs/data-model.md`의 `PublicProfile`은 `User` 엔티티에서 파생되므로, Post/Comment 응답을 만들 때
`authorId`(raw UUID 문자열)로 `UserRepository.findById(UUID.fromString(authorId))`를 조회해
`PublicProfileResponse`를 구성한다.

```java
// com.runvas.user.dto.PublicProfileResponse
public record PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio) {
    public static PublicProfileResponse from(User user) {
        return new PublicProfileResponse(
                "user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
    }
}
```

- **ID 포맷 결정**: `/me` 응답(`UserResponse.from`)과 동일하게 `"user_" + UUID`로 통일한다.
  `Course.authorId`는 raw UUID(prefix 없음)인 기존 불일치가 있으나, 이미 배포된 Course 모듈이라
  이번 작업에서 손대지 않는다. Post/Comment의 `author` 필드는 새로 만드는 응답이므로 `/me`와
  일관된 포맷을 따른다.
- 작성자 User가 삭제된 경우(MVP에는 회원 탈퇴가 없으므로 발생하지 않음)는 고려하지 않는다.

### Repository

```java
public interface PostRepository extends JpaRepository<Post, String> {
    List<Post> findAllByOrderByCreatedAtDesc();
}

public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(String postId);
    void deleteByPostId(String postId); // 게시글 삭제 시 댓글 cascade 삭제용
}
```

목록/검색/정렬(`q`, `tag`, `attachedCourseId`, `sort`)은 Course의 `list()`와 동일하게 서비스
레이어에서 스트림 필터링 + `limit` 적용 (실데이터 규모가 커지면 쿼리로 이전 — Course 설계 문서와
동일한 전제).

### Service

**`PostService`**

- `list(attachedCourseId, q, tag, sort, limit)`: `findAllByOrderByCreatedAtDesc()` → `attachedCourseId`
  일치 필터 → `q`는 `title` 또는 `body` 부분 일치 → `tag`는 `tags.contains` → `sort=popularDesc`면
  `likeCount` 내림차순, 기본은 `createdAt` 내림차순(이미 정렬됨) → `limit` 적용 (기본 20, 최대 50,
  초과 시 `VALIDATION_ERROR`) → `PageInfo(null)`.
- `create(request)`: 로그인 필요. `title`/`body`/`tags` 길이 검증(Bean Validation, Course의
  `CreateCourseRequest`와 동일하게 `@Size` 애노테이션으로 처리 — 별도 Validator 클래스는 만들지
  않는다, Course처럼 좌표 교차검증 같은 복잡한 규칙이 없으므로 YAGNI). `attachedCourseId`가 있으면
  `CourseRepository.findById`로 조회, 없으면 `NOT_FOUND`, `visibility != PUBLIC`이면 `NOT_FOUND`
  (문서: "첨부할 수 있는 코스는 PUBLIC 코스만 허용" — 비공개 코스 첨부 시도는 "존재하지 않는 첨부
  대상"과 동일하게 취급).
- `getById(postId)`: `Optional` 인증. `likedByMe`는 로그인 시에만 조회.
- `update(postId, request)`: 작성자 본인만, 전송한 필드만 갱신, `tags`는 전체 교체,
  `attachedCourseId`는 `null` 전송 시 첨부 해제, 새 값 전송 시 위와 동일한 PUBLIC 검증.
- `delete(postId)`: 작성자 본인만. `commentRepository.deleteByPostId(postId)` 먼저 실행 후 게시글
  삭제 (문서: "게시글 삭제 시 해당 게시글의 댓글도 더 이상 목록에 노출하지 않습니다" — 고아 행을
  남기지 않기 위해 실제로 cascade 삭제한다).

**`CommentService`**

- `list(postId, limit)`: 게시글 존재 확인(`NOT_FOUND`) 후 `findByPostIdOrderByCreatedAtAsc` +
  `limit` 적용, `PageInfo(null)`.
- `create(postId, request)`: 로그인 필요, 게시글 존재 확인, `body` 길이 검증, 생성 후
  `Post.commentCount` 증가.
- `update(commentId, request)`: 작성자 본인만.
- `delete(commentId)`: 작성자 본인만, 삭제 후 대상 게시글의 `commentCount` 감소(게시글이 이미 삭제된
  상태면 스킵 — 게시글 삭제 경로에서 댓글이 먼저 일괄 삭제되므로 개별 댓글 삭제 API로는 도달하지
  않는 케이스).

**`LikeService`** (신규 — 기존 `Like`/`LikeRepository`/`LikeTargetType` 재사용)

- `targetType` 경로 파라미터 문자열(`courses`/`posts`) → `LikeTargetType`(`COURSE`/`POST`) 매핑,
  매핑 실패 시 `VALIDATION_ERROR`.
- `like(targetType, targetId)`: 대상 존재 확인(Course 또는 Post, `NOT_FOUND`), `existsById`로 중복
  체크 후 없으면 `Like` 저장 + 대상의 `likeCount` 증가(중복이면 저장/증가 스킵, 응답은 동일하게
  `liked=true`로 멱등 처리).
- `unlike(targetType, targetId)`: 존재하면 삭제 + `likeCount` 감소(0 미만 방지), 없으면 스킵하고
  `liked=false`로 응답.
- Course/Post 조회는 각각 `CourseRepository`/`PostRepository`에 위임.

### Controller & 라우팅

- `PostController`: `GET /api/posts`, `POST /api/posts`, `GET /api/posts/{postId}`,
  `PATCH /api/posts/{postId}`, `DELETE /api/posts/{postId}`
- `CommentController`: `GET /api/posts/{postId}/comments`, `POST /api/posts/{postId}/comments`,
  `PATCH /api/comments/{commentId}`, `DELETE /api/comments/{commentId}`
- `LikeController`: `PUT /api/likes/{targetType}/{targetId}`, `DELETE /api/likes/{targetType}/{targetId}`

응답 포맷은 Course와 동일하게 `Map.of("post", ...)` / `Map.of("posts", ..., "pageInfo", ...)` 형태.

### SecurityConfig 변경

`GlobalSecurityConfig`의 `authorizeHttpRequests`에 아래를 추가한다 (Optional 엔드포인트만
`permitAll`, 나머지는 기존 `anyRequest().authenticated()`로 커버됨):

```java
.requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/{postId}").permitAll()
.requestMatchers(HttpMethod.GET, "/api/posts/{postId}/comments").permitAll()
```

### 에러 코드 매핑

문서의 에러 케이스를 `ApiException`으로 그대로 구현한다 (`VALIDATION_ERROR`/`UNAUTHORIZED`/
`FORBIDDEN`/`NOT_FOUND`) — Course 모듈과 동일한 방식.

## 모바일 연동 설계

### 서비스 레이어 교체

`courseApi.ts` 패턴(실제 `fetch`, `parseApiErrorMessage`, `EXPO_PUBLIC_API_BASE_URL`)을 그대로
따른다. 기존 mock 파일 최상단의 "MOCK 구현" 주석과 `incrementCommentCount`/`updateLikeState`
헬퍼(모듈 간 mock 상태 동기화용)는 제거한다 — 실제 백엔드가 매 응답에 최신 `likeCount`/
`commentCount`/`likedByMe`를 내려주므로 클라이언트 쪽 상태 동기화 헬퍼가 필요 없다.

**`postApi.ts`**: `getPosts(params)`, `getPost(postId)`, `createPost(body, accessToken)` — `author`
파라미터 제거(백엔드가 JWT로 작성자를 판별).

**`commentApi.ts`**: `getComments(postId)`, `createComment(postId, body, accessToken)` — 동일하게
`author` 파라미터 제거.

**`likeApi.ts`**: `putLike(postId, accessToken)`, `deleteLike(postId, accessToken)` — 내부적으로
`PUT/DELETE /api/likes/posts/{postId}` 호출 (targetType 하드코딩 `'posts'`, 기존 mock의 범위 노트와
동일).

### 호출부 수정

- [`PostCreateScreen.tsx`](../../mobile/src/screens/PostCreateScreen.tsx): `createPost` 호출에서
  세 번째 인자(`user`로부터 만든 `PublicProfile`) 제거.
- [`PostDetailScreen.tsx`](../../mobile/src/screens/PostDetailScreen.tsx): `createComment` 호출에서
  네 번째 인자(author) 제거. `putLike`/`deleteLike` 호출은 시그니처 변화 없음.

두 화면 모두 `useAuth()`의 `user`는 여전히 "로그인 여부"와 "댓글 입력창 표시 여부" 판단에 쓰이므로
import 자체는 유지한다.

## 테스트 계획

- 백엔드: `PostServiceTest`, `CommentServiceTest`, `LikeServiceTest` (단위, Course 모듈에는 없지만
  auth/user 모듈 수준의 커버리지를 목표로 함) + 각 컨트롤러 통합 테스트(`MockMvc`, 인증/권한 케이스
  포함: 비로그인 작성 401, 타인 게시글 수정/삭제 403, 존재하지 않는 첨부 코스 404, 비공개 코스
  첨부 404, 중복 좋아요 멱등, 좋아요 취소 후 재좋아요).
- 모바일: `npx tsc --noEmit`, `npx expo start` bundle 200 확인, 실기기/시뮬레이터에서 게시글 작성 →
  상세 진입 → 좋아요 토글 → 댓글 작성 → 게시판/코스별 게시판 목록에 반영 확인.
- 백엔드 실행 후 모바일과 동일한 예시 요청/응답으로 왕복 확인 (`CLAUDE.md` "백엔드와 모바일이 같은
  예시 요청과 응답으로 동작하는지 확인" 원칙).

## 완료 기준

- `docs/api-contract.md`의 Post/Comment/Like 섹션에 정의된 모든 엔드포인트가 백엔드에 구현되고
  테스트를 통과한다.
- 모바일 3개 서비스 파일이 mock 없이 실제 백엔드와 통신하고, 기존 화면 동작(목록/상세/좋아요/댓글)이
  실기기에서 그대로 재현된다.
- 완료 후 `backend/docs/implementations/`와 `mobile/docs/implementations/`에 각각 구현 기록을
  남긴다 (레포 관례).
