# 커뮤니티 게시글 Post/Comment/Like API

## 배경

`docs/api-contract.md`, `docs/data-model.md`에 정의된 Post/Comment/Like API 계약을 구현했다.
모바일은 이미 UI(BoardScreen, CourseBoardScreen, PostCreateScreen, PostDetailScreen)를 갖추고
in-memory mock으로 동작 중이었다 — 이번 작업으로 mock을 걷어내고 실제 백엔드와 연동한다.

## 설계 결정

- `community` 패키지(기존 `Like` 엔티티가 있던 곳)에 `Post`/`Comment` 엔티티를 추가하고, Course
  모듈과 동일한 패턴(`ApiException`/`ErrorCode`, `CurrentUserProvider`, 트랜잭션 경계)을 따랐다.
- `PublicProfile.id`는 `/me` 응답(`UserResponse`)과 동일하게 `"user_" + UUID` 포맷으로 통일했다.
  `Course.authorId`는 raw UUID(prefix 없음)인 기존 불일치가 있으나 이미 배포된 모듈이라 손대지
  않았다.
- 커서 페이지네이션은 Course 목록과 동일하게 항상 `null` (메모리 필터링 + `limit`만 적용).
- 게시글 삭제 시 댓글은 DB의 `ON DELETE CASCADE` FK로 정리한다 (애플리케이션 코드에서 별도로
  지우지 않는다).
- `Like` API는 문서 그대로 `courses`/`posts` 둘 다 지원하는 범용 구현이지만, 모바일은 게시글
  좋아요만 사용한다.

## 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `db/migration/V4__create_posts_comments.sql` | 신규 — `posts`, `post_tags`, `comments` 테이블 |
| `backend/community/Post.java`, `PostRepository.java` | 신규 엔티티/리포지토리 |
| `backend/community/Comment.java`, `CommentRepository.java` | 신규 엔티티/리포지토리 |
| `backend/community/PostService.java`, `PostController.java` | 게시글 CRUD |
| `backend/community/CommentService.java`, `CommentController.java` | 댓글 CRUD |
| `backend/community/LikeService.java`, `LikeController.java` | 좋아요 토글 (courses/posts 공용) |
| `user/dto/PublicProfileResponse.java` | 신규 — Post/Comment 작성자 공개 프로필 매핑 |
| `global/security/SecurityConfig.java` | `GET /api/posts`, `GET /api/posts/{id}`, `GET /api/posts/{id}/comments` permitAll 추가 |

## 참고

- 관련 스펙: `docs/superpowers/specs/2026-07-05-community-post-backend-design.md`
