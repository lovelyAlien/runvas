# 콘텐츠 신고 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 게시글/댓글/코스댓글을 신고할 수 있는 모바일 API와, 신고된 콘텐츠를 관리자가 삭제하거나 기각할 수 있는 관리자 대시보드 화면을 추가한다.

**Architecture:** 백엔드 `com.runvas.backend.community` 패키지에 `Report` 엔티티와 `POST /api/reports/{targetType}/{targetId}`를 `Like` 기능과 동일한 패턴으로 추가한다. `com.runvas.backend.admin` 패키지에 신고 목록 조회(`/admin/reports`)와 처리(삭제/기각) 컨트롤러를 추가하고, 실제 콘텐츠 삭제는 `PostService`/`CommentService`/`CourseCommentService`에 새로 추가하는 `deleteAsAdmin` 메서드로 위임한다. 모바일은 `WithdrawalReasonModal`과 동일한 구조의 `ReportReasonModal`을 만들어 게시글 상세/댓글/코스댓글 화면에 연결한다.

**Tech Stack:** Spring Boot(Java) + JPA + PostgreSQL(Flyway) 백엔드, Thymeleaf 관리자 화면, React Native(Expo) 모바일.

## Global Constraints

- 신고 대상은 `Post`, `Comment`(게시글 댓글), `CourseComment`(코스 댓글)만 — `Course` 자체는 제외.
- 신고 사유는 `SPAM`/`ABUSIVE`/`INAPPROPRIATE`/`OTHER` enum, `OTHER`일 때만 `reasonDetail` 1-200자 필수.
- 같은 사용자가 같은 대상에 이미 `PENDING` 신고가 있으면 새로 만들지 않고 기존 것을 재사용(멱등).
- 콘텐츠 삭제는 기존 작성자 전용 `delete()`가 아니라 새로 만드는 관리자 전용 `deleteAsAdmin()`을 통해서만 수행하고, 이미 삭제된 대상이면 조용히 넘어간다(예외를 던지지 않음).
- 자기 신고 제한 없음, 신고자에게 결과 알림 없음, 자동 숨김/삭제 없음 — 전부 관리자 수동 판단.
- 커밋 메시지는 한글 Conventional Commits, AI 저작자 트레일러 금지(`~/.githooks`가 강제).
- 작업은 `.claude/worktrees/content-report-design` 워크트리(브랜치 `docs/content-report-design`)에서 계속한다. 이미 스펙 문서가 이 브랜치에 커밋되어 있다.

관련 문서: `docs/superpowers/specs/2026-08-23-content-report-design.md`(원본 설계, 아래 계획과
다른 부분은 코드베이스 실사 결과를 반영해 이 계획이 우선한다 — 특히 테스트 구성은 기존 컨벤션에
맞춰 스펙보다 단순화했다).

---

### Task 1: 문서 갱신 — api-contract.md / data-model.md / admin-dashboard.md

**Files:**
- Modify: `docs/api-contract.md` (`## Like APIs` 섹션 뒤, `L1642` 부근에 `## Report APIs` 추가)
- Modify: `docs/data-model.md` (`## LikeTargetType` 섹션 뒤, `L199` 부근에 `## Report` 추가)
- Modify: `docs/admin-dashboard.md` (화면 목록 표 + 명시적 제외 범위 수정)

이 프로젝트는 docs-first 원칙(`CLAUDE.md`)이라 코드보다 문서를 먼저 커밋한다. 내용은 이미
`docs/superpowers/specs/2026-08-23-content-report-design.md`에서 확정된 내용을 그대로 옮긴다.

- [ ] **Step 1: `docs/api-contract.md`에 Report APIs 섹션 추가**

`## Like APIs` 섹션의 마지막 줄(`- 404 NOT_FOUND: 대상이 없음` 다음, 파일 끝 직전) 뒤에 추가:

```markdown

## Report APIs

### POST /reports/{targetType}/{targetId}

게시글, 댓글, 코스 댓글을 신고합니다.

#### Auth

`Required`

#### Path Params

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `targetType` | string | `posts` \| `comments` \| `course-comments` |
| `targetId` | string | 신고 대상 ID |

#### Request Body

```json
{
  "reason": "ABUSIVE",
  "reasonDetail": null
}
```

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | Y | `SPAM` \| `ABUSIVE` \| `INAPPROPRIATE` \| `OTHER` |
| `reasonDetail` | string \| null | N | `reason`이 `OTHER`일 때만 필수 (1-200자) |

#### Response: 201 Created (신규) 또는 200 OK (기존 PENDING 신고 재사용)

```json
{
  "id": "report_123",
  "targetType": "posts",
  "targetId": "post_456",
  "status": "PENDING",
  "createdAt": "2026-08-23T10:00:00Z"
}
```

#### Errors

- `400 VALIDATION_ERROR`: 지원하지 않는 `targetType`, `reason` 누락/미지원 값, `reason`이
  `OTHER`인데 `reasonDetail` 누락
- `401 UNAUTHORIZED`: 로그인하지 않음
- `404 NOT_FOUND`: 대상이 없음(이미 삭제된 콘텐츠 포함)
```

- [ ] **Step 2: `docs/data-model.md`에 Report 섹션 추가**

`## LikeTargetType` 섹션(값 표 포함) 바로 뒤, `## CourseBookmark` 섹션 앞에 추가:

```markdown

## Report

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | string | Y | 신고 ID |
| `reporterId` | string | Y | 신고한 사용자 ID. API 응답에는 노출하지 않음 |
| `targetType` | ReportTargetType | Y | `POST` \| `COMMENT` \| `COURSE_COMMENT` |
| `targetId` | string | Y | 신고 대상 ID |
| `reason` | ReportReason | Y | `SPAM` \| `ABUSIVE` \| `INAPPROPRIATE` \| `OTHER` |
| `reasonDetail` | string \| null | N | `reason`이 `OTHER`일 때만 필수 (1-200자) |
| `status` | ReportStatus | Y | `PENDING` \| `RESOLVED` \| `DISMISSED` |
| `createdAt` | string | Y | ISO 8601 신고 시각 |
| `resolvedAt` | string \| null | N | 관리자가 삭제/기각 처리한 시각 |

같은 사용자가 같은 대상에 이미 `PENDING` 신고를 넣었으면 새로 만들지 않고 기존 것을 재사용한다.
`RESOLVED`/`DISMISSED`로 끝난 신고 이후 재신고는 새로 생성된다.
```

- [ ] **Step 3: `docs/admin-dashboard.md` 화면 목록에 신고 행 추가**

`| `/admin/posts` | 게시글 목록. ... |` 행 다음 줄에 추가:

```markdown
| `/admin/reports` | 신고 목록. 기본 필터 `PENDING`, `status`/`targetType`로 필터링, 페이지네이션(20건). 각 행에 신고 대상 콘텐츠 미리보기, 신고자 ID, 사유, 신고 시각. `PENDING` 신고에 한해 "삭제"(콘텐츠 하드 삭제 + 신고 `RESOLVED`)/"기각"(신고만 `DISMISSED`) 처리 가능 |
```

- [ ] **Step 4: `docs/admin-dashboard.md` 명시적 제외 범위 수정**

`- 회원 정지, 코스/게시글/댓글 삭제 등 관리 쓰기 액션은 없습니다. 모든 화면은 조회 전용입니다.`
줄을 다음으로 교체:

```markdown
- 신고 처리(콘텐츠 삭제/기각)를 제외한 나머지 화면(회원/코스/게시글 목록)은 여전히 조회
  전용입니다. 회원 정지 기능은 없습니다.
```

- [ ] **Step 5: Commit**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/content-report-design
git add docs/api-contract.md docs/data-model.md docs/admin-dashboard.md
git commit -m "$(cat <<'EOF'
docs: 콘텐츠 신고 API/데이터모델/관리자 화면 계약 정의

게시글/댓글/코스댓글 신고 생성 API와 관리자 신고 처리(삭제/기각)
화면을 구현하기 전에 계약을 먼저 확정한다.
EOF
)"
```

---

### Task 2: DB 마이그레이션 + Report 엔티티 + enum + Repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__create_reports.sql`
- Create: `backend/src/main/java/com/runvas/backend/community/ReportTargetType.java`
- Create: `backend/src/main/java/com/runvas/backend/community/ReportReason.java`
- Create: `backend/src/main/java/com/runvas/backend/community/ReportStatus.java`
- Create: `backend/src/main/java/com/runvas/backend/community/Report.java`
- Create: `backend/src/main/java/com/runvas/backend/community/ReportRepository.java`
- Test: `backend/src/test/java/com/runvas/backend/community/ReportRepositoryTest.java`

**Interfaces:**
- Produces: `Report(String reporterId, ReportTargetType targetType, String targetId, ReportReason reason, String reasonDetail)` 생성자, `Report.resolve()`/`Report.dismiss()`, `Report.getStatus()`/`getTargetType()`/`getTargetId()`/`getReporterId()`/`getReason()`/`getReasonDetail()`/`getId()`/`getCreatedAt()`/`getResolvedAt()`.
- Produces: `ReportRepository.findByReporterIdAndTargetTypeAndTargetIdAndStatus(String, ReportTargetType, String, ReportStatus): Optional<Report>`, `.findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType, String, ReportStatus): List<Report>`, `.findAllByStatus(ReportStatus, Pageable): Page<Report>`, `.findAllByStatusAndTargetType(ReportStatus, ReportTargetType, Pageable): Page<Report>`.

- [ ] **Step 1: Flyway 마이그레이션 작성**

`backend/src/main/resources/db/migration/V15__create_reports.sql`:

```sql
CREATE TABLE reports (
    id VARCHAR(36) PRIMARY KEY,
    reporter_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    reason VARCHAR(20) NOT NULL,
    reason_detail VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_reports_status_created_at ON reports (status, created_at);
CREATE UNIQUE INDEX uq_reports_pending_target
    ON reports (reporter_id, target_type, target_id)
    WHERE status = 'PENDING';
```

- [ ] **Step 2: enum 3개 작성**

`backend/src/main/java/com/runvas/backend/community/ReportTargetType.java`:

```java
package com.runvas.backend.community;

public enum ReportTargetType {
	POST,
	COMMENT,
	COURSE_COMMENT
}
```

`backend/src/main/java/com/runvas/backend/community/ReportReason.java`:

```java
package com.runvas.backend.community;

public enum ReportReason {
	SPAM,
	ABUSIVE,
	INAPPROPRIATE,
	OTHER
}
```

`backend/src/main/java/com/runvas/backend/community/ReportStatus.java`:

```java
package com.runvas.backend.community;

public enum ReportStatus {
	PENDING,
	RESOLVED,
	DISMISSED
}
```

- [ ] **Step 3: `Report` 엔티티 작성**

`backend/src/main/java/com/runvas/backend/community/Report.java`:

```java
package com.runvas.backend.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// docs/data-model.md Report와 1:1.
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column(nullable = false)
	private String reporterId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportTargetType targetType;

	@Column(nullable = false)
	private String targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportReason reason;

	@Column(length = 200)
	private String reasonDetail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status = ReportStatus.PENDING;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	private Instant resolvedAt;

	public Report(String reporterId, ReportTargetType targetType, String targetId, ReportReason reason, String reasonDetail) {
		this.reporterId = reporterId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.reasonDetail = reasonDetail;
	}

	public void resolve() {
		this.status = ReportStatus.RESOLVED;
		this.resolvedAt = Instant.now();
	}

	public void dismiss() {
		this.status = ReportStatus.DISMISSED;
		this.resolvedAt = Instant.now();
	}
}
```

- [ ] **Step 4: `ReportRepository` 작성**

`backend/src/main/java/com/runvas/backend/community/ReportRepository.java`:

```java
package com.runvas.backend.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {

	Optional<Report> findByReporterIdAndTargetTypeAndTargetIdAndStatus(
			String reporterId, ReportTargetType targetType, String targetId, ReportStatus status);

	List<Report> findAllByTargetTypeAndTargetIdAndStatus(
			ReportTargetType targetType, String targetId, ReportStatus status);

	Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);

	Page<Report> findAllByStatusAndTargetType(ReportStatus status, ReportTargetType targetType, Pageable pageable);
}
```

- [ ] **Step 5: 실패하는 리포지토리 테스트 작성**

`backend/src/test/java/com/runvas/backend/community/ReportRepositoryTest.java`:

```java
package com.runvas.backend.community;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ReportRepositoryTest {

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
	ReportRepository reportRepository;

	@Test
	void pendingUniqueIndexPreventsDuplicatePendingReportForSameReporterAndTarget() {
		reportRepository.saveAndFlush(new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null));

		Optional<Report> found = reportRepository.findByReporterIdAndTargetTypeAndTargetIdAndStatus(
				"reporter-1", ReportTargetType.POST, "post-1", ReportStatus.PENDING);

		assertThat(found).isPresent();
	}

	@Test
	void findAllByTargetTypeAndTargetIdAndStatusReturnsOnlyMatchingPendingReports() {
		Report pending1 = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null));
		Report pending2 = reportRepository.saveAndFlush(
				new Report("reporter-2", ReportTargetType.POST, "post-1", ReportReason.ABUSIVE, null));
		reportRepository.saveAndFlush(
				new Report("reporter-3", ReportTargetType.POST, "post-2", ReportReason.SPAM, null));

		List<Report> results = reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
				ReportTargetType.POST, "post-1", ReportStatus.PENDING);

		assertThat(results).extracting(Report::getId).containsExactlyInAnyOrder(pending1.getId(), pending2.getId());
	}
}
```

- [ ] **Step 6: 테스트 실행 확인 (Docker 필요)**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.ReportRepositoryTest"`
Expected: PASS (Docker가 없으면 `disabledWithoutDocker = true`로 스킵됨 — 스킵도 정상)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V15__create_reports.sql \
  backend/src/main/java/com/runvas/backend/community/ReportTargetType.java \
  backend/src/main/java/com/runvas/backend/community/ReportReason.java \
  backend/src/main/java/com/runvas/backend/community/ReportStatus.java \
  backend/src/main/java/com/runvas/backend/community/Report.java \
  backend/src/main/java/com/runvas/backend/community/ReportRepository.java \
  backend/src/test/java/com/runvas/backend/community/ReportRepositoryTest.java
git commit -m "$(cat <<'EOF'
feat(backend): Report 엔티티와 마이그레이션 추가

신고 대상(게시글/댓글/코스댓글), 사유, 처리 상태를 저장하는
reports 테이블을 추가한다. 같은 사용자가 같은 대상에 PENDING
신고를 중복 생성하지 못하도록 부분 유니크 인덱스로 막는다.
EOF
)"
```

---

### Task 3: ReportService + ReportController + SecurityConfig

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/community/dto/ReportRequest.java`
- Create: `backend/src/main/java/com/runvas/backend/community/dto/ReportResponse.java`
- Create: `backend/src/main/java/com/runvas/backend/community/ReportService.java`
- Create: `backend/src/main/java/com/runvas/backend/community/ReportController.java`
- Modify: `backend/src/main/java/com/runvas/global/security/SecurityConfig.java:47` (마지막 `requestMatchers` 뒤, `.anyRequest()` 앞)
- Test: `backend/src/test/java/com/runvas/backend/community/ReportControllerTest.java`

**Interfaces:**
- Consumes: Task 2의 `Report`, `ReportRepository`, `ReportTargetType`, `ReportReason`, `ReportStatus`.
- Consumes: `com.runvas.backend.auth.CurrentUserProvider.requireUserId(): String`, `com.runvas.backend.common.ApiException`/`ErrorCode`(기존).
- Produces: `POST /api/reports/{targetType}/{targetId}` (인증 필요) — 201/200 응답.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`backend/src/test/java/com/runvas/backend/community/ReportControllerTest.java`:

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ReportControllerTest {

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
								{ "title": "신고 테스트용 글", "body": "본문" }
								"""))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.post.id");
	}

	@Test
	void reportingPostReturns201AndPending() throws Exception {
		String accessToken = createUserAndToken("reporter1");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/reports/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "SPAM" }
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetType").value("posts"))
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void reportingSameTargetTwiceIsIdempotent() throws Exception {
		String accessToken = createUserAndToken("reporter2");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/reports/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "SPAM" }
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/reports/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "ABUSIVE" }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void reportingWithOtherReasonWithoutDetailReturns400() throws Exception {
		String accessToken = createUserAndToken("reporter3");
		String postId = createPost(accessToken);

		mockMvc.perform(post("/api/reports/posts/" + postId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "OTHER" }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void reportingUnsupportedTargetTypeReturns400() throws Exception {
		String accessToken = createUserAndToken("reporter4");

		mockMvc.perform(post("/api/reports/courses/some-id")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "SPAM" }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void reportingUnknownPostReturns404() throws Exception {
		String accessToken = createUserAndToken("reporter5");

		mockMvc.perform(post("/api/reports/posts/unknown-post")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "SPAM" }
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void reportingWithoutAuthReturns401() throws Exception {
		mockMvc.perform(post("/api/reports/posts/some-id")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "SPAM" }
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reportingCourseCommentReturns201() throws Exception {
		String accessToken = createUserAndToken("reporter6");
		Course course = courseRepository.saveAndFlush(new Course(
				"author-x",
				"신고 테스트 코스",
				null,
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				List.of(new RoutePoint(37.0, 127.0, 0), new RoutePoint(37.001, 127.001, 1)),
				200,
				120,
				new GeoBounds(new GeoPoint(37.0, 127.0), new GeoPoint(37.001, 127.001)),
				CourseVisibility.PUBLIC,
				Set.of()));

		MvcResult commentResult = mockMvc.perform(post("/api/courses/" + course.getId() + "/comments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.param("body", "댓글 본문"))
				.andReturn();
		String commentId = JsonPath.read(commentResult.getResponse().getContentAsString(), "$.comment.id");

		mockMvc.perform(post("/api/reports/course-comments/" + commentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "reason": "INAPPROPRIATE" }
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetType").value("course-comments"));
	}
}
```

- [ ] **Step 2: 테스트 실행해 실패(컴파일 에러) 확인**

Run: `cd backend && ./gradlew compileTestJava`
Expected: FAIL — `ReportRequest`, `ReportController` 등이 없어서 컴파일 에러.

- [ ] **Step 3: `ReportRequest`/`ReportResponse` DTO 작성**

`backend/src/main/java/com/runvas/backend/community/dto/ReportRequest.java`:

```java
package com.runvas.backend.community.dto;

import com.runvas.backend.community.ReportReason;
import jakarta.validation.constraints.NotNull;

// docs/api-contract.md POST /reports/{targetType}/{targetId} 요청 본문.
public record ReportRequest(@NotNull ReportReason reason, String reasonDetail) {
}
```

`backend/src/main/java/com/runvas/backend/community/dto/ReportResponse.java`:

```java
package com.runvas.backend.community.dto;

import com.runvas.backend.community.Report;
import java.time.Instant;

// docs/api-contract.md POST /reports/{targetType}/{targetId} 응답.
public record ReportResponse(String id, String targetType, String targetId, String status, Instant createdAt) {
	public static ReportResponse from(String targetTypePathValue, Report report) {
		return new ReportResponse(
				report.getId(), targetTypePathValue, report.getTargetId(), report.getStatus().name(), report.getCreatedAt());
	}
}
```

- [ ] **Step 4: `ReportService` 작성**

`backend/src/main/java/com/runvas/backend/community/ReportService.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.auth.CurrentUserProvider;
import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.dto.ReportRequest;
import com.runvas.backend.community.dto.ReportResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

	private final ReportRepository reportRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final CourseCommentRepository courseCommentRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public Result report(String targetTypePathValue, String targetId, ReportRequest request) {
		validateReasonDetail(request.reason(), request.reasonDetail());
		ReportTargetType targetType = parseTargetType(targetTypePathValue);
		String reporterId = currentUserProvider.requireUserId();
		requireTargetExists(targetType, targetId);

		Optional<Report> existing = reportRepository.findByReporterIdAndTargetTypeAndTargetIdAndStatus(
				reporterId, targetType, targetId, ReportStatus.PENDING);
		if (existing.isPresent()) {
			return new Result(ReportResponse.from(targetTypePathValue, existing.get()), false);
		}

		Report saved = reportRepository.save(
				new Report(reporterId, targetType, targetId, request.reason(), request.reasonDetail()));
		return new Result(ReportResponse.from(targetTypePathValue, saved), true);
	}

	private void validateReasonDetail(ReportReason reason, String reasonDetail) {
		if (reason == ReportReason.OTHER && (reasonDetail == null || reasonDetail.isBlank())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "reasonDetail is required when reason is OTHER");
		}
		if (reasonDetail != null && reasonDetail.length() > 200) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "reasonDetail must be at most 200 characters");
		}
	}

	private ReportTargetType parseTargetType(String value) {
		return switch (value) {
			case "posts" -> ReportTargetType.POST;
			case "comments" -> ReportTargetType.COMMENT;
			case "course-comments" -> ReportTargetType.COURSE_COMMENT;
			default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "unsupported targetType: " + value);
		};
	}

	private void requireTargetExists(ReportTargetType targetType, String targetId) {
		boolean exists = switch (targetType) {
			case POST -> postRepository.existsById(targetId);
			case COMMENT -> commentRepository.existsById(targetId);
			case COURSE_COMMENT -> courseCommentRepository.existsById(targetId);
		};
		if (!exists) {
			throw new ApiException(ErrorCode.NOT_FOUND, "대상이 없습니다");
		}
	}

	public record Result(ReportResponse response, boolean isNew) {
	}
}
```

- [ ] **Step 5: `ReportController` 작성**

`backend/src/main/java/com/runvas/backend/community/ReportController.java`:

```java
package com.runvas.backend.community;

import com.runvas.backend.community.dto.ReportRequest;
import com.runvas.backend.community.dto.ReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@PostMapping("/{targetType}/{targetId}")
	public ResponseEntity<ReportResponse> report(
			@PathVariable String targetType,
			@PathVariable String targetId,
			@Valid @RequestBody ReportRequest request) {
		ReportService.Result result = reportService.report(targetType, targetId, request);
		HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.response());
	}
}
```

- [ ] **Step 6: `SecurityConfig`에 라우트 추가**

`backend/src/main/java/com/runvas/global/security/SecurityConfig.java:47` (`.requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}/comments/{commentId}").authenticated()` 다음 줄)에 추가:

```java
                        .requestMatchers(HttpMethod.POST, "/api/reports/{targetType}/{targetId}").authenticated()
```

- [ ] **Step 7: 테스트 실행해 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.ReportControllerTest"`
Expected: PASS (7개 테스트 전부, Docker 없으면 스킵)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/community/dto/ReportRequest.java \
  backend/src/main/java/com/runvas/backend/community/dto/ReportResponse.java \
  backend/src/main/java/com/runvas/backend/community/ReportService.java \
  backend/src/main/java/com/runvas/backend/community/ReportController.java \
  backend/src/main/java/com/runvas/global/security/SecurityConfig.java \
  backend/src/test/java/com/runvas/backend/community/ReportControllerTest.java
git commit -m "$(cat <<'EOF'
feat(backend): 콘텐츠 신고 생성 API 추가

POST /api/reports/{targetType}/{targetId}로 게시글/댓글/코스댓글을
신고할 수 있다. 같은 대상에 이미 PENDING 신고가 있으면 새로 만들지
않고 재사용해 멱등하게 처리한다.
EOF
)"
```

---

### Task 4: PostService/CommentService/CourseCommentService에 deleteAsAdmin 추가

**Files:**
- Modify: `backend/src/main/java/com/runvas/backend/community/PostService.java:114` (`delete` 메서드 뒤)
- Modify: `backend/src/main/java/com/runvas/backend/community/CommentService.java:76` (`delete` 메서드 뒤)
- Modify: `backend/src/main/java/com/runvas/backend/community/CourseCommentService.java:117` (`delete` 메서드 뒤)

**Interfaces:**
- Produces: `PostService.deleteAsAdmin(String postId): void`, `CommentService.deleteAsAdmin(String commentId): void`, `CourseCommentService.deleteAsAdmin(String commentId): void` — 셋 다 작성자 검증(`requireAuthor`) 없이 대상이 있으면 삭제하고, 없으면 조용히 아무것도 하지 않는다(예외 없음).

기존 `delete()`는 작성자 본인 검증을 하므로 관리자 경로에서 그대로 쓸 수 없다. Task 6에서
`AdminReportActionService`가 이 메서드들을 호출한다. 이 메서드들은 기존 `*ControllerTest`가 이미
있는 파일에 메서드만 추가하는 것이라 별도 단위 테스트 파일을 새로 만들지 않고, Task 6의
`AdminReportControllerTest`에서 "삭제 처리 후 실제로 콘텐츠가 사라지는지"로 통합 검증한다(이
저장소는 `PostService`/`CommentService`/`CourseCommentService`에 대한 단위 테스트 파일이 원래
없고, `*ControllerTest`로만 검증하는 기존 컨벤션을 따른다).

- [ ] **Step 1: `PostService.deleteAsAdmin` 추가**

`backend/src/main/java/com/runvas/backend/community/PostService.java:114`(`delete` 메서드의 닫는
중괄호) 바로 뒤에 추가:

```java

	@Transactional
	public void deleteAsAdmin(String postId) {
		postRepository.findById(postId).ifPresent(postRepository::delete);
	}
```

- [ ] **Step 2: `CommentService.deleteAsAdmin` 추가**

`backend/src/main/java/com/runvas/backend/community/CommentService.java:76`(`delete` 메서드의 닫는
중괄호) 바로 뒤에 추가:

```java

	@Transactional
	public void deleteAsAdmin(String commentId) {
		commentRepository.findById(commentId).ifPresent(comment -> {
			commentRepository.delete(comment);
			postRepository.findById(comment.getPostId()).ifPresent(Post::decrementCommentCount);
		});
	}
```

- [ ] **Step 3: `CourseCommentService.deleteAsAdmin` 추가**

`backend/src/main/java/com/runvas/backend/community/CourseCommentService.java:117`(`delete`
메서드의 닫는 중괄호) 바로 뒤에 추가:

```java

	@Transactional
	public void deleteAsAdmin(String commentId) {
		courseCommentRepository.findById(commentId).ifPresent(courseCommentRepository::delete);
	}
```

하위 대댓글은 `course_comments.parent_comment_id`의 `ON DELETE CASCADE`(`V9__add_parent_comment_id_to_course_comments.sql`)로 DB가 자동 처리하므로 애플리케이션 코드에서 별도로 지울 필요가 없다.

- [ ] **Step 4: 전체 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: PASS (아직 아무도 호출하지 않지만 컴파일은 통과해야 한다)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/community/PostService.java \
  backend/src/main/java/com/runvas/backend/community/CommentService.java \
  backend/src/main/java/com/runvas/backend/community/CourseCommentService.java
git commit -m "$(cat <<'EOF'
feat(backend): 게시글/댓글/코스댓글에 관리자 전용 삭제 메서드 추가

기존 delete()는 작성자 본인만 삭제 가능하도록 검증하기 때문에
신고 처리(관리자 삭제) 경로에는 쓸 수 없다. requireAuthor 검증을
생략하고 대상이 이미 없으면 조용히 넘어가는 deleteAsAdmin을
추가한다.
EOF
)"
```

---

### Task 5: AdminReportQueryService + AdminReportActionService

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminReportView.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminReportQueryService.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java`

**Interfaces:**
- Consumes: Task 2의 `Report`/`ReportRepository`/`ReportTargetType`/`ReportStatus`, Task 4의 `PostService.deleteAsAdmin`/`CommentService.deleteAsAdmin`/`CourseCommentService.deleteAsAdmin`.
- Produces: `AdminReportQueryService.search(ReportStatus, ReportTargetType targetTypeOrNull, int page, int size): Page<AdminReportView>`, `AdminReportActionService.resolve(String reportId): void`, `AdminReportActionService.dismiss(String reportId): void`.

이 두 서비스는 `CurrentUserProvider`가 필요 없어서(관리자 인증은 세션이 처리) `AdminStatsService`처럼
순수 Mockito 단위 테스트로 검증한다 — Testcontainers 없이 빠르게 돈다.

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.CommentService;
import com.runvas.backend.community.CourseCommentService;
import com.runvas.backend.community.PostService;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportReason;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminReportActionServiceTest {

	private final ReportRepository reportRepository = mock(ReportRepository.class);
	private final PostService postService = mock(PostService.class);
	private final CommentService commentService = mock(CommentService.class);
	private final CourseCommentService courseCommentService = mock(CourseCommentService.class);
	private final AdminReportActionService adminReportActionService =
			new AdminReportActionService(reportRepository, postService, commentService, courseCommentService);

	@Test
	void resolveDeletesPostAndResolvesAllPendingReportsForSameTarget() {
		Report target = new Report("reporter-1", ReportTargetType.POST, "post-1", ReportReason.SPAM, null);
		Report otherPendingForSameTarget =
				new Report("reporter-2", ReportTargetType.POST, "post-1", ReportReason.ABUSIVE, null);
		when(reportRepository.findById("report-1")).thenReturn(Optional.of(target));
		when(reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
						ReportTargetType.POST, "post-1", ReportStatus.PENDING))
				.thenReturn(List.of(target, otherPendingForSameTarget));

		adminReportActionService.resolve("report-1");

		verify(postService).deleteAsAdmin("post-1");
		verify(commentService, never()).deleteAsAdmin(any());
		verify(courseCommentService, never()).deleteAsAdmin(any());
		assertThat(target.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		assertThat(otherPendingForSameTarget.getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	void resolveOnUnknownReportThrowsNotFound() {
		when(reportRepository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> adminReportActionService.resolve("missing"))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void dismissMarksReportDismissedWithoutTouchingContent() {
		Report report = new Report("reporter-1", ReportTargetType.COMMENT, "comment-1", ReportReason.OTHER, "상세");
		when(reportRepository.findById("report-2")).thenReturn(Optional.of(report));

		adminReportActionService.dismiss("report-2");

		assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
		verify(postService, never()).deleteAsAdmin(any());
		verify(commentService, never()).deleteAsAdmin(any());
		verify(courseCommentService, never()).deleteAsAdmin(any());
	}
}
```

- [ ] **Step 2: 실행해 컴파일 실패 확인**

Run: `cd backend && ./gradlew compileTestJava`
Expected: FAIL — `AdminReportActionService`가 없어서 컴파일 에러.

- [ ] **Step 3: `AdminReportView` 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminReportView.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.ReportReason;
import com.runvas.backend.community.ReportTargetType;
import java.time.Instant;

// /admin/reports 화면 전용 뷰 모델. Report에 콘텐츠 미리보기를 덧붙인다.
public record AdminReportView(
		String id,
		ReportTargetType targetType,
		String targetId,
		String contentPreview,
		String reporterId,
		ReportReason reason,
		String reasonDetail,
		Instant createdAt) {
}
```

- [ ] **Step 4: `AdminReportQueryService` 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminReportQueryService.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.Comment;
import com.runvas.backend.community.CommentRepository;
import com.runvas.backend.community.CourseComment;
import com.runvas.backend.community.CourseCommentRepository;
import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminReportQueryService {

	private final ReportRepository reportRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final CourseCommentRepository courseCommentRepository;

	public AdminReportQueryService(
			ReportRepository reportRepository,
			PostRepository postRepository,
			CommentRepository commentRepository,
			CourseCommentRepository courseCommentRepository) {
		this.reportRepository = reportRepository;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.courseCommentRepository = courseCommentRepository;
	}

	public Page<AdminReportView> search(ReportStatus status, ReportTargetType targetType, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(0, page), size);
		Page<Report> reports = targetType == null
				? reportRepository.findAllByStatus(status, pageable)
				: reportRepository.findAllByStatusAndTargetType(status, targetType, pageable);
		return reports.map(this::toView);
	}

	private AdminReportView toView(Report report) {
		String preview = switch (report.getTargetType()) {
			case POST -> postRepository.findById(report.getTargetId()).map(Post::getTitle).orElse(null);
			case COMMENT -> commentRepository.findById(report.getTargetId()).map(Comment::getBody).orElse(null);
			case COURSE_COMMENT -> courseCommentRepository.findById(report.getTargetId())
					.map(CourseComment::getBody)
					.orElse(null);
		};
		return new AdminReportView(
				report.getId(),
				report.getTargetType(),
				report.getTargetId(),
				preview,
				report.getReporterId(),
				report.getReason(),
				report.getReasonDetail(),
				report.getCreatedAt());
	}
}
```

- [ ] **Step 5: `AdminReportActionService` 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import com.runvas.backend.community.CommentService;
import com.runvas.backend.community.CourseCommentService;
import com.runvas.backend.community.PostService;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportActionService {

	private final ReportRepository reportRepository;
	private final PostService postService;
	private final CommentService commentService;
	private final CourseCommentService courseCommentService;

	@Transactional
	public void resolve(String reportId) {
		Report report = findOrThrow(reportId);

		switch (report.getTargetType()) {
			case POST -> postService.deleteAsAdmin(report.getTargetId());
			case COMMENT -> commentService.deleteAsAdmin(report.getTargetId());
			case COURSE_COMMENT -> courseCommentService.deleteAsAdmin(report.getTargetId());
		}

		reportRepository
				.findAllByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(), ReportStatus.PENDING)
				.forEach(Report::resolve);
	}

	@Transactional
	public void dismiss(String reportId) {
		findOrThrow(reportId).dismiss();
	}

	private Report findOrThrow(String reportId) {
		return reportRepository.findById(reportId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "신고가 없습니다"));
	}
}
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportActionServiceTest"`
Expected: PASS (3개 테스트, Docker 불필요 — 순수 Mockito)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminReportView.java \
  backend/src/main/java/com/runvas/backend/admin/AdminReportQueryService.java \
  backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java \
  backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java
git commit -m "$(cat <<'EOF'
feat(backend): 신고 조회/처리 서비스 추가

관리자 대시보드에서 신고 목록을 콘텐츠 미리보기와 함께 조회하고,
삭제(RESOLVED)/기각(DISMISSED) 처리를 할 수 있는 서비스를
추가한다. 삭제 시 같은 대상에 걸린 다른 PENDING 신고도 함께
정리한다.
EOF
)"
```

---

### Task 6: AdminReportController + Thymeleaf 화면

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminReportController.java`
- Create: `backend/src/main/resources/templates/admin/reports.html`
- Modify: `backend/src/main/resources/templates/admin/fragments/nav.html`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminReportControllerTest.java`

**Interfaces:**
- Consumes: Task 5의 `AdminReportQueryService.search(...)`, `AdminReportActionService.resolve/dismiss(...)`.
- Produces: `GET /admin/reports`, `POST /admin/reports/{reportId}/resolve`, `POST /admin/reports/{reportId}/dismiss`.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminReportControllerTest.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportReason;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminReportControllerTest {

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
	PostRepository postRepository;

	@Autowired
	ReportRepository reportRepository;

	@Test
	@WithMockUser(username = "operator", roles = "ADMIN")
	void reportsListShowsPendingReportWithContentPreview() throws Exception {
		Post post = postRepository.saveAndFlush(new Post("author-1", "신고당할 게시글", "본문", null, Set.of()));
		reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, post.getId(), ReportReason.SPAM, null));

		mockMvc.perform(get("/admin/reports"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("신고당할 게시글")));
	}

	@Test
	@WithMockUser(username = "operator", roles = "ADMIN")
	void resolveDeletesPostAndRedirects() throws Exception {
		Post post = postRepository.saveAndFlush(new Post("author-1", "삭제될 게시글", "본문", null, Set.of()));
		Report report = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, post.getId(), ReportReason.SPAM, null));

		mockMvc.perform(post("/admin/reports/" + report.getId() + "/resolve").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/reports"));

		assertThat(postRepository.existsById(post.getId())).isFalse();
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	@WithMockUser(username = "operator", roles = "ADMIN")
	void dismissKeepsPostAndMarksDismissed() throws Exception {
		Post post = postRepository.saveAndFlush(new Post("author-1", "유지될 게시글", "본문", null, Set.of()));
		Report report = reportRepository.saveAndFlush(
				new Report("reporter-1", ReportTargetType.POST, post.getId(), ReportReason.SPAM, null));

		mockMvc.perform(post("/admin/reports/" + report.getId() + "/dismiss").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/reports"));

		assertThat(postRepository.existsById(post.getId())).isTrue();
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
				.isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void listRedirectsWhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/admin/reports"))
				.andExpect(status().is3xxRedirection());
	}
}
```

- [ ] **Step 2: 실행해 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportControllerTest"`
Expected: FAIL — `AdminReportController`가 없어서 `/admin/reports`가 404.

- [ ] **Step 3: `AdminReportController` 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminReportController.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminReportController {

	private static final int PAGE_SIZE = 20;

	private final AdminReportQueryService adminReportQueryService;
	private final AdminReportActionService adminReportActionService;

	public AdminReportController(
			AdminReportQueryService adminReportQueryService, AdminReportActionService adminReportActionService) {
		this.adminReportQueryService = adminReportQueryService;
		this.adminReportActionService = adminReportActionService;
	}

	@GetMapping("/admin/reports")
	String reports(
			@RequestParam(name = "status", defaultValue = "PENDING") ReportStatus status,
			@RequestParam(name = "targetType", required = false) ReportTargetType targetType,
			@RequestParam(name = "page", defaultValue = "0") int page,
			Model model) {
		Page<AdminReportView> result = adminReportQueryService.search(status, targetType, page, PAGE_SIZE);
		model.addAttribute("status", status);
		model.addAttribute("targetType", targetType);
		model.addAttribute("reports", result.getContent());
		model.addAttribute("page", result.getNumber());
		model.addAttribute("totalPages", result.getTotalPages());
		return "admin/reports";
	}

	@PostMapping("/admin/reports/{reportId}/resolve")
	String resolve(@PathVariable String reportId) {
		adminReportActionService.resolve(reportId);
		return "redirect:/admin/reports";
	}

	@PostMapping("/admin/reports/{reportId}/dismiss")
	String dismiss(@PathVariable String reportId) {
		adminReportActionService.dismiss(reportId);
		return "redirect:/admin/reports";
	}
}
```

- [ ] **Step 4: `admin/reports.html` 템플릿 작성**

`backend/src/main/resources/templates/admin/reports.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>Runvas 관리자 - 신고</title>
    <link rel="stylesheet" th:href="@{/admin/assets/admin.css}"/>
</head>
<body>
<div class="admin-shell">
    <nav th:replace="~{admin/fragments/nav :: nav}"></nav>
    <h1>신고 목록</h1>
    <form method="get" th:action="@{/admin/reports}" class="field-row">
        <select name="status">
            <option value="PENDING" th:selected="${status.name() == 'PENDING'}">대기중</option>
            <option value="RESOLVED" th:selected="${status.name() == 'RESOLVED'}">처리됨(삭제)</option>
            <option value="DISMISSED" th:selected="${status.name() == 'DISMISSED'}">기각됨</option>
        </select>
        <select name="targetType">
            <option value="" th:selected="${targetType == null}">전체</option>
            <option value="POST" th:selected="${targetType != null and targetType.name() == 'POST'}">게시글</option>
            <option value="COMMENT" th:selected="${targetType != null and targetType.name() == 'COMMENT'}">게시글 댓글</option>
            <option value="COURSE_COMMENT" th:selected="${targetType != null and targetType.name() == 'COURSE_COMMENT'}">코스 댓글</option>
        </select>
        <button type="submit" class="btn">필터</button>
    </form>
    <table>
        <colgroup>
            <col style="width:10%"/>
            <col style="width:12%"/>
            <col style="width:22%"/>
            <col style="width:14%"/>
            <col style="width:12%"/>
            <col style="width:16%"/>
            <col style="width:14%"/>
        </colgroup>
        <thead>
        <tr>
            <th>ID</th>
            <th>대상 유형</th>
            <th>콘텐츠 미리보기</th>
            <th>신고자 ID</th>
            <th>사유</th>
            <th>신고 시각</th>
            <th>처리</th>
        </tr>
        </thead>
        <tbody>
        <tr th:each="report : ${reports}">
            <td class="mono" th:text="${report.id()}" th:title="${report.id()}"></td>
            <td th:text="${report.targetType()}"></td>
            <td th:text="${report.contentPreview()} ?: '(삭제된 콘텐츠)'"></td>
            <td class="mono" th:text="${report.reporterId()}" th:title="${report.reporterId()}"></td>
            <td th:text="${report.reasonDetail()} != null ? (${report.reason()} + ' - ' + ${report.reasonDetail()}) : ${report.reason()}"></td>
            <td class="mono" th:text="${report.createdAt()}"></td>
            <td>
                <form method="post" th:if="${status.name() == 'PENDING'}"
                      th:action="@{'/admin/reports/' + ${report.id()} + '/resolve'}" style="display:inline">
                    <button type="submit" class="btn">삭제</button>
                </form>
                <form method="post" th:if="${status.name() == 'PENDING'}"
                      th:action="@{'/admin/reports/' + ${report.id()} + '/dismiss'}" style="display:inline">
                    <button type="submit" class="btn">기각</button>
                </form>
            </td>
        </tr>
        </tbody>
    </table>
    <div class="pagination">
        <span class="mono" th:text="'페이지 ' + (${page} + 1) + ' / ' + ${totalPages}"></span>
        <a th:if="${page > 0}" th:href="@{/admin/reports(status=${status}, targetType=${targetType}, page=${page - 1})}">이전</a>
        <a th:if="${page + 1 < totalPages}" th:href="@{/admin/reports(status=${status}, targetType=${targetType}, page=${page + 1})}">다음</a>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 5: 네비게이션에 링크 추가**

`backend/src/main/resources/templates/admin/fragments/nav.html`의
`<a th:href="@{/admin/posts}">게시글</a>` 줄 바로 뒤에 추가:

```html
    <a th:href="@{/admin/reports}">신고</a>
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportControllerTest"`
Expected: PASS (4개 테스트)

- [ ] **Step 7: 전체 백엔드 테스트 스위트 실행**

Run: `cd backend && ./gradlew test`
Expected: PASS (기존 테스트 포함 전부 통과 — `deleteAsAdmin` 추가나 `SecurityConfig` 수정이
기존 동작을 깨지 않았는지 확인)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminReportController.java \
  backend/src/main/resources/templates/admin/reports.html \
  backend/src/main/resources/templates/admin/fragments/nav.html \
  backend/src/test/java/com/runvas/backend/admin/AdminReportControllerTest.java
git commit -m "$(cat <<'EOF'
feat(backend): 관리자 신고 목록/처리 화면 추가

/admin/reports에서 상태·대상유형별로 신고를 필터링해 보고,
PENDING 신고에 한해 삭제(콘텐츠 하드 삭제) 또는 기각 처리를 할 수
있다. admin-dashboard의 "조회 전용" 원칙을 신고 처리에 한해 처음
깨는 화면이다.
EOF
)"
```

---

### Task 7: 모바일 타입 + reportApi.ts

**Files:**
- Modify: `mobile/src/types/index.ts` (`WithdrawalReason` 타입 뒤에 추가)
- Create: `mobile/src/services/reportApi.ts`

**Interfaces:**
- Produces: `ReportReason = 'SPAM' | 'ABUSIVE' | 'INAPPROPRIATE' | 'OTHER'`, `ReportTargetType = 'posts' | 'comments' | 'course-comments'`, `postReport(targetType: ReportTargetType, targetId: string, reason: ReportReason, reasonDetail: string | null, accessToken: string): Promise<void>`.

- [ ] **Step 1: 타입 추가**

`mobile/src/types/index.ts`의 `WithdrawalReason` 타입 선언 뒤에 추가:

```ts

export type ReportReason = 'SPAM' | 'ABUSIVE' | 'INAPPROPRIATE' | 'OTHER';
export type ReportTargetType = 'posts' | 'comments' | 'course-comments';
```

- [ ] **Step 2: `reportApi.ts` 작성**

`mobile/src/services/reportApi.ts`:

```ts
import { ReportReason, ReportTargetType } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function postReport(
  targetType: ReportTargetType,
  targetId: string,
  reason: ReportReason,
  reasonDetail: string | null,
  accessToken: string
): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/reports/${targetType}/${targetId}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ reason, reasonDetail }),
  });

  if (response.status !== 201 && response.status !== 200) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
```

- [ ] **Step 3: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 (아직 아무도 `postReport`를 호출하지 않으므로 unused export 경고도 없음 —
TypeScript는 미사용 export를 기본적으로 에러로 보지 않는다)

- [ ] **Step 4: Commit**

```bash
git add mobile/src/types/index.ts mobile/src/services/reportApi.ts
git commit -m "$(cat <<'EOF'
feat(mobile): 신고 API 타입/클라이언트 추가

docs/api-contract.md의 POST /reports/{targetType}/{targetId}
계약에 맞춘 타입과 fetch 래퍼를 추가한다.
EOF
)"
```

---

### Task 8: ReportReasonModal 컴포넌트

**Files:**
- Create: `mobile/src/components/ReportReasonModal.tsx`

**Interfaces:**
- Consumes: Task 7의 `ReportReason` 타입.
- Produces: `<ReportReasonModal visible isSubmitting onConfirm={(reason, reasonDetail) => void} onClose={() => void} />` — `WithdrawalReasonModal`과 동일한 props 계약.

- [ ] **Step 1: 컴포넌트 작성**

`mobile/src/components/ReportReasonModal.tsx` — `mobile/src/components/WithdrawalReasonModal.tsx`
구조를 그대로 재사용하되 사유 4개와 신고 전용 문구로 바꾼다:

```tsx
import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { ReportReason } from '../types';
import { Colors } from '../constants/theme';

const REASON_OPTIONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: '스팸/광고예요' },
  { value: 'ABUSIVE', label: '욕설·혐오 표현이에요' },
  { value: 'INAPPROPRIATE', label: '부적절한 콘텐츠예요' },
  { value: 'OTHER', label: '기타' },
];

interface ReportReasonModalProps {
  visible: boolean;
  onConfirm: (reason: ReportReason, reasonDetail: string | null) => void;
  onClose: () => void;
  isSubmitting: boolean;
}

export default function ReportReasonModal({
  visible,
  onConfirm,
  onClose,
  isSubmitting,
}: ReportReasonModalProps) {
  const [selectedReason, setSelectedReason] = useState<ReportReason | null>(null);
  const [reasonDetail, setReasonDetail] = useState('');

  const isOtherSelected = selectedReason === 'OTHER';
  const isDetailValid = !isOtherSelected || reasonDetail.trim().length > 0;
  const canSubmit = selectedReason !== null && isDetailValid && !isSubmitting;

  const handleConfirm = () => {
    if (!selectedReason || !isDetailValid) return;
    onConfirm(selectedReason, isOtherSelected ? reasonDetail.trim() : null);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>신고하기</Text>
          <Text style={styles.subtitle}>신고 사유를 선택해주세요.</Text>

          {REASON_OPTIONS.map((option) => (
            <Pressable
              key={option.value}
              style={styles.optionRow}
              onPress={() => setSelectedReason(option.value)}
            >
              <View
                style={[
                  styles.radio,
                  selectedReason === option.value && styles.radioSelected,
                ]}
              />
              <Text style={styles.optionLabel}>{option.label}</Text>
            </Pressable>
          ))}

          {isOtherSelected && (
            <TextInput
              style={styles.input}
              placeholder="사유를 입력해주세요"
              placeholderTextColor={Colors.gray400}
              value={reasonDetail}
              onChangeText={setReasonDetail}
              maxLength={200}
              multiline
            />
          )}

          <View style={styles.buttonRow}>
            <Pressable style={styles.cancelButton} onPress={onClose} disabled={isSubmitting}>
              <Text style={styles.cancelButtonText}>취소</Text>
            </Pressable>
            <Pressable
              style={[styles.confirmButton, !canSubmit && styles.confirmButtonDisabled]}
              onPress={handleConfirm}
              disabled={!canSubmit}
            >
              {isSubmitting ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.confirmButtonText}>신고하기</Text>
              )}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 20,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: Colors.gray400,
    marginBottom: 16,
  },
  optionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
  },
  radio: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: Colors.gray300,
    marginRight: 10,
  },
  radioSelected: {
    borderColor: Colors.primary,
    backgroundColor: Colors.primary,
  },
  optionLabel: {
    fontSize: 14,
    color: Colors.gray900,
  },
  input: {
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 10,
    padding: 10,
    minHeight: 60,
    marginTop: 4,
    marginBottom: 8,
    fontSize: 14,
    color: Colors.gray900,
    textAlignVertical: 'top',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 12,
  },
  cancelButton: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: Colors.gray100,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: Colors.gray900,
    fontSize: 14,
    fontWeight: '600',
  },
  confirmButton: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: Colors.danger,
    alignItems: 'center',
  },
  confirmButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  confirmButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
});
```

- [ ] **Step 2: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/components/ReportReasonModal.tsx
git commit -m "$(cat <<'EOF'
feat(mobile): 신고 사유 선택 모달 컴포넌트 추가

WithdrawalReasonModal과 동일한 구조로 신고 사유 4개(SPAM/ABUSIVE/
INAPPROPRIATE/OTHER) 선택 UI를 만든다.
EOF
)"
```

---

### Task 9: PostDetailScreen에 게시글/댓글 신고 연동

**Files:**
- Modify: `mobile/src/screens/PostDetailScreen.tsx`

**Interfaces:**
- Consumes: Task 7의 `postReport`, Task 8의 `ReportReasonModal`.

이 화면에는 케밥/컨텍스트 메뉴가 없고 `isMine` 조건의 인라인 텍스트 버튼(`commentActionsRow`,
`수정`/`삭제`) 패턴만 있다. 신고 버튼도 같은 패턴으로 — 게시글은 좋아요 버튼 옆, 댓글은
`!isMine`일 때 `commentActionsRow`에 추가한다.

- [ ] **Step 1: import와 상태 추가**

`mobile/src/screens/PostDetailScreen.tsx:28`(`import { RootStackParamList } from '../navigation/types';`) 뒤에 추가:

```tsx
import { postReport } from '../services/reportApi';
import ReportReasonModal from '../components/ReportReasonModal';
import { ReportReason, ReportTargetType } from '../types';
```

`isSavingEdit` state 선언(`L43`) 뒤에 추가:

```tsx
  const [reportTarget, setReportTarget] = useState<{ type: ReportTargetType; id: string } | null>(null);
  const [isSubmittingReport, setIsSubmittingReport] = useState(false);
```

- [ ] **Step 2: 신고 핸들러 추가**

`handleDeleteComment` 함수(`L119-137`) 뒤에 추가:

```tsx
  const handleConfirmReport = async (reason: ReportReason, reasonDetail: string | null) => {
    if (!reportTarget || !accessToken) return;
    setIsSubmittingReport(true);
    try {
      await postReport(reportTarget.type, reportTarget.id, reason, reasonDetail, accessToken);
      setReportTarget(null);
      Alert.alert('신고 접수', '신고가 접수되었습니다.');
    } catch (e: unknown) {
      Alert.alert('신고 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmittingReport(false);
    }
  };
```

- [ ] **Step 3: 게시글 신고 버튼 추가**

`ListHeaderComponent`의 `likeButton`(`L185-192`) 다음에 추가:

```tsx
              {user != null && user.id !== post.author.id && (
                <TouchableOpacity
                  style={styles.reportButton}
                  onPress={() => setReportTarget({ type: 'posts', id: post.id })}
                  activeOpacity={0.7}
                >
                  <Text style={styles.reportButtonLabel}>신고</Text>
                </TouchableOpacity>
              )}
```

- [ ] **Step 4: 댓글 신고 버튼 추가**

댓글 `renderItem`의 `!isMine` 분기가 없으므로, `isMine && (...)` 블록(`L234-243`) 뒤에 추가:

```tsx
                {!isMine && user != null && (
                  <View style={styles.commentActionsRow}>
                    <TouchableOpacity
                      onPress={() => setReportTarget({ type: 'comments', id: item.id })}
                      activeOpacity={0.7}
                    >
                      <Text style={styles.commentActionLabel}>신고</Text>
                    </TouchableOpacity>
                  </View>
                )}
```

- [ ] **Step 5: 모달 렌더링 추가**

`KeyboardAvoidingView`가 끝나는 `</KeyboardAvoidingView>`(`L272`) 다음, `</SafeAreaView>`(`L273`)
앞에 추가:

```tsx
      <ReportReasonModal
        visible={reportTarget !== null}
        onConfirm={handleConfirmReport}
        onClose={() => setReportTarget(null)}
        isSubmitting={isSubmittingReport}
      />
```

- [ ] **Step 6: 스타일 추가**

`styles` 객체(`likeCount` 정의 뒤, `L352-355`)에 추가:

```tsx
  reportButton: {
    marginTop: 8,
  },
  reportButtonLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.gray400,
  },
```

- [ ] **Step 7: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 8: 실기기/시뮬레이터 확인**

Run: `cd mobile && npx expo start` (백그라운드) 후 게시글 상세 화면에서 다른 사용자 게시글에
"신고" 버튼이 보이는지, 본인 게시글에는 안 보이는지, 신고 모달에서 사유 선택 → 제출 시
"신고가 접수되었습니다" 알림이 뜨는지 확인. 댓글도 동일하게 본인 댓글엔 "신고"가 없고 남의
댓글에만 있는지 확인.

- [ ] **Step 9: Commit**

```bash
git add mobile/src/screens/PostDetailScreen.tsx
git commit -m "$(cat <<'EOF'
feat(mobile): 게시글 상세 화면에 신고 버튼 연동

본인 게시글/댓글이 아닐 때만 신고 버튼이 보이고, 신고 사유 모달을
거쳐 POST /api/reports를 호출한다.
EOF
)"
```

---

### Task 10: CourseCommentItem + CourseDetailScreen에 코스 댓글 신고 연동

**Files:**
- Modify: `mobile/src/components/CourseCommentItem.tsx`
- Modify: `mobile/src/screens/CourseDetailScreen.tsx`

**Interfaces:**
- Consumes: Task 7의 `postReport`, Task 8의 `ReportReasonModal`.
- Produces: `CourseCommentItem`에 새 prop `onReport: (commentId: string) => void` 추가(기존 prop들 뒤).

- [ ] **Step 1: `CourseCommentItem`에 `onReport` prop 추가**

`mobile/src/components/CourseCommentItem.tsx:24`(`onCancelReply: () => void;`) 뒤에 추가:

```tsx
  onReport: (commentId: string) => void;
```

`mobile/src/components/CourseCommentItem.tsx:42`(`onCancelReply,`) 뒤에도 추가:

```tsx
  onReport,
```

- [ ] **Step 2: 신고 버튼 렌더링 추가**

`mobile/src/components/CourseCommentItem.tsx:137`(`</TouchableOpacity>` — "답글 달기" 버튼이
닫히는 줄) 다음에 추가:

```tsx
        {!isMine && (
          <TouchableOpacity onPress={() => onReport(comment.id)} activeOpacity={0.7}>
            <Text style={styles.actionText}>신고</Text>
          </TouchableOpacity>
        )}
```

- [ ] **Step 3: 재귀 호출(대댓글)에도 `onReport` 전달**

`mobile/src/components/CourseCommentItem.tsx:199`(`onCancelReply={onCancelReply}`) 뒤에 추가:

```tsx
                onReport={onReport}
```

- [ ] **Step 4: `CourseDetailScreen`에 신고 상태/핸들러 추가**

`mobile/src/screens/CourseDetailScreen.tsx:22`(`import CourseCommentItem from '../components/CourseCommentItem';`) 뒤에 추가:

```tsx
import ReportReasonModal from '../components/ReportReasonModal';
```

`mobile/src/screens/CourseDetailScreen.tsx:32`(`} from '../services/courseCommentApi';`) 뒤에 추가:

```tsx
import { postReport } from '../services/reportApi';
```

`mobile/src/screens/CourseDetailScreen.tsx:38`을 다음으로 교체(`ReportReason` 타입 추가):

```tsx
import { Course, CourseComment, ReportReason } from '../types';
```

`mobile/src/screens/CourseDetailScreen.tsx:63`(`const [loadingReplyIds, setLoadingReplyIds] = useState<Record<string, boolean>>({});`) 뒤에 추가:

```tsx
  const [reportCommentId, setReportCommentId] = useState<string | null>(null);
  const [isSubmittingReport, setIsSubmittingReport] = useState(false);
```

`mobile/src/screens/CourseDetailScreen.tsx:355`(`handleDeleteComment`의 닫는 `};`) 뒤에 추가:

```tsx

  const handleReportComment = (commentId: string) => {
    setReportCommentId(commentId);
  };

  const handleConfirmReport = async (reason: ReportReason, reasonDetail: string | null) => {
    if (!reportCommentId || !accessToken) return;
    setIsSubmittingReport(true);
    try {
      await postReport('course-comments', reportCommentId, reason, reasonDetail, accessToken);
      setReportCommentId(null);
      Alert.alert('신고 접수', '신고가 접수되었습니다.');
    } catch (e: unknown) {
      Alert.alert('신고 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmittingReport(false);
    }
  };
```

`mobile/src/screens/CourseDetailScreen.tsx:474`(`onDelete={handleDeleteComment}`) 다음 줄에 추가:

```tsx
                  onReport={handleReportComment}
```

`mobile/src/screens/CourseDetailScreen.tsx:520`(`</KeyboardAvoidingView>`)과 `:521`
(`</SafeAreaView>`) 사이에 추가 — `PostDetailScreen.tsx`와 동일하게 `KeyboardAvoidingView`
바로 다음, `SafeAreaView`가 닫히기 직전에 넣는다:

```tsx
      <ReportReasonModal
        visible={reportCommentId !== null}
        onConfirm={handleConfirmReport}
        onClose={() => setReportCommentId(null)}
        isSubmitting={isSubmittingReport}
      />
```

- [ ] **Step 5: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음. `CourseCommentItem`을 호출하는 다른 곳이 있다면 `onReport` prop 누락으로
에러가 날 수 있다 — 이 컴포넌트를 쓰는 곳이 `CourseDetailScreen` 하나뿐인지 먼저 확인한다.

Run: `cd mobile && grep -rn "CourseCommentItem" src/`

- [ ] **Step 6: 실기기/시뮬레이터 확인**

코스 상세 화면에서 본인 댓글엔 "신고"가 없고 남의 댓글/대댓글에만 있는지, 신고 모달 제출이
정상 동작하는지 확인.

- [ ] **Step 7: Commit**

```bash
git add mobile/src/components/CourseCommentItem.tsx mobile/src/screens/CourseDetailScreen.tsx
git commit -m "$(cat <<'EOF'
feat(mobile): 코스 댓글에 신고 버튼 연동

CourseCommentItem에 onReport prop을 추가해 본인 댓글/대댓글이
아닐 때만 신고 버튼을 보여주고, CourseDetailScreen에서 신고 모달과
POST /api/reports/course-comments 호출을 연결한다.
EOF
)"
```

---

### Task 11: 구현 기록 작성 + 최종 검증

**Files:**
- Create: `mobile/docs/implementations/content-report.md`

- [ ] **Step 1: 구현 기록 작성**

`mobile/docs/implementations/content-report.md` — 기존 `mobile/docs/implementations/tag-search.md`
형식을 따른다:

```markdown
# 콘텐츠 신고 기능

## 개요

게시글/댓글/코스댓글을 신고할 수 있는 기능을 추가했습니다. Apple App Review Guideline 2.1
재제출 요청에서 UGC 신고/차단 메커니즘을 언급했고, Guideline 1.2 대응을 위해 최소 범위로
구현했습니다.

## 구현 일자

2026-08-23

## 변경 파일

### Backend
- `backend/.../community/Report.java`, `ReportRepository.java`, `ReportService.java`,
  `ReportController.java` — 신고 생성 API
- `backend/.../community/{PostService,CommentService,CourseCommentService}.java` —
  `deleteAsAdmin` 추가
- `backend/.../admin/AdminReport{View,QueryService,ActionService,Controller}.java` —
  관리자 신고 조회/처리
- `backend/src/main/resources/templates/admin/reports.html` — 관리자 신고 화면
- `docs/api-contract.md`, `docs/data-model.md`, `docs/admin-dashboard.md` — 계약 문서

### Mobile
- `mobile/src/services/reportApi.ts` — 신고 API 클라이언트
- `mobile/src/components/ReportReasonModal.tsx` — 신고 사유 선택 모달
- `mobile/src/screens/PostDetailScreen.tsx` — 게시글/댓글 신고 버튼
- `mobile/src/components/CourseCommentItem.tsx`, `mobile/src/screens/CourseDetailScreen.tsx` —
  코스 댓글 신고 버튼

## 설계 문서

`docs/superpowers/specs/2026-08-23-content-report-design.md`
`docs/superpowers/plans/2026-08-23-content-report.md`

## 사용한 스킬

superpowers:brainstorming → superpowers:writing-plans →
superpowers:subagent-driven-development(또는 executing-plans)
```

- [ ] **Step 2: 최종 검증**

Run: `cd backend && ./gradlew test`
Expected: PASS (전체 스위트)

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

Run: `cd mobile && npx expo start` (백그라운드) 후 `curl "http://localhost:8081/index.bundle?platform=ios&dev=true"`로 HTTP 200 확인.

- [ ] **Step 3: Commit**

```bash
git add mobile/docs/implementations/content-report.md
git commit -m "$(cat <<'EOF'
docs(mobile): 콘텐츠 신고 기능 구현 기록 작성
EOF
)"
```

- [ ] **Step 4: PR 생성 여부 확인**

여기까지 전체 스위트가 통과하면 사용자에게 PR 생성 여부를 확인한다(`superpowers:finishing-a-development-branch` 참고) — 이 계획 문서는 PR을 자동으로 만들지 않는다.
