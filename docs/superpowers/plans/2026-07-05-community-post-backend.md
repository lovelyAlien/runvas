# 커뮤니티 게시글 Post/Comment/Like 백엔드 구현 + 모바일 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docs/api-contract.md`에 정의된 Post/Comment/Like API를 `backend/`에 구현하고, `mobile/`의
3개 mock 서비스(`postApi.ts`, `commentApi.ts`, `likeApi.ts`)를 실제 `fetch` 호출로 교체해 모바일
커뮤니티 UI와 백엔드를 연동한다.

**Architecture:** Course 모듈과 동일한 패턴(`ApiException`/`ErrorCode`, `CurrentUserProvider`, 트랜잭션
경계, 비정규화 카운터)을 따라 `com.runvas.backend.community` 패키지에 `Post`/`Comment` 엔티티와
서비스/컨트롤러를 추가하고, 기존 `Like`/`LikeRepository`를 재사용하는 `LikeService`/`LikeController`를
새로 만든다. 모바일은 `courseApi.ts` 패턴(실제 `fetch`, `parseApiErrorMessage`)을 그대로 따른다.

**Tech Stack:** Spring Boot 3.3.7 / Java 21, Spring Data JPA, Flyway(PostgreSQL), Spring Security(JWT),
JUnit 5 + MockMvc + Testcontainers(PostgreSQL) / React Native(Expo) + TypeScript, `fetch`

## Global Constraints

- 요청/응답 필드명·타입·상태 코드·에러 코드는 `docs/api-contract.md`를 그대로 따른다 (문서에 없는
  필드를 임의로 추가하지 않는다).
- 에러 처리는 `com.runvas.backend.common.ApiException`/`ErrorCode` 사용 (Course/community 모듈과
  동일 계열 — `auth`/`user` 모듈의 `RunvasException`과는 다른 계열이지만 `GlobalExceptionHandler`가
  둘 다 처리한다).
- `PublicProfile.id`는 `"user_" + UUID` 포맷 (`/me` 응답과 동일 — `docs/superpowers/specs/2026-07-05-community-post-backend-design.md` "ID 포맷 결정" 참고).
- 인증이 필요한 엔드포인트는 `CurrentUserProvider.requireUserId()`, Optional 엔드포인트는
  `currentUserIdOrNull()` 사용.
- 커서 페이지네이션은 항상 `nextCursor: null` (Course 목록과 동일한 MVP 범위 축소 — `docs/superpowers/specs/2026-07-05-community-post-backend-design.md` "페이지네이션" 참고).
- `PATCH` 요청의 선택 필드는 Course 모듈과 동일하게 `null`과 "생략"을 구분하지 않는다 (필드가
  `null`이면 수정하지 않음 — 기존 `UpdateCourseRequest` 관례를 따른다).
- 커밋 메시지에 `Co-Authored-By` 등 도구/저작자 표시를 넣지 않는다 (`CLAUDE.md` Git 작업 규칙,
  로컬 `commit-msg` 훅이 검사함 — 새 워크트리라면 먼저 `sh scripts/setup-git-hooks.sh` 실행).
- 백엔드 테스트는 Docker가 필요하다 (`@Testcontainers(disabledWithoutDocker = true)` — Docker
  없으면 자동 스킵되고 실패하지 않는다. 로컬에 Docker가 없다면 실행 전에 확인한다).

---

## Task 1: Post/Comment 엔티티, 리포지토리, DB 마이그레이션

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__create_posts_comments.sql`
- Create: `backend/src/main/java/com/runvas/backend/community/Post.java`
- Create: `backend/src/main/java/com/runvas/backend/community/PostRepository.java`
- Create: `backend/src/main/java/com/runvas/backend/community/Comment.java`
- Create: `backend/src/main/java/com/runvas/backend/community/CommentRepository.java`
- Create: `backend/src/main/java/com/runvas/user/dto/PublicProfileResponse.java`
- Test: `backend/src/test/java/com/runvas/backend/community/PostRepositoryTest.java`

**Interfaces:**
- Produces: `Post(String authorId, String title, String body, String attachedCourseId, Set<String> tags)`
  생성자, `Post.getId()/getAuthorId()/getTitle()/getBody()/getAttachedCourseId()/getTags()/getLikeCount()/getCommentCount()/getCreatedAt()/getUpdatedAt()`,
  `Post.setTitle/setBody/setAttachedCourseId/setUpdatedAt`, `Post.replaceTags(Set<String>)`,
  `Post.incrementCommentCount()/decrementCommentCount()/incrementLikeCount()/decrementLikeCount()`.
  `Comment(String postId, String authorId, String body)` 생성자, getter 전체 + `setBody`/`setUpdatedAt`.
  `PostRepository.findAllByOrderByCreatedAtDesc()`. `CommentRepository.findByPostIdOrderByCreatedAtAsc(String postId)`.
  `PublicProfileResponse.from(User user)` → `PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio)`.

- [ ] **Step 1: DB 마이그레이션 작성**

`backend/src/main/resources/db/migration/V4__create_posts_comments.sql`:

```sql
CREATE TABLE posts (
    id VARCHAR(36) PRIMARY KEY,
    author_id VARCHAR(36) NOT NULL,
    title VARCHAR(80) NOT NULL,
    body TEXT NOT NULL,
    attached_course_id VARCHAR(36),
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE post_tags (
    post_id VARCHAR(36) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag VARCHAR(20) NOT NULL,
    PRIMARY KEY (post_id, tag)
);

CREATE TABLE comments (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id VARCHAR(36) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_posts_author ON posts (author_id);
CREATE INDEX idx_posts_attached_course ON posts (attached_course_id);
CREATE INDEX idx_comments_post ON comments (post_id);
```

`comments.post_id`에 `ON DELETE CASCADE` FK를 걸어 게시글 삭제 시 댓글이 DB 레벨에서 함께
삭제되도록 한다 (`course_tags`가 `courses`에 거는 방식과 동일 — 애플리케이션 코드에서 댓글을
따로 지우지 않아도 된다).

- [ ] **Step 2: Post 엔티티 작성**

`backend/src/main/java/com/runvas/backend/community/Post.java`:

```java
package com.runvas.backend.community;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// docs/data-model.md Post와 1:1. likeCount/commentCount는 저장하는 비정규화 카운터
// (Course.likeCount와 동일한 이유 — Like/Comment 테이블을 매번 세지 않는다).
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String authorId;

	@Column(nullable = false, length = 80)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	private String attachedCourseId;

	@ElementCollection
	@CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
	@Column(name = "tag", length = 20)
	private Set<String> tags = new LinkedHashSet<>();

	@Column(nullable = false)
	private Integer likeCount = 0;

	@Column(nullable = false)
	private Integer commentCount = 0;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public Post(String authorId, String title, String body, String attachedCourseId, Set<String> tags) {
		this.authorId = authorId;
		this.title = title;
		this.body = body;
		this.attachedCourseId = attachedCourseId;
		this.tags = new LinkedHashSet<>(tags);
	}

	public void replaceTags(Set<String> newTags) {
		this.tags = new LinkedHashSet<>(newTags);
	}

	public void incrementCommentCount() {
		this.commentCount = this.commentCount + 1;
	}

	public void decrementCommentCount() {
		this.commentCount = Math.max(0, this.commentCount - 1);
	}

	public void incrementLikeCount() {
		this.likeCount = this.likeCount + 1;
	}

	public void decrementLikeCount() {
		this.likeCount = Math.max(0, this.likeCount - 1);
	}
}
```

- [ ] **Step 3: PostRepository 작성**

`backend/src/main/java/com/runvas/backend/community/PostRepository.java`:

```java
package com.runvas.backend.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, String> {
	List<Post> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: Comment 엔티티 + 리포지토리 작성**

`backend/src/main/java/com/runvas/backend/community/Comment.java`:

```java
package com.runvas.backend.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// docs/data-model.md Comment와 1:1.
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String postId;

	@Column(nullable = false)
	private String authorId;

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	public Comment(String postId, String authorId, String body) {
		this.postId = postId;
		this.authorId = authorId;
		this.body = body;
	}
}
```

`backend/src/main/java/com/runvas/backend/community/CommentRepository.java`:

```java
package com.runvas.backend.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, String> {
	List<Comment> findByPostIdOrderByCreatedAtAsc(String postId);
}
```

- [ ] **Step 5: PublicProfileResponse dto 작성**

`backend/src/main/java/com/runvas/user/dto/PublicProfileResponse.java`:

```java
package com.runvas.user.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1. Post/Comment 작성자 정보에 쓴다.
// id는 UserResponse.from()과 동일하게 "user_" + UUID 포맷으로 통일한다.
public record PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio) {
	public static PublicProfileResponse from(User user) {
		return new PublicProfileResponse(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}
}
```

- [ ] **Step 6: 영속성 테스트 작성**

`backend/src/test/java/com/runvas/backend/community/PostRepositoryTest.java`:

```java
package com.runvas.backend.community;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	PostRepository postRepository;

	@Autowired
	CommentRepository commentRepository;

	@Test
	void savesAndFindsPostWithTags() {
		Post post = new Post("author-1", "한강 하트 코스 후기", "평탄해서 좋았습니다", null, Set.of("hangang", "heart"));
		postRepository.saveAndFlush(post);

		List<Post> found = postRepository.findAllByOrderByCreatedAtDesc();

		assertThat(found).hasSize(1);
		assertThat(found.get(0).getTitle()).isEqualTo("한강 하트 코스 후기");
		assertThat(found.get(0).getTags()).containsExactlyInAnyOrder("hangang", "heart");
		assertThat(found.get(0).getLikeCount()).isEqualTo(0);
		assertThat(found.get(0).getCommentCount()).isEqualTo(0);
	}

	@Test
	void savesAndFindsCommentsByPostIdInCreatedOrder() {
		Post post = postRepository.saveAndFlush(new Post("author-1", "제목", "본문", null, Set.of()));

		commentRepository.saveAndFlush(new Comment(post.getId(), "author-2", "첫 댓글"));
		commentRepository.saveAndFlush(new Comment(post.getId(), "author-3", "두 번째 댓글"));

		List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId());

		assertThat(comments).hasSize(2);
		assertThat(comments.get(0).getBody()).isEqualTo("첫 댓글");
		assertThat(comments.get(1).getBody()).isEqualTo("두 번째 댓글");
	}

	@Test
	void deletingPostCascadesComments() {
		Post post = postRepository.saveAndFlush(new Post("author-1", "제목", "본문", null, Set.of()));
		commentRepository.saveAndFlush(new Comment(post.getId(), "author-2", "댓글"));

		postRepository.delete(post);
		postRepository.flush();

		assertThat(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).isEmpty();
	}
}
```

- [ ] **Step 7: 테스트 실행 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostRepositoryTest"`
Expected: BUILD SUCCESSFUL, 3 tests passed (Docker 필요 — 없으면 테스트가 스킵된다).

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/resources/db/migration/V4__create_posts_comments.sql \
        backend/src/main/java/com/runvas/backend/community/Post.java \
        backend/src/main/java/com/runvas/backend/community/PostRepository.java \
        backend/src/main/java/com/runvas/backend/community/Comment.java \
        backend/src/main/java/com/runvas/backend/community/CommentRepository.java \
        backend/src/main/java/com/runvas/user/dto/PublicProfileResponse.java \
        backend/src/test/java/com/runvas/backend/community/PostRepositoryTest.java
git commit -m "feat(backend): Post/Comment 엔티티와 리포지토리 추가"
```

---

## Task 2: Post CRUD (서비스, 컨트롤러, 보안 설정, 테스트)

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/community/dto/CreatePostRequest.java`
- Create: `backend/src/main/java/com/runvas/backend/community/dto/UpdatePostRequest.java`
- Create: `backend/src/main/java/com/runvas/backend/community/dto/PostResponse.java`
- Create: `backend/src/main/java/com/runvas/backend/community/PostService.java`
- Create: `backend/src/main/java/com/runvas/backend/community/PostController.java`
- Modify: `backend/src/main/java/com/runvas/global/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/runvas/backend/community/PostControllerTest.java`

**Interfaces:**
- Consumes (Task 1): `Post` 엔티티/생성자, `PostRepository.findAllByOrderByCreatedAtDesc()`,
  `PublicProfileResponse.from(User)`. `com.runvas.backend.course.CourseRepository`(기존),
  `com.runvas.backend.course.Course.getVisibility()`(기존), `com.runvas.backend.community.Like.LikeId`,
  `LikeTargetType`(기존), `com.runvas.backend.auth.CurrentUserProvider`(기존),
  `com.runvas.user.repository.UserRepository`(기존).
- Produces: `PostService.create(CreatePostRequest)`, `getById(String postId)`,
  `list(String attachedCourseId, String q, String tag, String sort, Integer limit)` →
  `PostService.ListResult(List<PostResponse> posts, PageInfo pageInfo)`,
  `update(String postId, UpdatePostRequest)`, `delete(String postId)`. 이 메서드들은 Task 3(Comment),
  Task 4(Like)에서 `postRepository`/`Post` 타입을 그대로 재사용한다.

- [ ] **Step 1: 요청/응답 DTO 작성**

`backend/src/main/java/com/runvas/backend/community/dto/CreatePostRequest.java`:

```java
package com.runvas.backend.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

// docs/api-contract.md POST /posts 요청 본문, docs/data-model.md 커뮤니티 제한값.
public record CreatePostRequest(
		@NotNull @Size(min = 1, max = 80) String title,
		@NotNull @Size(min = 1, max = 5000) String body,
		String attachedCourseId,
		@Size(max = 10) Set<String> tags) {
}
```

`backend/src/main/java/com/runvas/backend/community/dto/UpdatePostRequest.java`:

```java
package com.runvas.backend.community.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;

// docs/api-contract.md PATCH /posts/{id} — 전송한 필드만 수정, tags/attachedCourseId는 전체 교체.
// UpdateCourseRequest와 동일하게 null과 생략을 구분하지 않는다 (기존 Course 모듈 관례).
public record UpdatePostRequest(
		@Size(min = 1, max = 80) String title,
		@Size(min = 1, max = 5000) String body,
		String attachedCourseId,
		@Size(max = 10) Set<String> tags) {
}
```

`backend/src/main/java/com/runvas/backend/community/dto/PostResponse.java`:

```java
package com.runvas.backend.community.dto;

import com.runvas.backend.community.Post;
import com.runvas.user.dto.PublicProfileResponse;
import java.time.Instant;
import java.util.Set;

// docs/api-contract.md GET/POST /posts의 post 객체.
public record PostResponse(
		String id,
		PublicProfileResponse author,
		String title,
		String body,
		String attachedCourseId,
		Set<String> tags,
		Integer likeCount,
		boolean likedByMe,
		Integer commentCount,
		Instant createdAt,
		Instant updatedAt) {

	public static PostResponse from(Post post, PublicProfileResponse author, boolean likedByMe) {
		return new PostResponse(
				post.getId(),
				author,
				post.getTitle(),
				post.getBody(),
				post.getAttachedCourseId(),
				// tags는 지연 로딩 컬렉션 — 트랜잭션이 끝난 뒤(Jackson 직렬화 시점) 접근하면
				// LazyInitializationException이 나므로, 트랜잭션 안에서 즉시 복사해 둔다 (Course와 동일 이유).
				Set.copyOf(post.getTags()),
				post.getLikeCount(),
				likedByMe,
				post.getCommentCount(),
				post.getCreatedAt(),
				post.getUpdatedAt());
	}
}
```

- [ ] **Step 2: PostService 작성**

`backend/src/main/java/com/runvas/backend/community/PostService.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.CreatePostRequest;
import com.runvas.backend.community.dto.PostResponse;
import com.runvas.backend.community.dto.UpdatePostRequest;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.dto.PublicProfileResponse;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 50;

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final LikeRepository likeRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public PostResponse create(CreatePostRequest request) {
		String authorId = currentUserProvider.requireUserId();
		validateAttachedCourse(request.attachedCourseId());

		Post post = new Post(
				authorId,
				request.title(),
				request.body(),
				request.attachedCourseId(),
				request.tags() == null ? Set.of() : request.tags());

		postRepository.save(post);
		return toResponse(post, false);
	}

	@Transactional(readOnly = true)
	public PostResponse getById(String postId) {
		Post post = findPostOrThrow(postId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		return toResponse(post, isLikedByCurrentUser(post.getId(), currentUserId));
	}

	@Transactional(readOnly = true)
	public ListResult list(String attachedCourseId, String q, String tag, String sort, Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (limit != null && limit > MAX_LIMIT) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be at most " + MAX_LIMIT);
		}
		if (sort != null && !sort.equals("createdAtDesc") && !sort.equals("popularDesc")) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported sort: " + sort);
		}

		String currentUserId = currentUserProvider.currentUserIdOrNull();

		List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc().stream()
				.filter(post -> attachedCourseId == null || attachedCourseId.equals(post.getAttachedCourseId()))
				.filter(post -> q == null || post.getTitle().contains(q) || post.getBody().contains(q))
				.filter(post -> tag == null || post.getTags().contains(tag))
				.toList();

		if ("popularDesc".equals(sort)) {
			posts = posts.stream()
					.sorted(Comparator.comparing(Post::getLikeCount).reversed())
					.toList();
		}

		List<PostResponse> responses = posts.stream()
				.limit(effectiveLimit)
				.map(post -> toResponse(post, isLikedByCurrentUser(post.getId(), currentUserId)))
				.toList();

		return new ListResult(responses, new PageInfo(null));
	}

	@Transactional
	public PostResponse update(String postId, UpdatePostRequest request) {
		Post post = findPostOrThrow(postId);
		requireAuthor(post);

		if (request.title() != null) post.setTitle(request.title());
		if (request.body() != null) post.setBody(request.body());
		if (request.tags() != null) post.replaceTags(request.tags());
		if (request.attachedCourseId() != null) {
			validateAttachedCourse(request.attachedCourseId());
			post.setAttachedCourseId(request.attachedCourseId());
		}

		post.setUpdatedAt(Instant.now());
		return toResponse(post, isLikedByCurrentUser(post.getId(), post.getAuthorId()));
	}

	@Transactional
	public void delete(String postId) {
		Post post = findPostOrThrow(postId);
		requireAuthor(post);
		postRepository.delete(post);
	}

	private void validateAttachedCourse(String attachedCourseId) {
		if (attachedCourseId == null) return;
		Course course = courseRepository.findById(attachedCourseId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "첨부 코스가 없습니다"));
		if (course.getVisibility() != CourseVisibility.PUBLIC) {
			throw new ApiException(ErrorCode.NOT_FOUND, "첨부 코스가 없습니다");
		}
	}

	private Post findPostOrThrow(String postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다"));
	}

	private void requireAuthor(Post post) {
		String currentUserId = currentUserProvider.requireUserId();
		if (!post.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "작성자가 아닙니다");
		}
	}

	private boolean isLikedByCurrentUser(String postId, String currentUserId) {
		if (currentUserId == null) return false;
		return likeRepository.existsById(new Like.LikeId(currentUserId, LikeTargetType.POST, postId));
	}

	private PostResponse toResponse(Post post, boolean likedByMe) {
		User author = userRepository.findById(UUID.fromString(post.getAuthorId()))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return PostResponse.from(post, PublicProfileResponse.from(author), likedByMe);
	}

	public record ListResult(List<PostResponse> posts, PageInfo pageInfo) {
	}
}
```

- [ ] **Step 3: PostController 작성**

`backend/src/main/java/com/runvas/backend/community/PostController.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.community.dto.CreatePostRequest;
import com.runvas.backend.community.dto.PostResponse;
import com.runvas.backend.community.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@PostMapping
	public ResponseEntity<Map<String, PostResponse>> create(@Valid @RequestBody CreatePostRequest request) {
		PostResponse post = postService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("post", post));
	}

	@GetMapping
	public Map<String, Object> list(
			@RequestParam(required = false) String attachedCourseId,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String tag,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		PostService.ListResult result = postService.list(attachedCourseId, q, tag, sort, limit);
		return Map.of("posts", result.posts(), "pageInfo", result.pageInfo());
	}

	@GetMapping("/{postId}")
	public Map<String, PostResponse> getById(@PathVariable String postId) {
		return Map.of("post", postService.getById(postId));
	}

	@PatchMapping("/{postId}")
	public Map<String, PostResponse> update(
			@PathVariable String postId, @Valid @RequestBody UpdatePostRequest request) {
		return Map.of("post", postService.update(postId, request));
	}

	@DeleteMapping("/{postId}")
	public ResponseEntity<Void> delete(@PathVariable String postId) {
		postService.delete(postId);
		return ResponseEntity.noContent().build();
	}
}
```

- [ ] **Step 4: SecurityConfig에 Post/Comment Optional 라우팅 추가**

`backend/src/main/java/com/runvas/global/security/SecurityConfig.java`의 `authorizeHttpRequests`
블록에서 아래 줄을:

```java
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/{courseId}").permitAll()
```

아래로 교체 (바로 다음 줄에 posts/comments permitAll 2줄 추가):

```java
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/{courseId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/{postId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/{postId}/comments").permitAll()
```

(`/api/posts/{postId}/comments` GET은 Task 3의 `CommentController`가 아직 없어도 라우팅 규칙만
먼저 추가해 두는 것 — 문제 없다. Post/Comment API 전체의 인증 정책을 한 번에 정리하기 위함.)

- [ ] **Step 5: PostControllerTest 작성**

`backend/src/test/java/com/runvas/backend/community/PostControllerTest.java`:

```java
package com.runvas.backend.community;

import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.JwtProvider;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PostControllerTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
		registry.add("runvas.jwt.expiration-seconds", () -> "3600");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String createUserAndToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	private Course savePublicCourse() {
		return courseRepository.saveAndFlush(new Course(
				"author-x",
				"테스트 코스",
				null,
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				200,
				120,
				new GeoBounds(new GeoPoint(37.0, 127.0), new GeoPoint(37.001, 127.001)),
				CourseVisibility.PUBLIC,
				Set.of()));
	}

	private Course savePrivateCourse() {
		Course course = savePublicCourse();
		course.setVisibility(CourseVisibility.PRIVATE);
		return courseRepository.saveAndFlush(course);
	}

	private String createPost(String accessToken, String title, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "%s", "body": "%s" }
								""".formatted(title, body)))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	@Test
	void createsPostAndReturnsDocumentedResponse() throws Exception {
		String accessToken = createUserAndToken("author1");

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "한강 하트 코스 후기",
								  "body": "초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.",
								  "tags": ["hangang", "heart"]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.post.title").value("한강 하트 코스 후기"))
				.andExpect(jsonPath("$.post.author.id").isString())
				.andExpect(jsonPath("$.post.likeCount").value(0))
				.andExpect(jsonPath("$.post.commentCount").value(0))
				.andExpect(jsonPath("$.post.likedByMe").value(false));
	}

	@Test
	void createWithoutAuthReturns401() throws Exception {
		mockMvc.perform(post("/api/posts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "제목", "body": "본문" }
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createWithTooLongTitleReturns400() throws Exception {
		String accessToken = createUserAndToken("author2");
		String longTitle = "a".repeat(81);

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "%s", "body": "본문" }
								""".formatted(longTitle)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createWithPrivateAttachedCourseReturns404() throws Exception {
		String accessToken = createUserAndToken("author3");
		Course privateCourse = savePrivateCourse();

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "제목", "body": "본문", "attachedCourseId": "%s" }
								""".formatted(privateCourse.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void listReturnsCreatedAtDescendingAndFiltersByAttachedCourseId() throws Exception {
		String accessToken = createUserAndToken("author4");
		Course course = savePublicCourse();

		mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "코스 첨부 글", "body": "본문", "attachedCourseId": "%s" }
								""".formatted(course.getId())))
				.andExpect(status().isCreated());

		createPost(accessToken, "일반 글", "본문");

		mockMvc.perform(get("/api/posts").param("attachedCourseId", course.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.posts.length()").value(1))
				.andExpect(jsonPath("$.posts[0].title").value("코스 첨부 글"));
	}

	@Test
	void listRejectsUnsupportedSort() throws Exception {
		mockMvc.perform(get("/api/posts").param("sort", "unknownSort"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void getByIdReturnsNotFoundForUnknownPost() throws Exception {
		mockMvc.perform(get("/api/posts/unknown-post-id"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void updateByAuthorSucceeds() throws Exception {
		String accessToken = createUserAndToken("author5");
		String postId = createPost(accessToken, "원래 제목", "원래 본문");

		mockMvc.perform(patch("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "수정한 제목" }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.post.title").value("수정한 제목"))
				.andExpect(jsonPath("$.post.body").value("원래 본문"));
	}

	@Test
	void updateByNonAuthorReturns403() throws Exception {
		String ownerToken = createUserAndToken("owner1");
		String otherToken = createUserAndToken("other1");
		String postId = createPost(ownerToken, "제목", "본문");

		mockMvc.perform(patch("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "다른 사람이 수정 시도" }
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void deleteByAuthorRemovesPost() throws Exception {
		String accessToken = createUserAndToken("author6");
		String postId = createPost(accessToken, "삭제될 글", "본문");

		mockMvc.perform(delete("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteByNonAuthorReturns403() throws Exception {
		String ownerToken = createUserAndToken("owner2");
		String otherToken = createUserAndToken("other2");
		String postId = createPost(ownerToken, "제목", "본문");

		mockMvc.perform(delete("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isForbidden());
	}
}
```

- [ ] **Step 6: 테스트 실행 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostControllerTest"`
Expected: BUILD SUCCESSFUL, 11 tests passed.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/dto/CreatePostRequest.java \
        backend/src/main/java/com/runvas/backend/community/dto/UpdatePostRequest.java \
        backend/src/main/java/com/runvas/backend/community/dto/PostResponse.java \
        backend/src/main/java/com/runvas/backend/community/PostService.java \
        backend/src/main/java/com/runvas/backend/community/PostController.java \
        backend/src/main/java/com/runvas/global/security/SecurityConfig.java \
        backend/src/test/java/com/runvas/backend/community/PostControllerTest.java
git commit -m "feat(backend): 게시글 조회/작성/수정/삭제 API 구현"
```

---

## Task 3: Comment CRUD (서비스, 컨트롤러, 테스트)

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/community/dto/CreateCommentRequest.java`
- Create: `backend/src/main/java/com/runvas/backend/community/dto/UpdateCommentRequest.java`
- Create: `backend/src/main/java/com/runvas/backend/community/dto/CommentResponse.java`
- Create: `backend/src/main/java/com/runvas/backend/community/CommentService.java`
- Create: `backend/src/main/java/com/runvas/backend/community/CommentController.java`
- Test: `backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java`

**Interfaces:**
- Consumes (Task 1/2): `Comment` 엔티티/생성자, `CommentRepository.findByPostIdOrderByCreatedAtAsc`,
  `PostRepository`(Task 1), `Post.incrementCommentCount()/decrementCommentCount()`(Task 1),
  `PublicProfileResponse.from(User)`(Task 1), `CurrentUserProvider`(기존), `UserRepository`(기존).
  `POST /api/posts` (Task 2, 테스트에서 댓글을 달 게시글을 만드는 데 사용).
- Produces: `CommentService.create(String postId, CreateCommentRequest)`, `list(String postId, Integer limit)` →
  `CommentService.ListResult(List<CommentResponse> comments, PageInfo pageInfo)`,
  `update(String commentId, UpdateCommentRequest)`, `delete(String commentId)`.

- [ ] **Step 1: 요청/응답 DTO 작성**

`backend/src/main/java/com/runvas/backend/community/dto/CreateCommentRequest.java`:

```java
package com.runvas.backend.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// docs/api-contract.md POST /posts/{postId}/comments 요청 본문.
public record CreateCommentRequest(@NotNull @Size(min = 1, max = 1000) String body) {
}
```

`backend/src/main/java/com/runvas/backend/community/dto/UpdateCommentRequest.java`:

```java
package com.runvas.backend.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// docs/api-contract.md PATCH /comments/{commentId} 요청 본문 — body는 필수.
public record UpdateCommentRequest(@NotNull @Size(min = 1, max = 1000) String body) {
}
```

`backend/src/main/java/com/runvas/backend/community/dto/CommentResponse.java`:

```java
package com.runvas.backend.community.dto;

import com.runvas.backend.community.Comment;
import com.runvas.user.dto.PublicProfileResponse;
import java.time.Instant;

// docs/api-contract.md GET/POST /posts/{postId}/comments의 comment 객체.
public record CommentResponse(
		String id,
		String postId,
		PublicProfileResponse author,
		String body,
		Instant createdAt,
		Instant updatedAt) {

	public static CommentResponse from(Comment comment, PublicProfileResponse author) {
		return new CommentResponse(
				comment.getId(),
				comment.getPostId(),
				author,
				comment.getBody(),
				comment.getCreatedAt(),
				comment.getUpdatedAt());
	}
}
```

- [ ] **Step 2: CommentService 작성**

`backend/src/main/java/com/runvas/backend/community/CommentService.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.CommentResponse;
import com.runvas.backend.community.dto.CreateCommentRequest;
import com.runvas.backend.community.dto.UpdateCommentRequest;
import com.runvas.user.domain.User;
import com.runvas.user.dto.PublicProfileResponse;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 50;

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public CommentResponse create(String postId, CreateCommentRequest request) {
		String authorId = currentUserProvider.requireUserId();
		Post post = findPostOrThrow(postId);

		Comment comment = new Comment(postId, authorId, request.body());
		commentRepository.save(comment);
		post.incrementCommentCount();

		return toResponse(comment);
	}

	@Transactional(readOnly = true)
	public ListResult list(String postId, Integer limit) {
		findPostOrThrow(postId);
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (limit != null && limit > MAX_LIMIT) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be at most " + MAX_LIMIT);
		}

		List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
				.limit(effectiveLimit)
				.map(this::toResponse)
				.toList();

		return new ListResult(comments, new PageInfo(null));
	}

	@Transactional
	public CommentResponse update(String commentId, UpdateCommentRequest request) {
		Comment comment = findCommentOrThrow(commentId);
		requireAuthor(comment);

		comment.setBody(request.body());
		comment.setUpdatedAt(Instant.now());
		return toResponse(comment);
	}

	@Transactional
	public void delete(String commentId) {
		Comment comment = findCommentOrThrow(commentId);
		requireAuthor(comment);

		commentRepository.delete(comment);
		postRepository.findById(comment.getPostId()).ifPresent(Post::decrementCommentCount);
	}

	private Post findPostOrThrow(String postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다"));
	}

	private Comment findCommentOrThrow(String commentId) {
		return commentRepository.findById(commentId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "댓글이 없습니다"));
	}

	private void requireAuthor(Comment comment) {
		String currentUserId = currentUserProvider.requireUserId();
		if (!comment.getAuthorId().equals(currentUserId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "작성자가 아닙니다");
		}
	}

	private CommentResponse toResponse(Comment comment) {
		User author = userRepository.findById(UUID.fromString(comment.getAuthorId()))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return CommentResponse.from(comment, PublicProfileResponse.from(author));
	}

	public record ListResult(List<CommentResponse> comments, PageInfo pageInfo) {
	}
}
```

- [ ] **Step 3: CommentController 작성**

`backend/src/main/java/com/runvas/backend/community/CommentController.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.community.dto.CommentResponse;
import com.runvas.backend.community.dto.CreateCommentRequest;
import com.runvas.backend.community.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@GetMapping("/api/posts/{postId}/comments")
	public Map<String, Object> list(
			@PathVariable String postId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		CommentService.ListResult result = commentService.list(postId, limit);
		return Map.of("comments", result.comments(), "pageInfo", result.pageInfo());
	}

	@PostMapping("/api/posts/{postId}/comments")
	public ResponseEntity<Map<String, CommentResponse>> create(
			@PathVariable String postId, @Valid @RequestBody CreateCommentRequest request) {
		CommentResponse comment = commentService.create(postId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("comment", comment));
	}

	@PatchMapping("/api/comments/{commentId}")
	public Map<String, CommentResponse> update(
			@PathVariable String commentId, @Valid @RequestBody UpdateCommentRequest request) {
		return Map.of("comment", commentService.update(commentId, request));
	}

	@DeleteMapping("/api/comments/{commentId}")
	public ResponseEntity<Void> delete(@PathVariable String commentId) {
		commentService.delete(commentId);
		return ResponseEntity.noContent().build();
	}
}
```

- [ ] **Step 4: CommentControllerTest 작성**

`backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java`:

```java
package com.runvas.backend.community;

import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.JwtProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CommentControllerTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
		registry.add("runvas.jwt.expiration-seconds", () -> "3600");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String createUserAndToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	private String createPost(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "댓글 테스트용 글", "body": "본문" }
								"""))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	private String createComment(String accessToken, String postId, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts/" + postId + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "%s" }
								""".formatted(body)))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.comment.id");
	}

	@Test
	void createCommentIncrementsPostCommentCount() throws Exception {
		String accessToken = createUserAndToken("commenter1");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/posts/" + postId + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "이 코스 저장해두고 뛰어볼게요." }
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comment.postId").value(postId))
				.andExpect(jsonPath("$.comment.author.id").isString());

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(jsonPath("$.post.commentCount").value(1));
	}

	@Test
	void createCommentOnUnknownPostReturns404() throws Exception {
		String accessToken = createUserAndToken("commenter2");

		mockMvc.perform(post("/api/posts/unknown-post/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "댓글" }
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void createCommentWithoutAuthReturns401() throws Exception {
		String accessToken = createUserAndToken("commenter3");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/posts/" + postId + "/comments")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "댓글" }
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listReturnsCommentsInCreatedOrder() throws Exception {
		String accessToken = createUserAndToken("commenter4");
		String postId = createPost(accessToken);

		createComment(accessToken, postId, "첫 댓글");
		createComment(accessToken, postId, "두 번째 댓글");

		mockMvc.perform(get("/api/posts/" + postId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments.length()").value(2))
				.andExpect(jsonPath("$.comments[0].body").value("첫 댓글"))
				.andExpect(jsonPath("$.comments[1].body").value("두 번째 댓글"));
	}

	@Test
	void updateByNonAuthorReturns403() throws Exception {
		String ownerToken = createUserAndToken("owner3");
		String otherToken = createUserAndToken("other3");
		String postId = createPost(ownerToken);
		String commentId = createComment(ownerToken, postId, "원본 댓글");

		mockMvc.perform(patch("/api/comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "body": "다른 사람이 수정 시도" }
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteByAuthorDecrementsPostCommentCount() throws Exception {
		String accessToken = createUserAndToken("commenter5");
		String postId = createPost(accessToken);
		String commentId = createComment(accessToken, postId, "삭제될 댓글");

		mockMvc.perform(delete("/api/comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(jsonPath("$.post.commentCount").value(0));
	}
}
```

- [ ] **Step 5: 테스트 실행 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.CommentControllerTest"`
Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/dto/CreateCommentRequest.java \
        backend/src/main/java/com/runvas/backend/community/dto/UpdateCommentRequest.java \
        backend/src/main/java/com/runvas/backend/community/dto/CommentResponse.java \
        backend/src/main/java/com/runvas/backend/community/CommentService.java \
        backend/src/main/java/com/runvas/backend/community/CommentController.java \
        backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java
git commit -m "feat(backend): 댓글 조회/작성/수정/삭제 API 구현"
```

---

## Task 4: Like 토글 (courses/posts 공용, 서비스, 컨트롤러, 테스트)

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/community/dto/LikeResponse.java`
- Create: `backend/src/main/java/com/runvas/backend/community/LikeService.java`
- Create: `backend/src/main/java/com/runvas/backend/community/LikeController.java`
- Test: `backend/src/test/java/com/runvas/backend/community/LikeControllerTest.java`

**Interfaces:**
- Consumes: `Like`/`Like.LikeId`/`LikeRepository`/`LikeTargetType`(기존), `PostRepository`(Task 1),
  `Post.incrementLikeCount()/decrementLikeCount()/getLikeCount()`(Task 1),
  `com.runvas.backend.course.CourseRepository`/`Course`(기존), `CurrentUserProvider`(기존).
- Produces: `LikeService.like(String targetType, String targetId)`,
  `unlike(String targetType, String targetId)` → `LikeResponse(String targetType, String targetId, boolean liked, Integer likeCount)`.
  이 엔드포인트는 `Auth: Required`라 `SecurityConfig`의 기존 `anyRequest().authenticated()`로 이미
  커버되므로 `SecurityConfig` 변경이 필요 없다.

- [ ] **Step 1: LikeResponse dto 작성**

`backend/src/main/java/com/runvas/backend/community/dto/LikeResponse.java`:

```java
package com.runvas.backend.community.dto;

// docs/api-contract.md PUT/DELETE /likes/{targetType}/{targetId} 응답.
public record LikeResponse(String targetType, String targetId, boolean liked, Integer likeCount) {
}
```

- [ ] **Step 2: LikeService 작성**

`backend/src/main/java/com/runvas/backend/community/LikeService.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.dto.LikeResponse;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

	private final LikeRepository likeRepository;
	private final CourseRepository courseRepository;
	private final PostRepository postRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public LikeResponse like(String targetTypePathValue, String targetId) {
		LikeTargetType targetType = parseTargetType(targetTypePathValue);
		String userId = currentUserProvider.requireUserId();

		Like.LikeId likeId = new Like.LikeId(userId, targetType, targetId);
		if (likeRepository.existsById(likeId)) {
			return new LikeResponse(targetTypePathValue, targetId, true, currentLikeCount(targetType, targetId));
		}

		requireTargetExists(targetType, targetId);
		likeRepository.save(new Like(userId, targetType, targetId));
		incrementLikeCount(targetType, targetId);

		return new LikeResponse(targetTypePathValue, targetId, true, currentLikeCount(targetType, targetId));
	}

	@Transactional
	public LikeResponse unlike(String targetTypePathValue, String targetId) {
		LikeTargetType targetType = parseTargetType(targetTypePathValue);
		String userId = currentUserProvider.requireUserId();

		Like.LikeId likeId = new Like.LikeId(userId, targetType, targetId);
		if (!likeRepository.existsById(likeId)) {
			return new LikeResponse(targetTypePathValue, targetId, false, currentLikeCount(targetType, targetId));
		}

		requireTargetExists(targetType, targetId);
		likeRepository.deleteById(likeId);
		decrementLikeCount(targetType, targetId);

		return new LikeResponse(targetTypePathValue, targetId, false, currentLikeCount(targetType, targetId));
	}

	private LikeTargetType parseTargetType(String value) {
		if ("courses".equals(value)) return LikeTargetType.COURSE;
		if ("posts".equals(value)) return LikeTargetType.POST;
		throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported targetType: " + value);
	}

	private void requireTargetExists(LikeTargetType targetType, String targetId) {
		boolean exists = switch (targetType) {
			case COURSE -> courseRepository.existsById(targetId);
			case POST -> postRepository.existsById(targetId);
		};
		if (!exists) {
			throw new ApiException(ErrorCode.NOT_FOUND, "대상이 없습니다");
		}
	}

	private void incrementLikeCount(LikeTargetType targetType, String targetId) {
		switch (targetType) {
			case COURSE -> courseRepository.findById(targetId)
					.ifPresent(course -> course.setLikeCount(course.getLikeCount() + 1));
			case POST -> postRepository.findById(targetId).ifPresent(Post::incrementLikeCount);
		}
	}

	private void decrementLikeCount(LikeTargetType targetType, String targetId) {
		switch (targetType) {
			case COURSE -> courseRepository.findById(targetId)
					.ifPresent(course -> course.setLikeCount(Math.max(0, course.getLikeCount() - 1)));
			case POST -> postRepository.findById(targetId).ifPresent(Post::decrementLikeCount);
		}
	}

	private Integer currentLikeCount(LikeTargetType targetType, String targetId) {
		return switch (targetType) {
			case COURSE -> courseRepository.findById(targetId).map(Course::getLikeCount).orElse(0);
			case POST -> postRepository.findById(targetId).map(Post::getLikeCount).orElse(0);
		};
	}
}
```

- [ ] **Step 3: LikeController 작성**

`backend/src/main/java/com/runvas/backend/community/LikeController.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.community.dto.LikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

	private final LikeService likeService;

	@PutMapping("/{targetType}/{targetId}")
	public LikeResponse like(@PathVariable String targetType, @PathVariable String targetId) {
		return likeService.like(targetType, targetId);
	}

	@DeleteMapping("/{targetType}/{targetId}")
	public LikeResponse unlike(@PathVariable String targetType, @PathVariable String targetId) {
		return likeService.unlike(targetType, targetId);
	}
}
```

- [ ] **Step 4: LikeControllerTest 작성**

`backend/src/test/java/com/runvas/backend/community/LikeControllerTest.java`:

```java
package com.runvas.backend.community;

import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.JwtProvider;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class LikeControllerTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
		registry.add("runvas.jwt.expiration-seconds", () -> "3600");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	JwtProvider jwtProvider;

	private String createUserAndToken(String nickname) {
		User user = userRepository.saveAndFlush(User.createKakaoUser("kakao-" + nickname, null, nickname, null));
		return jwtProvider.createAccessToken(user.getId());
	}

	private String createPost(String accessToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "좋아요 테스트용 글", "body": "본문" }
								"""))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	@Test
	void likingPostTwiceIsIdempotent() throws Exception {
		String accessToken = createUserAndToken("liker1");
		String postId = createPost(accessToken);

		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));

		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));
	}

	@Test
	void unlikingRemovesLikeAndUnlikingAgainIsIdempotent() throws Exception {
		String accessToken = createUserAndToken("liker2");
		String postId = createPost(accessToken);

		mockMvc.perform(put("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));

		mockMvc.perform(delete("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(0));

		mockMvc.perform(delete("/api/likes/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(0));
	}

	@Test
	void likingUnsupportedTargetTypeReturns400() throws Exception {
		String accessToken = createUserAndToken("liker3");

		mockMvc.perform(put("/api/likes/users/some-id")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void likingUnknownPostReturns404() throws Exception {
		String accessToken = createUserAndToken("liker4");

		mockMvc.perform(put("/api/likes/posts/unknown-post")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void likingWithoutAuthReturns401() throws Exception {
		mockMvc.perform(put("/api/likes/posts/some-id"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void likingCourseIncrementsCourseLikeCount() throws Exception {
		String accessToken = createUserAndToken("liker5");
		Course course = courseRepository.saveAndFlush(new Course(
				"author-x",
				"좋아요 테스트 코스",
				null,
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				200,
				120,
				new GeoBounds(new GeoPoint(37.0, 127.0), new GeoPoint(37.001, 127.001)),
				CourseVisibility.PUBLIC,
				Set.of()));

		mockMvc.perform(put("/api/likes/courses/" + course.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetType").value("courses"))
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));
	}
}
```

- [ ] **Step 5: 테스트 실행 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.LikeControllerTest"`
Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 6: 전체 백엔드 테스트 스위트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (기존 auth/user/course 테스트 포함 전체 통과).

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/dto/LikeResponse.java \
        backend/src/main/java/com/runvas/backend/community/LikeService.java \
        backend/src/main/java/com/runvas/backend/community/LikeController.java \
        backend/src/test/java/com/runvas/backend/community/LikeControllerTest.java
git commit -m "feat(backend): 코스/게시글 좋아요 토글 API 구현"
```

---

## Task 5: 모바일 서비스 레이어를 mock에서 실제 fetch로 교체

**Files:**
- Modify: `mobile/src/services/postApi.ts`
- Modify: `mobile/src/services/commentApi.ts`
- Modify: `mobile/src/services/likeApi.ts`

**Interfaces:**
- Consumes: Task 2/3/4에서 구현한 `POST/GET/PATCH/DELETE /api/posts`, `GET/POST /api/posts/{postId}/comments`,
  `PUT/DELETE /api/likes/posts/{postId}` (백엔드가 응답 계약을 정확히 지켜야 이 파일들이 그대로 동작한다).
  `mobile/src/utils/apiError.ts`의 `parseApiErrorMessage(response: Response): Promise<string>`(기존).
  `mobile/src/types/index.ts`의 `Post`, `Comment`, `CreatePostRequestBody`, `CreateCommentRequestBody`(기존).
- Produces: `getPosts(params): Promise<Post[]>`, `getPost(postId): Promise<Post>`,
  `createPost(body, accessToken): Promise<Post>` (author 파라미터 제거),
  `getComments(postId): Promise<Comment[]>`, `createComment(postId, body, accessToken): Promise<Comment>`
  (author 파라미터 제거), `putLike(postId, accessToken)`, `deleteLike(postId, accessToken)` →
  `{ liked: boolean; likeCount: number }`. 이 시그니처들을 Task 6에서 화면이 호출한다.

- [ ] **Step 1: postApi.ts를 실제 fetch로 교체**

`mobile/src/services/postApi.ts` 전체 내용을 아래로 교체:

```ts
// 게시글 API (GET/POST /api/posts 등) — runvas/backend의 PostController와 연동됨.
import { Post, CreatePostRequestBody } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

interface GetPostsParams {
  attachedCourseId?: string;
  sort?: 'createdAtDesc' | 'popularDesc';
}

export async function getPosts(params: GetPostsParams = {}): Promise<Post[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const query = new URLSearchParams();
  if (params.attachedCourseId) query.set('attachedCourseId', params.attachedCourseId);
  if (params.sort) query.set('sort', params.sort);

  const response = await fetch(`${API_BASE_URL}/api/posts?${query.toString()}`);

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { posts } = (await response.json()) as { posts: Post[] };
  return posts;
}

export async function getPost(postId: string): Promise<Post> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts/${postId}`);

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { post } = (await response.json()) as { post: Post };
  return post;
}

export async function createPost(body: CreatePostRequestBody, accessToken: string): Promise<Post> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { post } = (await response.json()) as { post: Post };
  return post;
}
```

- [ ] **Step 2: commentApi.ts를 실제 fetch로 교체**

`mobile/src/services/commentApi.ts` 전체 내용을 아래로 교체:

```ts
// 댓글 API (GET/POST /api/posts/{postId}/comments) — runvas/backend의 CommentController와 연동됨.
import { Comment, CreateCommentRequestBody } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function getComments(postId: string): Promise<Comment[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts/${postId}/comments`);

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { comments } = (await response.json()) as { comments: Comment[] };
  return comments;
}

export async function createComment(
  postId: string,
  body: CreateCommentRequestBody,
  accessToken: string
): Promise<Comment> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts/${postId}/comments`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { comment } = (await response.json()) as { comment: Comment };
  return comment;
}
```

- [ ] **Step 3: likeApi.ts를 실제 fetch로 교체**

`mobile/src/services/likeApi.ts` 전체 내용을 아래로 교체:

```ts
// 좋아요 API (PUT/DELETE /api/likes/posts/{postId}) — runvas/backend의 LikeController와 연동됨.
// docs/api-contract.md §Like APIs는 targetType(courses/posts)을 받지만, 모바일에서는 게시글
// 좋아요만 다루므로 targetType은 'posts'로 고정한다 (코스 좋아요 UI는 범위 밖).
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

interface LikeResult {
  liked: boolean;
  likeCount: number;
}

export async function putLike(postId: string, accessToken: string): Promise<LikeResult> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/likes/posts/${postId}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { liked, likeCount } = (await response.json()) as LikeResult;
  return { liked, likeCount };
}

export async function deleteLike(postId: string, accessToken: string): Promise<LikeResult> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/likes/posts/${postId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { liked, likeCount } = (await response.json()) as LikeResult;
  return { liked, likeCount };
}
```

- [ ] **Step 4: 타입 체크로 시그니처 변경 파급 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: `createPost`/`createComment` 호출부(`PostCreateScreen.tsx`, `PostDetailScreen.tsx`)에서
인자 개수 불일치 에러가 발생한다 (Task 6에서 고친다). 이 시점에는 이 에러만 있고 다른 타입 에러는
없어야 한다.

- [ ] **Step 5: 커밋**

```bash
git add mobile/src/services/postApi.ts mobile/src/services/commentApi.ts mobile/src/services/likeApi.ts
git commit -m "feat(mobile): 게시글/댓글/좋아요 서비스를 mock에서 실제 백엔드 연동으로 교체"
```

---

## Task 6: 화면 호출부 수정, 전체 검증, 구현 기록

**Files:**
- Modify: `mobile/src/screens/PostCreateScreen.tsx:43-56`
- Modify: `mobile/src/screens/PostDetailScreen.tsx:77-87`
- Create: `backend/docs/implementations/community-post-api.md`
- Create: `mobile/docs/implementations/community-post-backend-integration.md`

**Interfaces:**
- Consumes (Task 5): `createPost(body: CreatePostRequestBody, accessToken: string): Promise<Post>`,
  `createComment(postId: string, body: CreateCommentRequestBody, accessToken: string): Promise<Comment>`.

- [ ] **Step 1: PostCreateScreen.tsx에서 createPost 호출 수정**

`mobile/src/screens/PostCreateScreen.tsx`에서 아래 블록을:

```ts
      const post = await createPost(
        {
          title: title.trim(),
          body: body.trim(),
          attachedCourseId: attachedCourseId ?? null,
        },
        accessToken,
        {
          id: user.id,
          nickname: user.nickname,
          profileImageUrl: user.profileImageUrl,
          bio: user.bio,
        }
      );
```

아래로 교체 (세 번째 인자 제거 — 백엔드가 JWT로 작성자를 판별한다):

```ts
      const post = await createPost(
        {
          title: title.trim(),
          body: body.trim(),
          attachedCourseId: attachedCourseId ?? null,
        },
        accessToken
      );
```

`user`는 그 위 `if (!accessToken || !user) return;` 가드에서 여전히 쓰이므로 import는 그대로 둔다.

- [ ] **Step 2: PostDetailScreen.tsx에서 createComment 호출 수정**

`mobile/src/screens/PostDetailScreen.tsx`에서 아래 블록을:

```ts
      const comment = await createComment(
        postId,
        { body: commentBody.trim() },
        accessToken,
        {
          id: user.id,
          nickname: user.nickname,
          profileImageUrl: user.profileImageUrl,
          bio: user.bio,
        }
      );
```

아래로 교체:

```ts
      const comment = await createComment(postId, { body: commentBody.trim() }, accessToken);
```

`user`는 댓글 입력창 표시 여부 판단(`{user ? (...) : (...)}`)과 위쪽 가드에서 계속 쓰이므로 그대로 둔다.

- [ ] **Step 3: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없이 종료.

- [ ] **Step 4: 번들 기동 확인**

Run: `cd mobile && npx expo start --non-interactive &`
Run (몇 초 대기 후): `curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"`
Expected: `200`

- [ ] **Step 5: 백엔드 기동 + 실기기/시뮬레이터 수동 검증**

1. `cd backend && ./gradlew bootRun` (로컬 Postgres/Redis 필요 — `docker-compose` 등 기존 로컬 실행
   방식을 그대로 사용).
2. 모바일 `.env`의 `EXPO_PUBLIC_API_BASE_URL`이 로컬 백엔드를 가리키는지 확인.
3. 시뮬레이터/실기기에서: 게시판 탭 → 글쓰기 → 제목/본문 입력 → 저장 → 상세 화면 진입 확인
   (mock이 아닌 실제 서버 응답으로 `post.id`가 UUID 형태인지 확인).
4. 상세 화면에서 좋아요 토글 → `likeCount` 반영 확인.
5. 댓글 작성 → 목록에 반영, `post.commentCount` 갱신 확인.
6. 코스 상세 → "게시판" 버튼 → 코스별 게시글 목록에 방금 만든 글이 보이는지 확인 (`attachedCourseId`
   필터).
7. 앱을 종료 후 재실행해도 방금 작성한 글이 남아있는지 확인 (mock과 달리 재시작해도 유지되어야 함 —
   이게 이번 작업의 핵심 검증 포인트).

- [ ] **Step 6: 백엔드 구현 기록 작성**

`backend/docs/implementations/community-post-api.md`:

```markdown
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
```

- [ ] **Step 7: 모바일 구현 기록 작성**

`mobile/docs/implementations/community-post-backend-integration.md`:

```markdown
# 커뮤니티 게시글 백엔드 연동 (mock 제거)

## 배경

`mobile/docs/implementations/community-post-mobile-ui.md`에서 mock으로 구현했던 Post/Comment/Like
서비스를 실제 백엔드(`backend` community 패키지)와 연동했다.

## 변경 내용

- `postApi.ts`/`commentApi.ts`/`likeApi.ts`를 `courseApi.ts`와 동일한 실제 `fetch` 패턴으로 교체.
  in-memory 시드 데이터, `incrementCommentCount`/`updateLikeState` 헬퍼를 제거했다 (실제 서버가
  매 응답에 최신 값을 내려주므로 클라이언트 쪽 동기화가 필요 없다).
- `createPost`/`createComment`에서 `author` 파라미터를 제거했다 — 백엔드가 JWT로 작성자를
  판별해 응답에 채워준다. `PostCreateScreen.tsx`, `PostDetailScreen.tsx`의 호출부도 함께 수정했다.

## 검증

- `npx tsc --noEmit` 통과
- 실기기/시뮬레이터에서 게시글 작성 → 상세 진입 → 좋아요 토글 → 댓글 작성 → 앱 재시작 후에도
  데이터가 유지되는지 확인 (mock 시절과 달리 재시작해도 사라지지 않아야 함).

## 참고

- 관련 스펙: `docs/superpowers/specs/2026-07-05-community-post-backend-design.md`
```

- [ ] **Step 8: 커밋**

```bash
git add mobile/src/screens/PostCreateScreen.tsx \
        mobile/src/screens/PostDetailScreen.tsx \
        backend/docs/implementations/community-post-api.md \
        mobile/docs/implementations/community-post-backend-integration.md
git commit -m "feat(mobile): 게시글/댓글 작성 화면을 실제 백엔드 응답 기준으로 정리"
```

