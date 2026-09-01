# 사용자 차단 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 다른 사용자를 차단하면(단방향) 게시글/댓글/코스댓글 조회에서 그 사용자의 콘텐츠가 보이지 않게 하고, 차단·해제·차단 목록 조회 API와 모바일 UI를 제공한다.

**Architecture:** `Bookmark`와 동일한 `@EmbeddedId` 복합키 관계 테이블(`Block`)을 추가하고, `PostService`/`CommentService`/`CourseCommentService`의 조회 메서드에 "호출자가 차단한 작성자 ID 집합"으로 필터링하는 로직을 삽입한다. 모바일은 기존 신고 버튼 옆에 차단 버튼을 추가하고, 프로필에 차단 목록 관리 화면을 신규로 둔다.

**Tech Stack:** Spring Boot(JPA/Flyway/PostgreSQL, MockMvc+Testcontainers 통합 테스트), Expo/React Native/TypeScript.

## Global Constraints

- 차단은 단방향이다 — 차단한 사람의 화면에서만 상대 콘텐츠가 사라진다. 쓰기 경로(댓글 작성, 좋아요)는 건드리지 않는다.
- 차단 적용 범위는 `Post`, `Comment`, `CourseComment` 세 종류뿐이다. `Course` 자체(코스 목록/지도 탐색/상세)는 건드리지 않는다.
- 자기 자신 차단은 `400 VALIDATION_ERROR`로 막는다.
- 차단/해제는 멱등이다 — 중복 차단은 `200 OK`(신규는 `201`), 차단하지 않은 상태에서 해제해도 `204`.
- 커밋 메시지는 Conventional Commits + 한글, `Co-Authored-By` 등 AI 저작자 표시 금지 (`sh scripts/setup-git-hooks.sh`가 이미 이 워크트리에 적용되어 있다).
- 커밋에는 의도한 파일만 스테이징한다 (`git add -A` 금지).
- 백엔드 테스트는 `./gradlew test`(Testcontainers로 실제 PostgreSQL 컨테이너 필요, Docker 데몬이 떠 있어야 한다), 모바일은 `npx tsc --noEmit`로 매 태스크 끝에 검증한다.

---

### Task 1: 문서 — Block API/데이터 모델 반영

**Files:**
- Modify: `docs/api-contract.md` (`## Report APIs` 섹션 뒤에 `## Block APIs` 섹션 추가)
- Modify: `docs/data-model.md` (`## Report` 섹션 뒤에 `## Block` 섹션 추가)

**Interfaces:**
- Produces: 이후 모든 백엔드/모바일 태스크가 그대로 구현할 API 계약 — `POST /blocks/{userId}`, `DELETE /blocks/{userId}`, `GET /blocks`.

- [ ] **Step 1: `docs/api-contract.md`에 `## Block APIs` 섹션 추가**

`## Report APIs` 섹션(`### POST /reports/{targetType}/{targetId}`의 Errors 목록 끝) 바로 다음에 아래 내용을 새 섹션으로 추가한다.

```markdown
## Block APIs

### POST /blocks/{userId}

사용자를 차단합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | string | 차단할 사용자 ID (`PublicProfile.id`와 같은 `user_` 접두 포맷) |

#### Response: 201 Created (신규) 또는 200 OK (이미 차단 중)

```json
{
  "blockedUser": {
    "id": "user_456",
    "nickname": "River Runner",
    "profileImageUrl": null,
    "bio": null
  },
  "createdAt": "2026-09-01T10:00:00Z"
}
```

#### Errors

- `400 VALIDATION_ERROR`: 본인을 차단하려는 요청
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 대상 사용자가 없음(탈퇴 포함)

### DELETE /blocks/{userId}

차단을 해제합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | string | 차단 해제할 사용자 ID |

#### Response: 204 No Content

차단 중이 아니었어도 동일하게 204를 반환합니다(멱등).

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음

### GET /blocks

내가 차단한 사용자 목록을 조회합니다.

#### Auth

`Required`

#### Response: 200 OK

```json
{
  "blocks": [
    {
      "blockedUser": {
        "id": "user_456",
        "nickname": "River Runner",
        "profileImageUrl": null,
        "bio": null
      },
      "createdAt": "2026-09-01T10:00:00Z"
    }
  ],
  "pageInfo": {
    "nextCursor": null
  }
}
```

`GET /me/bookmarked-courses`와 동일하게 실제 커서 페이지네이션은 구현하지 않고 차단 목록 전체를
한 번에 반환합니다 (`pageInfo.nextCursor`는 항상 `null`).

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음

### 기존 조회 API에 적용되는 차단 필터링

아래 API들은 `Optional` 인증이며, **로그인한 요청에 한해** 호출자가 차단한 사용자의 콘텐츠를
결과에서 제외합니다. 비로그인 요청은 기존과 동일하게 동작합니다.

| API | 필터링 규칙 |
| --- | --- |
| `GET /posts` | `post.author`가 차단 대상이면 목록에서 제외 |
| `GET /posts/{postId}` | 작성자가 차단 대상이면 `404 NOT_FOUND` |
| `GET /posts/{postId}/comments` | 작성자가 차단 대상인 댓글을 목록에서 제외 |
| `GET /courses/{courseId}/comments` | 작성자가 차단 대상인 코스 댓글을 목록에서 제외 |
| `GET /courses/{courseId}/comments/{commentId}/replies` | 위와 동일 |

`GET /courses`, `GET /courses/{courseId}` 등 코스 관련 조회는 이번 범위에서 변경하지 않습니다.
```

- [ ] **Step 2: `docs/data-model.md`에 `## Block` 섹션 추가**

`## Report` 섹션(`RESOLVED`/`DISMISSED`로 끝난 신고 이후 재신고는 새로 생성된다는 문장) 바로
다음, `## CourseBookmark` 섹션 바로 앞에 추가한다.

```markdown
## Block

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `blockerId` | string | Y | 차단한 사용자 ID. API 응답에는 노출하지 않음 |
| `blockedId` | string | Y | 차단당한 사용자 ID |
| `createdAt` | string | Y | ISO 8601 차단 시각 |

`Report`처럼 상태가 바뀌는 엔티티가 아니라 `CourseBookmark`/`LikeTargetType`처럼 "존재 = 활성
상태, 삭제 = 해제"인 순수 관계다. 차단은 단방향이며 `Post`/`Comment`/`CourseComment` 조회에만
적용된다(`Course` 자체는 제외).
```

- [ ] **Step 3: 커밋**

```bash
git add docs/api-contract.md docs/data-model.md
git commit -m "docs: 사용자 차단 API/데이터 모델 문서화"
```

---

### Task 2: 백엔드 — Block 핵심 기능 (엔티티/리포지토리/서비스/컨트롤러)

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__create_blocks.sql`
- Create: `backend/src/main/java/com/runvas/backend/community/Block.java`
- Create: `backend/src/main/java/com/runvas/backend/community/BlockRepository.java`
- Create: `backend/src/main/java/com/runvas/backend/community/BlockService.java`
- Create: `backend/src/main/java/com/runvas/backend/community/BlockController.java`
- Create: `backend/src/test/java/com/runvas/backend/community/BlockControllerTest.java`

**Interfaces:**
- Consumes: `com.runvas.backend.auth.CurrentUserProvider#requireUserId()`, `com.runvas.backend.common.ApiException`, `com.runvas.backend.common.ErrorCode`, `com.runvas.backend.common.PageInfo`, `com.runvas.backend.community.dto.PublicProfile#from(User)`, `com.runvas.user.repository.UserRepository`, `com.runvas.user.domain.User`.
- Produces: `BlockRepository#findBlockedIdsByBlockerId(String): Set<String>` — Task 3~5가 그대로 주입해서 쓴다. `BlockService.BlockResponse(PublicProfile blockedUser, Instant createdAt)`, `BlockService.Result(BlockResponse response, boolean isNew)`, `BlockService.ListResult(List<BlockResponse> blocks, PageInfo pageInfo)`.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`backend/src/test/java/com/runvas/backend/community/BlockControllerTest.java`:

```java
package com.runvas.backend.community;

import com.runvas.auth.service.JwtProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class BlockControllerTest {

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

	private String userIdPathValue(String accessToken) {
		return "user_" + jwtProvider.parseUserId(accessToken);
	}

	@Test
	void blockingUserReturns201AndBlockedUserProfile() throws Exception {
		String blockerToken = createUserAndToken("blocker-a");
		String targetToken = createUserAndToken("target-a");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.blockedUser.id").value(targetUserId))
				.andExpect(jsonPath("$.blockedUser.nickname").value("target-a"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void blockingSameUserTwiceIsIdempotent() throws Exception {
		String blockerToken = createUserAndToken("blocker-b");
		String targetToken = createUserAndToken("target-b");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk());
	}

	@Test
	void blockingSelfReturns400() throws Exception {
		String token = createUserAndToken("self-blocker");
		String ownUserId = userIdPathValue(token);

		mockMvc.perform(post("/api/blocks/" + ownUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void blockingUnknownUserReturns404() throws Exception {
		String token = createUserAndToken("blocker-c");

		mockMvc.perform(post("/api/blocks/user_00000000-0000-0000-0000-000000000000")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void blockingWithoutAuthReturns401() throws Exception {
		mockMvc.perform(post("/api/blocks/user_00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unblockingRemovesBlockAndIsIdempotent() throws Exception {
		String blockerToken = createUserAndToken("blocker-d");
		String targetToken = createUserAndToken("target-d");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/blocks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks.length()").value(0));

		mockMvc.perform(delete("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isNoContent());
	}

	@Test
	void listReturnsBlockedUsers() throws Exception {
		String blockerToken = createUserAndToken("blocker-e");
		String targetToken = createUserAndToken("target-e");
		String targetUserId = userIdPathValue(targetToken);

		mockMvc.perform(post("/api/blocks/" + targetUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/blocks")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks[?(@.blockedUser.id == '" + targetUserId + "')]").exists())
				.andExpect(jsonPath("$.pageInfo.nextCursor").doesNotExist());
	}

	@Test
	void listWithoutAuthReturns401() throws Exception {
		mockMvc.perform(get("/api/blocks"))
				.andExpect(status().isUnauthorized());
	}
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.BlockControllerTest"`
Expected: FAIL — `/api/blocks/**` 라우트가 없어 모든 요청이 `404`/`401` 매칭 실패 또는 컴파일은
되지만(HTTP 호출만 하므로 컴파일 자체는 통과) 각 `andExpect`가 실패한다.

- [ ] **Step 3: 마이그레이션 작성**

`backend/src/main/resources/db/migration/V16__create_blocks.sql`:

```sql
CREATE TABLE blocks (
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE INDEX idx_blocks_blocker_id ON blocks (blocker_id);
```

- [ ] **Step 4: `Block` 엔티티 작성**

`backend/src/main/java/com/runvas/backend/community/Block.java`:

```java
package com.runvas.backend.community;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// docs/data-model.md Block — Bookmark와 동일한 복합키 관계 테이블. 단방향 차단이라 상태 필드가 없다.
@Entity
@Table(name = "blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

	@EmbeddedId
	private BlockId id;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	public Block(String blockerId, String blockedId) {
		this.id = new BlockId(blockerId, blockedId);
	}

	public String getBlockerId() {
		return id.getBlockerId();
	}

	public String getBlockedId() {
		return id.getBlockedId();
	}

	@Getter
	@EqualsAndHashCode
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class BlockId implements Serializable {
		private String blockerId;
		private String blockedId;

		public BlockId(String blockerId, String blockedId) {
			this.blockerId = blockerId;
			this.blockedId = blockedId;
		}
	}
}
```

- [ ] **Step 5: `BlockRepository` 작성**

`backend/src/main/java/com/runvas/backend/community/BlockRepository.java`:

```java
package com.runvas.backend.community;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Block.BlockId> {

	List<Block> findByIdBlockerIdOrderByCreatedAtDesc(String blockerId);

	// PostService/CommentService/CourseCommentService의 목록 조회에서 "차단된 작성자 ID 집합"을
	// 한 번만 조회해 in-memory 필터링하는 데 쓴다.
	@Query("select b.id.blockedId from Block b where b.id.blockerId = :blockerId")
	Set<String> findBlockedIdsByBlockerId(@Param("blockerId") String blockerId);
}
```

- [ ] **Step 6: `BlockService` 작성**

`backend/src/main/java/com/runvas/backend/community/BlockService.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.common.PageInfo;
import com.runvas.backend.community.dto.PublicProfile;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockService {

	private final BlockRepository blockRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public Result block(String targetUserIdPathValue) {
		String blockerId = currentUserProvider.requireUserId();
		String blockedId = parseUserId(targetUserIdPathValue);

		if (blockerId.equals(blockedId)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "자기 자신은 차단할 수 없습니다");
		}

		User blockedUser = userRepository.findById(UUID.fromString(blockedId))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자가 없습니다"));

		Block.BlockId blockId = new Block.BlockId(blockerId, blockedId);
		Optional<Block> existing = blockRepository.findById(blockId);
		Block block = existing.orElseGet(() -> blockRepository.save(new Block(blockerId, blockedId)));

		BlockResponse response = new BlockResponse(PublicProfile.from(blockedUser), block.getCreatedAt());
		return new Result(response, existing.isEmpty());
	}

	@Transactional
	public void unblock(String targetUserIdPathValue) {
		String blockerId = currentUserProvider.requireUserId();
		String blockedId = parseUserId(targetUserIdPathValue);
		blockRepository.deleteById(new Block.BlockId(blockerId, blockedId));
	}

	@Transactional(readOnly = true)
	public ListResult listByUser() {
		String blockerId = currentUserProvider.requireUserId();
		List<BlockResponse> blocks = blockRepository.findByIdBlockerIdOrderByCreatedAtDesc(blockerId).stream()
				.flatMap(block -> userRepository.findById(UUID.fromString(block.getBlockedId())).stream()
						.map(user -> new BlockResponse(PublicProfile.from(user), block.getCreatedAt())))
				.toList();
		return new ListResult(blocks, new PageInfo(null));
	}

	// "user_" 접두를 제거하고 UUID 형식을 검증한다. 형식이 잘못됐거나 접두가 없어도 그냥 원문을
	// UUID로 파싱 시도하므로, 어차피 존재하지 않는 사용자와 동일하게 404로 수렴한다.
	private String parseUserId(String pathValue) {
		String raw = pathValue.startsWith("user_") ? pathValue.substring(5) : pathValue;
		try {
			UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "사용자가 없습니다");
		}
		return raw;
	}

	public record BlockResponse(PublicProfile blockedUser, Instant createdAt) {}

	// isNew는 컨트롤러가 201/200을 분기하는 데만 쓰고 응답 JSON에는 포함하지 않는다
	// (ReportService.Result와 동일한 관례).
	public record Result(BlockResponse response, boolean isNew) {}

	public record ListResult(List<BlockResponse> blocks, PageInfo pageInfo) {}
}
```

- [ ] **Step 7: `BlockController` 작성**

`backend/src/main/java/com/runvas/backend/community/BlockController.java`:

```java
package com.runvas.backend.community;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BlockController {

	private final BlockService blockService;

	@PostMapping("/api/blocks/{userId}")
	public ResponseEntity<BlockService.BlockResponse> block(@PathVariable String userId) {
		BlockService.Result result = blockService.block(userId);
		HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.response());
	}

	@DeleteMapping("/api/blocks/{userId}")
	public ResponseEntity<Void> unblock(@PathVariable String userId) {
		blockService.unblock(userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/blocks")
	public Map<String, Object> list() {
		BlockService.ListResult result = blockService.listByUser();
		return Map.of("blocks", result.blocks(), "pageInfo", result.pageInfo());
	}
}
```

- [ ] **Step 8: 테스트를 다시 실행해 통과를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.BlockControllerTest"`
Expected: PASS — 8개 테스트 모두 통과.

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/resources/db/migration/V16__create_blocks.sql \
        backend/src/main/java/com/runvas/backend/community/Block.java \
        backend/src/main/java/com/runvas/backend/community/BlockRepository.java \
        backend/src/main/java/com/runvas/backend/community/BlockService.java \
        backend/src/main/java/com/runvas/backend/community/BlockController.java \
        backend/src/test/java/com/runvas/backend/community/BlockControllerTest.java
git commit -m "feat(backend): 사용자 차단/해제/목록 API 추가"
```

---

### Task 3: 백엔드 — 게시글 조회에 차단 필터링 반영

**Files:**
- Modify: `backend/src/main/java/com/runvas/backend/community/PostService.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/PostControllerTest.java`

**Interfaces:**
- Consumes: `BlockRepository#findBlockedIdsByBlockerId(String): Set<String>` (Task 2), `BlockController`의 `POST /api/blocks/{userId}` (테스트에서 차단을 걸기 위해 호출).

- [ ] **Step 1: 실패하는 테스트 추가**

`PostControllerTest.java`의 마지막 `@Test` 메서드(`showsWithdrawnPlaceholderWhenAuthorNoLongerExists`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
	@Test
	void listExcludesPostsByBlockedAuthor() throws Exception {
		String authorToken = createUserAndToken("blocked-author1");
		String blockerToken = createUserAndToken("blocker1");
		String postId = createPost(authorToken, "차단 테스트 글", "본문");

		MvcResult postResult = mockMvc.perform(get("/api/posts/" + postId)).andReturn();
		String authorUserId = JsonPath.read(postResult.getResponse().getContentAsString(), "$.post.author.id");

		mockMvc.perform(post("/api/blocks/" + authorUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/posts")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.posts[?(@.title == '차단 테스트 글')]").isEmpty());
	}

	@Test
	void getByIdReturnsNotFoundForBlockedAuthorPost() throws Exception {
		String authorToken = createUserAndToken("blocked-author2");
		String blockerToken = createUserAndToken("blocker2");
		String postId = createPost(authorToken, "차단된 작성자 글", "본문");

		MvcResult postResult = mockMvc.perform(get("/api/posts/" + postId)).andReturn();
		String authorUserId = JsonPath.read(postResult.getResponse().getContentAsString(), "$.post.author.id");

		mockMvc.perform(post("/api/blocks/" + authorUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void blockedAuthorPostStillVisibleToOtherUsers() throws Exception {
		String authorToken = createUserAndToken("blocked-author3");
		String blockerToken = createUserAndToken("blocker3");
		String otherToken = createUserAndToken("bystander3");
		String postId = createPost(authorToken, "다른 사람에게는 보이는 글", "본문");

		MvcResult postResult = mockMvc.perform(get("/api/posts/" + postId)).andReturn();
		String authorUserId = JsonPath.read(postResult.getResponse().getContentAsString(), "$.post.author.id");

		mockMvc.perform(post("/api/blocks/" + authorUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(status().isOk());
	}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostControllerTest"`
Expected: FAIL — 새로 추가한 3개 테스트가 실패(차단해도 게시글이 그대로 보임/조회됨). 나머지 기존
테스트는 계속 통과.

- [ ] **Step 3: `PostService`에 차단 필터링 추가**

`backend/src/main/java/com/runvas/backend/community/PostService.java`는 이미
`import java.util.Set;`를 갖고 있으므로(태그 처리에 사용 중) import 추가는 필요 없다. 필드에
`BlockRepository`를 추가한다:

```java
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final LikeRepository likeRepository;
	private final BlockRepository blockRepository;
	private final CurrentUserProvider currentUserProvider;
```

`getById`를 아래로 교체:

```java
	@Transactional(readOnly = true)
	public PostResponse getById(String postId) {
		Post post = findPostOrThrow(postId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		if (currentUserId != null
				&& blockRepository.findById(new Block.BlockId(currentUserId, post.getAuthorId())).isPresent()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다");
		}
		return toResponse(post, isLikedByCurrentUser(post.getId(), currentUserId));
	}
```

`list`를 아래로 교체:

```java
	@Transactional(readOnly = true)
	public ListResult list(String attachedCourseId, String q, String tag, String sort, Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
		}
		if (sort != null && !sort.equals("createdAtDesc") && !sort.equals("popularDesc")) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported sort: " + sort);
		}

		String currentUserId = currentUserProvider.currentUserIdOrNull();
		Set<String> blockedAuthorIds =
				currentUserId == null ? Set.of() : blockRepository.findBlockedIdsByBlockerId(currentUserId);

		List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc().stream()
				.filter(post -> !blockedAuthorIds.contains(post.getAuthorId()))
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
```

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostControllerTest"`
Expected: PASS — 전체 통과(기존 테스트 포함).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/PostService.java \
        backend/src/test/java/com/runvas/backend/community/PostControllerTest.java
git commit -m "feat(backend): 게시글 조회에 차단 필터링 반영"
```

---

### Task 4: 백엔드 — 게시글 댓글 조회에 차단 필터링 반영

**Files:**
- Modify: `backend/src/main/java/com/runvas/backend/community/CommentService.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java`

**Interfaces:**
- Consumes: `BlockRepository#findBlockedIdsByBlockerId(String): Set<String>` (Task 2).

- [ ] **Step 1: 실패하는 테스트 추가**

`CommentControllerTest.java`의 마지막 `@Test`(`showsWithdrawnPlaceholderWhenCommentAuthorNoLongerExists`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
	@Test
	void listExcludesCommentsByBlockedAuthor() throws Exception {
		String postOwnerToken = createUserAndToken("comment-post-owner1");
		String postId = createPost(postOwnerToken);
		String commenterToken = createUserAndToken("blocked-commenter1");
		String blockerToken = createUserAndToken("comment-blocker1");
		String commentId = createComment(commenterToken, postId, "차단될 댓글");

		MvcResult commentsResult = mockMvc.perform(get("/api/posts/" + postId + "/comments")).andReturn();
		String commenterUserId = JsonPath.read(
				commentsResult.getResponse().getContentAsString(),
				"$.comments[?(@.id == '" + commentId + "')].author.id[0]");

		mockMvc.perform(post("/api/blocks/" + commenterUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/posts/" + postId + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[?(@.body == '차단될 댓글')]").isEmpty());
	}

	@Test
	void listIncludesCommentsForAnonymousRequest() throws Exception {
		String postOwnerToken = createUserAndToken("comment-post-owner2");
		String postId = createPost(postOwnerToken);
		createComment(postOwnerToken, postId, "익명도 보이는 댓글");

		mockMvc.perform(get("/api/posts/" + postId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[?(@.body == '익명도 보이는 댓글')]").exists());
	}
```

이 파일에는 `com.jayway.jsonpath.JsonPath` import가 없으므로, 기존 import 블록의
`import org.junit.jupiter.api.Test;` 바로 위에 `import com.jayway.jsonpath.JsonPath;`를 추가한다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.CommentControllerTest"`
Expected: FAIL — `listExcludesCommentsByBlockedAuthor`가 실패(차단해도 댓글이 그대로 보임).
`listIncludesCommentsForAnonymousRequest`는 이미 통과할 수 있다(필터링이 없어도 익명 조회는
원래 다 보이므로) — 그래도 무방하다.

- [ ] **Step 3: `CommentService`에 차단 필터링 추가**

`backend/src/main/java/com/runvas/backend/community/CommentService.java`의 import 블록에
`import java.util.Set;`를 `import java.util.List;` 다음 줄에 추가하고, 필드에 `BlockRepository`를
추가한다:

```java
	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final BlockRepository blockRepository;
	private final CurrentUserProvider currentUserProvider;
```

`list`를 아래로 교체:

```java
	@Transactional(readOnly = true)
	public ListResult list(String postId, Integer limit) {
		findPostOrThrow(postId);
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
		}

		String currentUserId = currentUserProvider.currentUserIdOrNull();
		Set<String> blockedAuthorIds =
				currentUserId == null ? Set.of() : blockRepository.findBlockedIdsByBlockerId(currentUserId);

		List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
				.filter(comment -> !blockedAuthorIds.contains(comment.getAuthorId()))
				.limit(effectiveLimit)
				.map(this::toResponse)
				.toList();

		return new ListResult(comments, new PageInfo(null));
	}
```

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.CommentControllerTest"`
Expected: PASS — 전체 통과.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/CommentService.java \
        backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java
git commit -m "feat(backend): 게시글 댓글 조회에 차단 필터링 반영"
```

---

### Task 5: 백엔드 — 코스 댓글/대댓글 조회에 차단 필터링 반영

**Files:**
- Modify: `backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/CourseCommentControllerTest.java`

**Interfaces:**
- Consumes: `BlockRepository#findBlockedIdsByBlockerId(String): Set<String>` (Task 2).

- [ ] **Step 1: 실패하는 테스트 추가**

`CourseCommentControllerTest.java`의 마지막 `@Test`(`showsWithdrawnPlaceholderWhenCourseCommentAuthorNoLongerExists`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
	@Test
	void listExcludesCourseCommentsByBlockedAuthor() throws Exception {
		String courseOwnerToken = createUserToken("course-comment-owner1");
		String courseId = createCourse(authorIdFromToken(courseOwnerToken), CourseVisibility.PUBLIC);
		String commenterToken = createUserToken("blocked-course-commenter1");
		String blockerToken = createUserToken("course-comment-blocker1");
		String commentId = createComment(courseId, commenterToken, "차단될 코스 댓글");

		MvcResult commentsResult = mockMvc.perform(get("/api/courses/" + courseId + "/comments")).andReturn();
		String commenterUserId = JsonPath.read(
				commentsResult.getResponse().getContentAsString(),
				"$.comments[?(@.id == '" + commentId + "')].author.id[0]");

		mockMvc.perform(post("/api/blocks/" + commenterUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/courses/" + courseId + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[?(@.body == '차단될 코스 댓글')]").isEmpty());
	}

	@Test
	void listRepliesExcludesRepliesByBlockedAuthor() throws Exception {
		String courseOwnerToken = createUserToken("course-comment-owner2");
		String courseId = createCourse(authorIdFromToken(courseOwnerToken), CourseVisibility.PUBLIC);
		String parentId = createComment(courseId, courseOwnerToken, "원본 댓글");
		String replierToken = createUserToken("blocked-replier1");
		String blockerToken = createUserToken("course-comment-blocker2");
		createReply(courseId, replierToken, parentId, "차단될 대댓글");

		MvcResult repliesResult =
				mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + parentId + "/replies")).andReturn();
		String replierUserId =
				JsonPath.read(repliesResult.getResponse().getContentAsString(), "$.replies[0].author.id");

		mockMvc.perform(post("/api/blocks/" + replierUserId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/courses/" + courseId + "/comments/" + parentId + "/replies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + blockerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replies[?(@.body == '차단될 대댓글')]").isEmpty());
	}
```

이 파일에는 `com.jayway.jsonpath.JsonPath` import가 없으므로, 기존 import 블록의
`import com.runvas.auth.service.JwtProvider;` 바로 위에 `import com.jayway.jsonpath.JsonPath;`를
추가한다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.CourseCommentControllerTest"`
Expected: FAIL — 새로 추가한 2개 테스트가 실패.

- [ ] **Step 3: `CourseCommentService`에 차단 필터링 추가**

`backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`의 import 블록에
`import java.util.Set;`를 `import java.util.Map;` 다음 줄에 추가하고, 필드에 `BlockRepository`를
추가한다:

```java
	private final CourseCommentRepository courseCommentRepository;
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final BlockRepository blockRepository;
	private final CurrentUserProvider currentUserProvider;
```

`list`를 아래로 교체:

```java
	@Transactional(readOnly = true)
	public ListResult list(String courseId, Integer limit, String cursor) {
		Course course = findCourseOrThrow(courseId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		requireReadableCourse(course, currentUserId);
		Set<String> blockedAuthorIds = blockedAuthorIdsFor(currentUserId);

		int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
		}

		Pageable pageable = PageRequest.of(0, effectiveLimit + 1);
		List<CourseComment> comments = (cursor == null || cursor.isBlank())
				? courseCommentRepository.findFirstPage(courseId, pageable)
				: courseCommentRepository.findNextPage(courseId, cursorCreatedAt(cursor), cursor, pageable);
		// 참고: DB 페이지를 먼저 가져온 뒤 차단 필터링을 하므로, 차단된 작성자가 많으면 실제
		// effectiveLimit보다 적은 항목이 반환될 수 있다(MVP 범위 밖 최적화 — docs 설계 문서 참고).
		comments = comments.stream().filter(c -> !blockedAuthorIds.contains(c.getAuthorId())).toList();

		boolean hasMore = comments.size() > effectiveLimit;
		List<CourseComment> page = hasMore ? comments.subList(0, effectiveLimit) : comments;
		String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

		Map<String, Long> replyCounts = replyCountsFor(page);
		List<CourseCommentResponse> responses =
				page.stream().map(comment -> toResponse(comment, replyCounts.getOrDefault(comment.getId(), 0L))).toList();
		return new ListResult(responses, new PageInfo(nextCursor));
	}
```

`listReplies`를 아래로 교체:

```java
	@Transactional(readOnly = true)
	public List<CourseCommentResponse> listReplies(String courseId, String parentCommentId) {
		Course course = findCourseOrThrow(courseId);
		String currentUserId = currentUserProvider.currentUserIdOrNull();
		requireReadableCourse(course, currentUserId);
		findCommentOrThrow(courseId, parentCommentId);
		Set<String> blockedAuthorIds = blockedAuthorIdsFor(currentUserId);

		List<CourseComment> replies = courseCommentRepository
				.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId, PageRequest.of(0, MAX_REPLIES)).stream()
				.filter(reply -> !blockedAuthorIds.contains(reply.getAuthorId()))
				.toList();
		Map<String, Long> replyCounts = replyCountsFor(replies);
		return replies.stream()
				.map(reply -> toResponse(reply, replyCounts.getOrDefault(reply.getId(), 0L)))
				.toList();
	}
```

`replyCountsFor` 메서드 바로 위에 헬퍼 메서드를 추가:

```java
	private Set<String> blockedAuthorIdsFor(String currentUserId) {
		return currentUserId == null ? Set.of() : blockRepository.findBlockedIdsByBlockerId(currentUserId);
	}

```

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.CourseCommentControllerTest"`
Expected: PASS — 전체 통과.

- [ ] **Step 5: 백엔드 전체 테스트 스위트 실행**

Run: `cd backend && ./gradlew test`
Expected: PASS — 전체 테스트 그린(0 실패).

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/CourseCommentService.java \
        backend/src/test/java/com/runvas/backend/community/CourseCommentControllerTest.java
git commit -m "feat(backend): 코스 댓글/대댓글 조회에 차단 필터링 반영"
```

---

### Task 6: 모바일 — 타입 정의 및 Block API 클라이언트

**Files:**
- Modify: `mobile/src/types/index.ts`
- Create: `mobile/src/services/blockApi.ts`

**Interfaces:**
- Consumes: `mobile/src/types/index.ts`의 `PublicProfile`.
- Produces: `BlockedUser` 타입, `blockUser(userId, accessToken): Promise<BlockedUser>`,
  `unblockUser(userId, accessToken): Promise<void>`, `fetchBlockedUsers(accessToken): Promise<BlockedUser[]>` —
  Task 7~9가 그대로 임포트해서 쓴다.

- [ ] **Step 1: `types/index.ts`에 `BlockedUser` 타입 추가**

`mobile/src/types/index.ts`의 `export type ReportTargetType = 'posts' | 'comments' | 'course-comments';`
줄 바로 다음에 추가:

```ts

// docs/api-contract.md POST/GET /blocks 응답의 단일 항목과 1:1 대응.
export interface BlockedUser {
  blockedUser: PublicProfile;
  createdAt: string;
}
```

- [ ] **Step 2: `blockApi.ts` 작성**

`mobile/src/services/blockApi.ts`:

```ts
import { BlockedUser } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function blockUser(userId: string, accessToken: string): Promise<BlockedUser> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/blocks/${userId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (response.status !== 201 && response.status !== 200) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as BlockedUser;
}

export async function unblockUser(userId: string, accessToken: string): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/blocks/${userId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }
}

export async function fetchBlockedUsers(accessToken: string): Promise<BlockedUser[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/blocks`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const data = (await response.json()) as { blocks: BlockedUser[] };
  return data.blocks;
}
```

- [ ] **Step 3: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음(0 errors) — 이 두 파일은 아직 아무 화면에서도 쓰이지 않으므로 기존 코드에
영향이 없다.

- [ ] **Step 4: 커밋**

```bash
git add mobile/src/types/index.ts mobile/src/services/blockApi.ts
git commit -m "feat(mobile): 차단 API 타입 및 클라이언트 추가"
```

---

### Task 7: 모바일 — 게시글 상세 화면에 차단 버튼 추가

**Files:**
- Modify: `mobile/src/screens/PostDetailScreen.tsx`

**Interfaces:**
- Consumes: `mobile/src/services/blockApi.ts`의 `blockUser` (Task 6).

- [ ] **Step 1: import 추가**

`mobile/src/screens/PostDetailScreen.tsx`의 `import { ReportReason, ReportTargetType } from '../types';`
줄 다음에 추가:

```ts
import { blockUser } from '../services/blockApi';
```

- [ ] **Step 2: 차단 핸들러 추가**

`handleConfirmReport` 함수 바로 다음에 추가:

```ts
  const handleBlockUser = (userId: string, nickname: string) => {
    if (!requireAuth() || !accessToken) return;
    Alert.alert(
      '사용자 차단',
      `${nickname}님을 차단하시겠어요? 차단하면 이 사용자의 게시글과 댓글이 더 이상 보이지 않습니다.`,
      [
        { text: '취소', style: 'cancel' },
        {
          text: '차단',
          style: 'destructive',
          onPress: async () => {
            try {
              await blockUser(userId, accessToken);
              Alert.alert('차단 완료', `${nickname}님을 차단했습니다.`);
              loadPost();
            } catch (e: unknown) {
              Alert.alert('차단 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
            }
          },
        },
      ]
    );
  };
```

`loadPost`는 이 게시글 자체가 차단한 작성자의 글일 경우 재조회 시 `404`를 받아 `loadPost` 내부의
`catch` 블록이 `navigation.goBack()`을 호출하므로, 게시글 작성자를 차단한 경우와 댓글 작성자를
차단한 경우 모두 `loadPost()` 한 번으로 올바르게 처리된다(전자는 화면 이탈, 후자는 댓글 목록에서
차단된 작성자의 댓글이 사라진 상태로 갱신).

- [ ] **Step 3: 게시글 작성자 차단 버튼 추가**

`reportButton` `TouchableOpacity`(게시글 본문 영역, `onPress={() => setReportTarget({ type: 'posts', id: post.id })}`) 바로 다음에 추가:

```tsx
              {user != null && user.id !== post.author.id && (
                <TouchableOpacity
                  style={styles.reportButton}
                  onPress={() => handleBlockUser(post.author.id, post.author.nickname)}
                  activeOpacity={0.7}
                >
                  <Text style={styles.reportButtonLabel}>차단</Text>
                </TouchableOpacity>
              )}
```

- [ ] **Step 4: 댓글 작성자 차단 버튼 추가**

댓글 `renderItem`의 신고 버튼(`onPress={() => setReportTarget({ type: 'comments', id: item.id })}`)이
있는 `commentActionsRow` `View` 안, 그 `TouchableOpacity` 바로 다음에 추가:

```tsx
                {!isMine && user != null && (
                  <View style={styles.commentActionsRow}>
                    <TouchableOpacity
                      onPress={() => setReportTarget({ type: 'comments', id: item.id })}
                      activeOpacity={0.7}
                    >
                      <Text style={styles.commentActionLabel}>신고</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      onPress={() => handleBlockUser(item.author.id, item.author.nickname)}
                      activeOpacity={0.7}
                    >
                      <Text style={styles.commentActionLabel}>차단</Text>
                    </TouchableOpacity>
                  </View>
                )}
```

(기존 `commentActionsRow` 블록 전체를 이 코드로 교체 — `TouchableOpacity`가 하나 늘어난 것 외
동일하다.)

- [ ] **Step 5: 타입 체크 및 번들 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음.

Run(백그라운드로 `npx expo start` 띄운 뒤): `curl "http://localhost:8081/index.bundle?platform=ios&dev=true" -o /dev/null -w "%{http_code}\n"`
Expected: `200`

- [ ] **Step 6: 커밋**

```bash
git add mobile/src/screens/PostDetailScreen.tsx
git commit -m "feat(mobile): 게시글 상세 화면에 차단 버튼 추가"
```

---

### Task 8: 모바일 — 코스 댓글에 차단 버튼 추가

**Files:**
- Modify: `mobile/src/components/CourseCommentItem.tsx`
- Modify: `mobile/src/screens/CourseDetailScreen.tsx`

**Interfaces:**
- Consumes: `mobile/src/services/blockApi.ts`의 `blockUser` (Task 6).
- Produces: `CourseCommentItem`의 새 prop `onBlock: (userId: string, nickname: string) => void`.

- [ ] **Step 1: `CourseCommentItem`에 `onBlock` prop 추가**

`mobile/src/components/CourseCommentItem.tsx`의 `Props` 인터페이스에서
`onReport: (commentId: string) => void;` 다음 줄에 추가:

```ts
  onBlock: (userId: string, nickname: string) => void;
```

함수 매개변수 구조 분해에서 `onReport,` 다음 줄에 `onBlock,`을 추가한다.

`actionsRow`의 신고 버튼 블록을 아래로 교체:

```tsx
        {!isMine && (
          <TouchableOpacity onPress={() => onReport(comment.id)} activeOpacity={0.7}>
            <Text style={styles.actionText}>신고</Text>
          </TouchableOpacity>
        )}
        {!isMine && (
          <TouchableOpacity
            onPress={() => onBlock(comment.author.id, comment.author.nickname)}
            activeOpacity={0.7}
          >
            <Text style={styles.actionText}>차단</Text>
          </TouchableOpacity>
        )}
```

재귀적으로 자기 자신을 렌더링하는 대댓글 `<CourseCommentItem ... onReport={onReport} />` 호출부에
`onBlock={onBlock}`을 추가한다.

- [ ] **Step 2: `CourseDetailScreen`에 차단 핸들러 및 prop 연결**

`mobile/src/screens/CourseDetailScreen.tsx`의 `import { postReport } from '../services/reportApi';`
다음 줄에 추가:

```ts
import { blockUser } from '../services/blockApi';
```

`handleConfirmReport` 함수 바로 다음에 추가:

```ts
  const handleBlockCommentAuthor = (userId: string, nickname: string) => {
    if (!requireAuth() || !accessToken) return;
    Alert.alert(
      '사용자 차단',
      `${nickname}님을 차단하시겠어요? 차단하면 이 사용자의 게시글과 댓글이 더 이상 보이지 않습니다.`,
      [
        { text: '취소', style: 'cancel' },
        {
          text: '차단',
          style: 'destructive',
          onPress: async () => {
            try {
              await blockUser(userId, accessToken);
              Alert.alert('차단 완료', `${nickname}님을 차단했습니다.`);
              loadComments();
            } catch (e: unknown) {
              Alert.alert('차단 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
            }
          },
        },
      ]
    );
  };
```

`<CourseCommentItem ... onReport={handleReportComment} ... />` 호출부(최상위 댓글 목록 렌더링,
`comments.map((comment) => (...))` 안)에 `onBlock={handleBlockCommentAuthor}`를 추가한다.

- [ ] **Step 3: 타입 체크 및 번들 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 — `CourseCommentItem`의 재귀 호출부까지 `onBlock`이 빠짐없이 전달됐는지
타입 체크가 검증한다(누락 시 `Property 'onBlock' is missing` 에러 발생).

Run(백그라운드 `npx expo start` 상태에서): `curl "http://localhost:8081/index.bundle?platform=ios&dev=true" -o /dev/null -w "%{http_code}\n"`
Expected: `200`

- [ ] **Step 4: 커밋**

```bash
git add mobile/src/components/CourseCommentItem.tsx mobile/src/screens/CourseDetailScreen.tsx
git commit -m "feat(mobile): 코스 댓글에 차단 버튼 추가"
```

---

### Task 9: 모바일 — 차단 목록 관리 화면 및 프로필 진입점

**Files:**
- Create: `mobile/src/screens/BlockedUsersScreen.tsx`
- Modify: `mobile/src/navigation/types.ts`
- Modify: `App.tsx`
- Modify: `mobile/src/screens/ProfileScreen.tsx`

**Interfaces:**
- Consumes: `mobile/src/services/blockApi.ts`의 `fetchBlockedUsers`/`unblockUser` (Task 6).
- Produces: `RootStackParamList`에 `BlockedUsers: undefined` 라우트 — 이후 다른 화면에서
  `navigation.navigate('BlockedUsers')`로 진입 가능.

- [ ] **Step 1: 네비게이션 타입에 라우트 추가**

`mobile/src/navigation/types.ts`의 `CourseEdit: { courseId: string };` 다음 줄에 추가:

```ts
  BlockedUsers: undefined;
```

- [ ] **Step 2: `BlockedUsersScreen` 작성**

`mobile/src/screens/BlockedUsersScreen.tsx`:

```tsx
import React, { useCallback, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, Alert, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { fetchBlockedUsers, unblockUser } from '../services/blockApi';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { BlockedUser } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'BlockedUsers'>;

export default function BlockedUsersScreen({ navigation }: Props) {
  const { accessToken } = useAuth();
  const [blocks, setBlocks] = useState<BlockedUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const loadBlocks = useCallback(async () => {
    if (!accessToken) return;
    setIsLoading(true);
    try {
      const result = await fetchBlockedUsers(accessToken);
      setBlocks(result);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, [accessToken]);

  useFocusEffect(
    useCallback(() => {
      loadBlocks();
    }, [loadBlocks])
  );

  const handleUnblock = (userId: string, nickname: string) => {
    if (!accessToken) return;
    Alert.alert('차단 해제', `${nickname}님의 차단을 해제하시겠어요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '해제',
        onPress: async () => {
          try {
            await unblockUser(userId, accessToken);
            setBlocks((prev) => prev.filter((b) => b.blockedUser.id !== userId));
          } catch (e: unknown) {
            Alert.alert('해제 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          }
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>차단한 사용자</Text>
        <View style={styles.headerSpacer} />
      </View>

      {isLoading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={styles.loading} />
      ) : (
        <FlatList
          data={blocks}
          keyExtractor={(item) => item.blockedUser.id}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <Text style={styles.nickname}>{item.blockedUser.nickname}</Text>
              <TouchableOpacity
                onPress={() => handleUnblock(item.blockedUser.id, item.blockedUser.nickname)}
                activeOpacity={0.7}
              >
                <Text style={styles.unblockLabel}>차단 해제</Text>
              </TouchableOpacity>
            </View>
          )}
          ListEmptyComponent={<Text style={styles.emptyText}>차단한 사용자가 없습니다.</Text>}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  headerSpacer: {
    width: 24,
  },
  loading: {
    marginTop: 40,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  nickname: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.gray900,
  },
  unblockLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.primary,
  },
  emptyText: {
    paddingHorizontal: 20,
    paddingVertical: 24,
    textAlign: 'center',
    fontSize: 13,
    color: Colors.gray400,
  },
});
```

- [ ] **Step 3: `App.tsx`에 화면 등록**

`import CourseEditScreen from './src/screens/CourseEditScreen';` 다음 줄에 추가:

```ts
import BlockedUsersScreen from './src/screens/BlockedUsersScreen';
```

`<Stack.Screen name="CourseEdit" component={CourseEditScreen} />` 다음 줄에 추가:

```tsx
          <Stack.Screen name="BlockedUsers" component={BlockedUsersScreen} />
```

- [ ] **Step 4: `ProfileScreen`에 진입점 추가**

`ProfileScreen`은 `RootTabParamList`의 `Profile` 탭으로 렌더링되지만, 루트 스택의 `BlockedUsers`로
이동해야 한다. `BoardScreen.tsx`가 이미 같은 상황(탭 화면에서 스택 라우트로 이동)을
`CompositeScreenProps`로 타입 처리하고 있으므로 동일한 패턴을 따른다.

`mobile/src/screens/ProfileScreen.tsx`의 기존 import 블록을 아래로 교체(`CompositeScreenProps`,
`BottomTabScreenProps`, `NativeStackScreenProps`, `RootTabParamList`/`RootStackParamList` 추가):

```ts
import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { patchMe } from '../services/authApi';
import PaceSelector from '../components/PaceSelector';
import WithdrawalReasonModal from '../components/WithdrawalReasonModal';
import { WithdrawalReason } from '../types';
import { DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { formatPace } from '../utils/format';
import { Colors } from '../constants/theme';
import { RootTabParamList, RootStackParamList } from '../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Profile'>,
  NativeStackScreenProps<RootStackParamList>
>;
```

컴포넌트 선언을 `export default function ProfileScreen({ navigation }: Props) {`로 교체한다(기존
`export default function ProfileScreen() {`에는 매개변수가 없었다).

`withdrawButton` `TouchableOpacity` 바로 다음(로그인 상태 `<>...</>` 블록 안, `withdrawButton` 뒤,
`</>` 앞)에 추가:

```tsx
            <TouchableOpacity
              style={styles.blockedUsersButton}
              activeOpacity={0.6}
              onPress={() => navigation.navigate('BlockedUsers')}
            >
              <Text style={styles.blockedUsersButtonText}>차단한 사용자</Text>
            </TouchableOpacity>
```

`styles`의 `withdrawButtonText` 다음에 스타일 추가:

```ts
  blockedUsersButton: {
    marginTop: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  blockedUsersButtonText: {
    color: Colors.gray500,
    fontSize: 14,
    fontWeight: '600',
  },
```

- [ ] **Step 5: 타입 체크 및 번들 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음.

Run(백그라운드 `npx expo start` 상태에서): `curl "http://localhost:8081/index.bundle?platform=ios&dev=true" -o /dev/null -w "%{http_code}\n"`
Expected: `200`

- [ ] **Step 6: 실기기/시뮬레이터에서 수동 확인**

`mobile/CLAUDE.md`의 변경 후 검증 규칙에 따라 시뮬레이터에서 직접 확인한다:
1. 게시글 상세에서 다른 사용자의 게시글/댓글에 "차단" 버튼이 보이고, 누르면 확인 다이얼로그가 뜬다.
2. 차단 후 게시글 목록(게시판 탭)에 돌아가면 그 작성자의 글이 더 이상 보이지 않는다.
3. 코스 상세의 댓글에서도 "차단" 버튼이 동작한다.
4. 프로필 > "차단한 사용자"에서 방금 차단한 사용자가 보이고, "차단 해제"를 누르면 목록에서
   사라지며 이후 그 사용자의 콘텐츠가 다시 보인다.

- [ ] **Step 7: 커밋**

```bash
git add mobile/src/screens/BlockedUsersScreen.tsx mobile/src/navigation/types.ts App.tsx \
        mobile/src/screens/ProfileScreen.tsx
git commit -m "feat(mobile): 차단 목록 관리 화면 및 프로필 진입점 추가"
```
