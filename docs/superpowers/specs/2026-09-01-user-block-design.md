# 사용자 차단 기능 설계

작성일: 2026-09-01
관련 문서: `docs/api-contract.md` §Report APIs, `docs/data-model.md` §Report,
`docs/superpowers/specs/2026-08-23-content-report-design.md`(신고 기능 — 이번 기능이 이어받는
Apple 재제출 요구사항의 나머지 절반)

## 배경

`docs/superpowers/specs/2026-08-23-content-report-design.md`(PR #67)에서 Apple App Review
Guideline 2.1 재제출 요청이 "UGC 신고/차단 메커니즘"을 함께 언급했다고 기록했지만, 실제 구현
범위는 "신고"로만 한정했고 "범위 제외"에 "사용자 차단(block) 기능"을 명시적으로 남겨두었다.
Apple의 요구사항 중 차단 부분이 아직 충족되지 않은 상태이므로, 재제출 전에 이 기능을 추가한다.
Guideline 1.2(User Generated Content)는 신고와 차단을 함께 요구하는 것이 일반적이다.

## 정책 결정 (브레인스토밍 확정 사항)

- **차단 방향**: 단방향. A가 B를 차단하면 A의 화면에서만 B의 콘텐츠가 사라진다. B는 A의 콘텐츠를
  계속 보고 댓글/좋아요도 정상적으로 남길 수 있다 — 쓰기 경로는 건드리지 않고 조회 경로만
  필터링한다.
- **차단 적용 범위**: `Post`, `Comment`(게시글 댓글), `CourseComment`(코스 댓글) 세 종류.
  `Course` 자체(코스 목록/지도 탐색/상세)는 이번 범위에서 제외 — 신고 기능과 동일한 범위 경계를
  그대로 따른다.
- **진입점**: 이미 있는 신고 버튼과 같은 위치(게시글 상세 케밥 메뉴, 게시글 댓글 각 행, 코스 댓글
  각 행)에 "차단하기"를 추가한다. 본인 콘텐츠가 아닐 때만 노출하는 조건도 신고 버튼과 동일하게
  재사용한다.
- **차단 해제**: 프로필(설정) 화면에 "차단한 사용자" 목록 화면을 신규로 추가해 언제든 해제할 수
  있게 한다.
- **자기 자신 차단 금지**: `400 VALIDATION_ERROR`로 막는다 (신고와 달리, 자기 차단은 의미가 없어
  막는 것이 합리적).
- **중복 차단**: 멱등 처리 — 이미 차단한 사용자를 다시 차단해도 에러 없이 `200 OK`.
- **알림 없음**: 차단당한 사용자에게 알리지 않는다 (신고 기능과 동일한 원칙, MVP가 푸시 알림
  자체를 제외 범위로 둠).
- **관리자 대시보드 변경 없음**: 이 기능은 사용자 간 개인화된 필터링이라 관리자가 볼 필요가 없다
  (신고와 다른 점 — 신고는 관리자 처리 대상이지만 차단은 순수 사용자 로컬 설정).

## 데이터 모델 변경

### `docs/data-model.md`에 추가할 `Block` 섹션

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `blockerId` | string | Y | 차단한 사용자 ID. API 응답에는 노출하지 않음 |
| `blockedId` | string | Y | 차단당한 사용자 ID |
| `createdAt` | string | Y | ISO 8601 차단 시각 |

`Report`처럼 상태(`status`)가 바뀌는 엔티티가 아니라 `Bookmark`/`Like`처럼 "존재 = 활성 상태,
삭제 = 해제"인 순수 관계 테이블이다.

### `blocks` 테이블 (신규, `V16__create_blocks.sql`)

```sql
CREATE TABLE blocks (
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE INDEX idx_blocks_blocker_id ON blocks (blocker_id);
```

복합 PK로 `(blocker_id, blocked_id)` 중복을 DB 레벨에서 막는다. `blocker_id` 인덱스는 조회
경로(`PostService.list` 등)에서 "내가 차단한 ID 목록"을 매 요청마다 조회하는 데 쓰인다.

## API 계약 변경 (모바일 ↔ 백엔드)

`docs/api-contract.md`에 `## Report APIs` 다음에 `## Block APIs` 섹션을 추가한다.

### POST /blocks/{userId}

사용자를 차단한다.

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

차단을 해제한다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | string | 차단 해제할 사용자 ID |

#### Response: 204 No Content

차단 중이 아니었어도 동일하게 204를 반환한다(멱등).

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음

### GET /blocks

내가 차단한 사용자 목록을 조회한다.

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

`GET /me/bookmarked-courses`와 동일하게 이번 범위에서는 실제 커서 페이지네이션을 구현하지 않고
차단 목록 전체를 한 번에 반환한다 (`pageInfo.nextCursor`는 항상 `null`). 사용자 한 명이 차단하는
대상 수가 페이지네이션이 필요할 만큼 커질 가능성이 낮다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않음

## 기존 조회 API 필터링 반영

아래 API들은 `Optional` 인증이며, **로그인한 요청에 한해** 호출자가 차단한 사용자의 콘텐츠를
결과에서 제외한다. 비로그인 요청은 차단 목록이 없으므로 기존과 동일하게 동작한다.

| API | 필터링 규칙 |
| --- | --- |
| `GET /posts` | `post.author`가 차단 대상이면 목록에서 제외 |
| `GET /posts/{postId}` | 작성자가 차단 대상이면 `404 NOT_FOUND` |
| `GET /posts/{postId}/comments` | 작성자가 차단 대상인 댓글을 목록에서 제외 |
| `GET /courses/{courseId}/comments` | 작성자가 차단 대상인 코스 댓글을 목록에서 제외 |
| `GET /courses/{courseId}/comments/{commentId}/replies` | 위와 동일 |

`GET /posts/{postId}`를 `404`로 처리하는 이유는 기존 공통 에러 표의 `404 NOT_FOUND` 정의("대상
리소스가 없거나 접근 가능한 상태가 아닙니다")에 그대로 부합하고, 클라이언트가 "차단됨"과
"삭제됨"을 구분해 처리할 필요가 없어 단순하기 때문이다.

`GET /courses`, `GET /courses/{courseId}` 등 코스 관련 조회는 이번 범위에서 변경하지 않는다.

## 백엔드 구현

### `Block` 엔티티 (신규, `com.runvas.backend.community`)

`Bookmark`와 동일한 `@EmbeddedId` 복합키 패턴을 그대로 따른다.

```java
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

	public String getBlockerId() { return id.getBlockerId(); }
	public String getBlockedId() { return id.getBlockedId(); }

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

`blockerId`/`blockedId`는 `Post.authorId`와 동일하게 `user_` 접두 없는 raw UUID 문자열로
저장한다.

### `BlockRepository` (신규)

```java
public interface BlockRepository extends JpaRepository<Block, Block.BlockId> {

	List<Block> findByIdBlockerIdOrderByCreatedAtDesc(String blockerId);

	@Query("select b.id.blockedId from Block b where b.id.blockerId = :blockerId")
	Set<String> findBlockedIdsByBlockerId(String blockerId);
}
```

`findBlockedIdsByBlockerId`는 `PostService`/`CommentService`/`CourseCommentService`의 목록
조회에서 "차단된 작성자 ID 집합"을 한 번만 조회해 `Set.contains`로 필터링하는 데 쓴다 — 이미
`PostService.list`가 `postRepository.findAllByOrderByCreatedAtDesc().stream().filter(...)`로
인메모리 필터링하는 것과 동일한 방식이라 자연스럽게 필터 하나만 추가하면 된다.

### `BlockService` (신규)

`BookmarkService`와 동일한 구조(`add`/`remove`/`listByUser`에 대응하는 `block`/`unblock`/
`listByUser`).

```java
@Service
@RequiredArgsConstructor
public class BlockService {

	private final BlockRepository blockRepository;
	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public Result block(String targetUserIdPathValue) {
		String blockerId = currentUserProvider.requireUserId();
		String blockedId = parseUserId(targetUserIdPathValue); // "user_" 접두 제거 + UUID 파싱, 실패 시 NOT_FOUND

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
	// (ReportService.Result와 동일한 관례 — ReportService.java, ReportController.java 참고).
	public record Result(BlockResponse response, boolean isNew) {}

	public record ListResult(List<BlockResponse> blocks, PageInfo pageInfo) {}
}
```

`unblock`은 `deleteById`를 조건 없이 호출한다 — Spring Data JPA의 `deleteById`는 대상이 없어도
예외를 던지지 않으므로 별도 존재 확인 없이 멱등하게 동작한다(`LikeService.unlike`이 명시적으로
`existsById`를 먼저 확인하는 것과 달리, 여기서는 좋아요 수 감소 같은 부수 효과가 없어 그럴
필요가 없다).

### `BlockController` (신규)

```java
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

201/200 분기는 `LikeService.like`처럼 서비스가 신규 생성 여부를 판단해 반환하도록
`BlockResponse`에 `isNew` 플래그를 추가하는 방식으로 구현한다(위 `block()` 의사코드의 생략된
부분 — 실제 구현 시 `Report`의 `isNew` 패턴을 재사용).

### `PostService`/`CommentService`/`CourseCommentService` 필터링 추가

세 서비스 모두 생성자에 `BlockRepository`를 주입받고, 목록/상세 조회 시작 부분에서 한 번만
차단 집합을 조회한다.

```java
// PostService.list
String currentUserId = currentUserProvider.currentUserIdOrNull();
Set<String> blockedAuthorIds = currentUserId == null
		? Set.of()
		: blockRepository.findBlockedIdsByBlockerId(currentUserId);

List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc().stream()
		.filter(post -> !blockedAuthorIds.contains(post.getAuthorId()))
		// ... 기존 attachedCourseId/q/tag 필터 유지
		.toList();
```

```java
// PostService.getById
Post post = findPostOrThrow(postId);
String currentUserId = currentUserProvider.currentUserIdOrNull();
if (currentUserId != null
		&& blockRepository.findById(new Block.BlockId(currentUserId, post.getAuthorId())).isPresent()) {
	throw new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다");
}
```

`CommentService.list`, `CourseCommentService.list`(및 대댓글 조회)도 동일하게 `blockedAuthorIds`
집합을 한 번 구해 `!blockedAuthorIds.contains(comment.getAuthorId())` 필터를 스트림에 추가한다.

## 모바일 구현

### `src/services/blockApi.ts` (신규)

`reportApi.ts`와 동일한 구조.

```ts
export async function blockUser(userId: string, accessToken: string): Promise<BlockedUser> { ... }
export async function unblockUser(userId: string, accessToken: string): Promise<void> { ... }
export async function fetchBlockedUsers(accessToken: string): Promise<BlockedUser[]> { ... }
```

### 차단 버튼 노출 위치

기존 신고 버튼과 같은 케밥 메뉴에 "차단하기" 항목을 추가한다.

- 게시글 상세 화면(`PostDetailScreen.tsx`): 케밥 메뉴에 "신고" 아래 "차단하기" 추가
- 게시글 댓글 각 행: 동일
- 코스 상세 화면의 코스 댓글 각 행(`CourseCommentItem`): 동일

탭하면 `Alert.alert`로 "이 사용자를 차단하시겠어요? 차단하면 이 사용자의 게시글과 댓글이 더 이상
보이지 않습니다."를 확인한 뒤 `blockUser` 호출. 성공 시 토스트 안내 + 현재 화면의 목록에서 해당
작성자 콘텐츠를 로컬 상태로 즉시 제거(낙관적 업데이트) — 다음 진입 시에는 서버가 필터링해서
아예 내려주지 않는다.

### `BlockedUsersScreen` (신규, `src/screens/`)

`GET /blocks` 목록을 렌더링하고, 각 행에 "차단 해제" 버튼(`unblockUser` 호출 후 로컬 목록에서
제거)을 둔다. 목록이 비어 있으면 "차단한 사용자가 없습니다" 빈 상태 문구를 보여준다.

### `ProfileScreen.tsx`

"차단한 사용자" 메뉴 항목을 추가해 `BlockedUsersScreen`으로 이동한다(로그아웃/회원탈퇴 항목과
같은 목록 UI에 추가).

### `mobile/src/navigation/types.ts`

```ts
export type RootStackParamList = {
  // ... 기존 항목
  BlockedUsers: undefined;
};
```

## 에러 처리 / 엣지 케이스

| 상황 | 처리 |
| --- | --- |
| 자기 자신을 차단하려는 요청 | `400 VALIDATION_ERROR` |
| 이미 차단한 사용자를 다시 차단 | 새로 만들지 않고 기존 차단을 `200 OK`로 반환 |
| 존재하지 않거나 형식이 잘못된 `userId`로 차단 | `404 NOT_FOUND` |
| 차단하지 않은 사용자를 해제 요청 | 에러 없이 `204 No Content` |
| 차단한 사용자가 이후 탈퇴 | 탈퇴 계정은 `docs/data-model.md`의 "탈퇴한 사용자" 표시 규칙에 따라
콘텐츠가 남되 작성자 표시만 바뀐다. 차단 관계 자체는 그대로 유지되지만 실질적 의미는 없어진다
(탈퇴 사용자 콘텐츠는 신규 차단 대상이 되지 않으므로 별도 정리 로직 불필요) |
| 차단한 사용자의 게시글 상세를 딥링크 등으로 직접 접근 | `404 NOT_FOUND` (목록에서 안 보이는 것과
동일하게 처리) |
| 비로그인 사용자의 차단/해제/목록 요청 | `401 UNAUTHORIZED` |

## 범위 제외

- 양방향 차단(상호 콘텐츠 숨김, 상호작용 차단)
- 코스(`Course`) 조회 필터링 — 코스 목록/지도 탐색/상세는 이번 범위에서 제외
- 차단 시 기존에 이미 단 댓글/좋아요를 소급 삭제하는 기능 — 조회만 필터링하고 데이터는 그대로 둠
- 차단당한 사용자에게 알림
- 차단 사유 기록 — 신고와 달리 사유 없이 즉시 차단
- 관리자 대시보드 연동 — 순수 사용자 개인화 설정이라 운영자가 볼 필요 없음

## 테스트

- **백엔드**:
  - `BlockServiceTest`(신규): 정상 차단(`isNew=true`), 중복 차단 시 기존 재사용(`isNew=false`),
    자기 자신 차단 시 `VALIDATION_ERROR`, 존재하지 않는 사용자 차단 시 `NOT_FOUND`, 차단 해제
    멱등성(차단하지 않은 상태에서 해제해도 예외 없음).
  - `PostServiceTest`/`CommentServiceTest`/`CourseCommentServiceTest`에 케이스 추가: 차단한
    사용자의 게시글/댓글이 목록에서 제외되는지, 비로그인 요청은 필터링되지 않는지, 차단한
    사용자의 게시글 상세 조회 시 `404`가 나는지.
  - `BlockControllerTest`: `POST`/`DELETE`/`GET /api/blocks` 인증 필요, 정상 케이스 `201`/`200`/
    `204` 구분.
- **모바일**: `npx tsc --noEmit` 통과 + 실기기/시뮬레이터에서 게시글 작성자 차단 → 게시글 목록/
  상세/댓글에서 사라지는지, 설정 > 차단한 사용자 목록에서 해제 → 다시 보이는지 확인
  (`mobile/CLAUDE.md` 검증 규칙).

## 검증 기준

- 로그인한 사용자가 다른 사용자를 차단하면 `201`과 함께 `blocks` 테이블에 행이 생긴다.
- 차단 후 `GET /posts`, `GET /posts/{id}/comments`, `GET /courses/{id}/comments`에서 그
  사용자의 콘텐츠가 더 이상 보이지 않는다.
- 차단한 사용자의 게시글 상세를 직접 조회하면 `404`가 반환된다.
- 차단당한 사용자 본인은 아무 영향 없이 정상적으로 앱을 사용할 수 있다(단방향 확인).
- `GET /blocks`에서 차단 목록이 보이고, 해제하면 목록에서 사라지며 이전에 숨겨졌던 콘텐츠가
  다시 보인다.
- `docs/api-contract.md`의 `Block APIs` 예시와 실제 구현 동작이 일치한다.
