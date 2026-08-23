# 콘텐츠 신고 기능 설계

작성일: 2026-08-23
관련 문서: `docs/api-contract.md` §Like APIs, `docs/data-model.md` §Post/§Comment/§CourseComment,
`docs/admin-dashboard.md`, `docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`,
`docs/superpowers/specs/2026-07-18-account-withdrawal-design.md`(사유 enum + 관리자 화면 패턴 재사용)

## 배경

Apple App Review가 Guideline 2.1(Information Needed) 대응을 위해 "User-generated content,
including content reporting and blocking mechanisms"를 화면 녹화에 포함하라고 요구했다. Runvas는
게시글·댓글·코스댓글이라는 UGC를 핵심 기능으로 제공하지만([product-scope.md](../../product-scope.md)
사용자 흐름 7~8번), 현재 콘텐츠를 신고하거나 부적절한 콘텐츠에 대응할 수단이 전혀 없다
(Guideline 1.2 User Generated Content 요구사항 미충족 상태). 이 설계는 재제출 전 최소 범위로
"신고 생성 + 관리자 삭제/기각" 흐름을 정의한다.

## 정책 결정 (브레인스토밍 확정 사항)

- **신고 대상**: `Post`, `Comment`(게시글 댓글), `CourseComment`(코스 댓글) 세 종류만. `Course`
  자체(제목/설명)는 이번 범위에서 제외한다.
- **처리 수단**: 관리자 대시보드에 쓰기 액션을 처음으로 추가한다 — 신고 목록을 보고 그 자리에서
  "삭제"(콘텐츠 하드 삭제 + 신고 `RESOLVED`) 또는 "기각"(콘텐츠는 그대로, 신고만 `DISMISSED`)을
  누를 수 있다. `docs/admin-dashboard.md`의 "모든 화면은 조회 전용" 원칙을 신고 처리에 한해 깬다.
- **신고 사유**: `WithdrawalReason`과 동일한 패턴 — `SPAM`/`ABUSIVE`/`INAPPROPRIATE`/`OTHER`
  enum, `OTHER`일 때만 `reasonDetail`(1-200자) 필수.
- **중복 신고**: 같은 사용자가 같은 대상에 이미 `PENDING` 신고를 넣었으면 새로 만들지 않고 기존
  것을 그대로 반환한다(`PUT /likes`와 동일한 관례). `RESOLVED`/`DISMISSED`로 끝난 신고 이후 재신고는
  새로 생성 가능(콘텐츠가 수정되어 다시 문제될 수 있으므로).
- **삭제 처리**: 새 삭제 로직을 만들지 않고 기존 `DELETE /posts/{id}` /
  `DELETE /comments/{id}` / `DELETE /courses/{courseId}/comments/{id}`와 동일한 하드 삭제를
  관리자 권한으로 재사용한다. `CourseComment` 삭제는 기존 규칙대로 하위 대댓글도 함께 삭제된다.
- **자기 신고 제한 없음**: 본인이 쓴 글/댓글도 신고 가능(막을 이유 없음, 검증 로직 최소화).
- **알림 없음**: 신고자에게 처리 결과를 알리지 않는다(MVP가 푸시 알림 자체를 제외 범위로 둠).

## 데이터 모델 변경

### `docs/data-model.md`에 추가할 `Report` 섹션

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

### `reports` 테이블 (신규, `V15__create_reports.sql`)

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

부분 유니크 인덱스(`WHERE status = 'PENDING'`)로 "같은 대상에 PENDING 신고는 사용자당 1건" 제약을
DB 레벨에서 보장한다 — `RESOLVED`/`DISMISSED` 이후 재신고는 새 행으로 허용된다.

## API 계약 변경 (모바일 ↔ 백엔드)

`docs/api-contract.md`에 `## Like APIs` 다음에 `## Report APIs` 섹션을 추가한다.

### POST /reports/{targetType}/{targetId}

게시글, 댓글, 코스 댓글을 신고한다.

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

## 백엔드 구현

### `ReportTargetType` / `ReportReason` / `ReportStatus` (신규 enum, `com.runvas.backend.community`)

```java
public enum ReportTargetType { POST, COMMENT, COURSE_COMMENT }
public enum ReportReason { SPAM, ABUSIVE, INAPPROPRIATE, OTHER }
public enum ReportStatus { PENDING, RESOLVED, DISMISSED }
```

`LikeTargetType`과 마찬가지로 URL 경로값(`posts`/`comments`/`course-comments`)과 enum 사이의
변환은 서비스 계층에서 수행한다(`LikeService.parseTargetType`과 동일 패턴).

### `Report` (신규 엔티티, `com.runvas.backend.community`)

일반 `@Id` 엔티티(단일 PK `id`, UUID 문자열)로 만든다. `Like`처럼 복합키(`@EmbeddedId`)를 쓰지
않는 이유는 `status`/`resolvedAt`처럼 신고 1건 단위로 갱신되는 가변 필드가 있어서 엔티티 자체가
식별자 이상의 상태를 가져야 하기 때문이다.

```java
@Entity
@Table(name = "reports")
public class Report {
    @Id
    private String id; // UUID.randomUUID().toString()
    private String reporterId;
    @Enumerated(EnumType.STRING) private ReportTargetType targetType;
    private String targetId;
    @Enumerated(EnumType.STRING) private ReportReason reason;
    private String reasonDetail;
    @Enumerated(EnumType.STRING) private ReportStatus status = ReportStatus.PENDING;
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;

    public void resolve() { this.status = ReportStatus.RESOLVED; this.resolvedAt = Instant.now(); }
    public void dismiss() { this.status = ReportStatus.DISMISSED; this.resolvedAt = Instant.now(); }
}
```

### `ReportRepository` (신규)

```java
public interface ReportRepository extends JpaRepository<Report, String> {
    Optional<Report> findByReporterIdAndTargetTypeAndTargetIdAndStatus(
            String reporterId, ReportTargetType targetType, String targetId, ReportStatus status);
    List<Report> findAllByTargetTypeAndTargetIdAndStatus(
            ReportTargetType targetType, String targetId, ReportStatus status);
    Page<Report> findAllByStatus(ReportStatus status, Pageable pageable);
}
```

### `ReportService` (신규, 모바일 API용)

```java
@Transactional
public ReportResponse report(String targetTypePathValue, String targetId, ReportReason reason, String reasonDetail) {
    validateReasonDetail(reason, reasonDetail);
    ReportTargetType targetType = parseTargetType(targetTypePathValue); // "posts"→POST 등
    String reporterId = currentUserProvider.requireUserId();
    requireTargetExists(targetType, targetId);

    Optional<Report> existing = reportRepository
            .findByReporterIdAndTargetTypeAndTargetIdAndStatus(reporterId, targetType, targetId, ReportStatus.PENDING);
    if (existing.isPresent()) {
        return ReportResponse.from(targetTypePathValue, existing.get(), false);
    }

    Report saved = reportRepository.save(new Report(reporterId, targetType, targetId, reason, reasonDetail));
    return ReportResponse.from(targetTypePathValue, saved, true);
}
```

`requireTargetExists`는 `LikeService`의 동명 메서드와 동일하게 `switch`로 `PostRepository`/
`CommentRepository`/`CourseCommentRepository`의 `existsById`를 호출한다. 컨트롤러가
`ReportResponse.from`의 `isNew` 플래그를 보고 `201`/`200`을 결정한다.

### 관리자 처리 — `AdminReportQueryService` / `AdminReportActionService` (신규, `com.runvas.backend.admin`)

- `AdminReportQueryService.search(status, targetType, page, size)`: `AdminPostQueryService`와
  동일한 얇은 위임 패턴.
- `AdminReportActionService`:
  - `resolve(reportId)`: 신고를 조회 → `targetType`에 따라 `PostService`/`CommentService`/
    `CourseCommentService`의 `deleteAsAdmin(targetId)`(아래 신규 메서드)를 호출(대상이 이미
    없으면 조용히 건너뜀, 예외를 던지지 않음) → 같은 `(targetType, targetId)`에 걸린 다른
    `PENDING` 신고도 함께 `RESOLVED` 처리(`findAllByTargetTypeAndTargetIdAndStatus` 활용) →
    대상 신고 `resolve()`.
  - `dismiss(reportId)`: 신고 하나만 `dismiss()`.

기존 `PostService.delete`/`CommentService.delete`/`CourseCommentService.delete`는 모두
`requireAuthor(...)`로 작성자 본인만 삭제 가능하도록 강제하고 있어(예:
`PostService.java:112`, `CommentService.java:72`, `CourseCommentService.java:115`), 관리자
경로에는 그대로 쓸 수 없다. 세 서비스 각각에 `requireAuthor`를 건너뛰는 신규 메서드를 추가한다.

```java
// PostService — 기존 delete()의 findPostOrThrow 대신 findById로 완화, requireAuthor 생략
public void deleteAsAdmin(String postId) {
    postRepository.findById(postId).ifPresent(postRepository::delete);
}

// CommentService — 기존 delete()와 동일한 side effect(게시글 댓글 수 감소)는 유지
public void deleteAsAdmin(String commentId) {
    commentRepository.findById(commentId).ifPresent(comment -> {
        commentRepository.delete(comment);
        postRepository.findById(comment.getPostId()).ifPresent(Post::decrementCommentCount);
    });
}

// CourseCommentService — 기존 delete()의 courseId 일치 검증은 admin 경로에서는 불필요
// (신고 대상 targetId만으로 조회하며, 하위 대댓글은 course_comments.parent_comment_id의
//  ON DELETE CASCADE(V9 마이그레이션)로 DB가 자동 처리한다)
public void deleteAsAdmin(String commentId) {
    courseCommentRepository.findById(commentId).ifPresent(courseCommentRepository::delete);
}
```

### `AdminReportController` (신규, `com.runvas.backend.admin`)

`AdminPostController`와 동일한 `@Controller` + Thymeleaf 반환 패턴.

```java
@GetMapping("/admin/reports")
String reports(@RequestParam(defaultValue = "PENDING") ReportStatus status,
               @RequestParam(required = false) ReportTargetType targetType,
               @RequestParam(defaultValue = "0") int page, Model model) { ... }

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
```

`AdminSecurityConfig`가 이미 `/admin/**`를 세션 인증으로 보호하고 있으므로 추가 보안 설정은
필요 없다.

### `admin/reports.html` (신규 템플릿)

`admin/posts.html` 구조를 참고. 필터(상태/타입 드롭다운), 목록 테이블(대상 미리보기, 신고자
닉네임, 사유, 상세사유, 신고 시각, 처리 버튼), 페이지네이션을 포함한다. 대상 콘텐츠가 이미
삭제된 경우 미리보기 칸에 "(삭제된 콘텐츠)"를 표시한다(조회 시 `existsById`로 확인).
`admin/fragments/nav.html`에 "신고" 메뉴 항목을 추가한다.

### `docs/admin-dashboard.md` 수정

화면 목록 표에 `/admin/reports` 행을 추가하고, "명시적 제외 범위"의 "모든 화면은 조회
전용입니다" 문장을 다음으로 좁혀서 고친다.

> 신고 처리(콘텐츠 삭제/기각)를 제외한 나머지 화면(회원/코스/게시글 목록)은 여전히 조회
> 전용입니다. 회원 정지 기능은 없습니다.

## 모바일 구현

### `src/types/index.ts`

```ts
export type ReportReason = 'SPAM' | 'ABUSIVE' | 'INAPPROPRIATE' | 'OTHER';
export type ReportTargetType = 'posts' | 'comments' | 'course-comments';
```

### `src/services/reportApi.ts` (신규)

```ts
export async function reportContent(
  targetType: ReportTargetType,
  targetId: string,
  reason: ReportReason,
  reasonDetail: string | null,
  accessToken: string,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/reports/${targetType}/${targetId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({ reason, reasonDetail }),
  });
  if (response.status !== 201 && response.status !== 200) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
```

### `ReportReasonModal` (신규 컴포넌트, `src/components/`)

`WithdrawalReasonModal.tsx` 구조를 그대로 재사용 — 사유 4개 라디오 목록, `OTHER` 선택 시
상세 텍스트(1-200자) 입력란, 제출 버튼.

### 신고 버튼 노출 위치

- 게시글 상세 화면(케밥 메뉴): "신고" → `targetType: 'posts'`
- 게시글 댓글 각 행(케밥 메뉴): "신고" → `targetType: 'comments'`
- 코스 상세 화면의 코스 댓글 각 행(케밥 메뉴): "신고" → `targetType: 'course-comments'`

신고 성공 시 토스트로 "신고가 접수되었습니다" 안내만 하고, 별도 로컬 상태(비활성화 등)는 두지
않는다 — 재신고를 눌러도 서버가 idempotent하게 처리한다.

## 에러 처리 / 엣지 케이스

| 상황 | 처리 |
| --- | --- |
| `reason`이 `OTHER`인데 `reasonDetail` 미전송 | `400 VALIDATION_ERROR` |
| 이미 대상에 `PENDING` 신고가 있는 상태에서 재신고 | 새로 만들지 않고 기존 신고를 `200 OK`로 반환 |
| `RESOLVED`/`DISMISSED` 이후 같은 대상 재신고 | 새 `PENDING` 신고 생성, `201 Created` |
| 존재하지 않거나 이미 삭제된 콘텐츠 신고 | `404 NOT_FOUND` |
| 관리자가 이미 콘텐츠가 삭제된 신고에 "삭제" 처리 | 에러 없이 신고만 `RESOLVED` |
| 같은 콘텐츠에 여러 명이 신고 후 관리자가 "삭제" 처리 | 그 대상의 다른 `PENDING` 신고들도 함께 `RESOLVED` |
| 본인 글/댓글 자기 신고 | 제한 없이 정상 처리 |
| 비로그인 사용자의 신고 요청 | `401 UNAUTHORIZED` |

## 범위 제외

- 사용자 차단(block) 기능
- 신고 누적 시 자동 숨김/자동 삭제 — 전부 관리자 수동 판단
- 신고자에게 처리 결과 알림
- 콘텐츠가 삭제된 작성자에 대한 이의제기/복구 절차
- 신고 남용 방지(rate limit)
- `Course` 자체 신고

## 테스트

- **백엔드**:
  - `ReportServiceTest`(신규): 정상 신고 생성(`201`에 해당하는 `isNew=true`), 동일 대상 중복
    신고 시 기존 `PENDING` 재사용(`isNew=false`), `OTHER` + `reasonDetail` 누락 시 검증 예외,
    존재하지 않는 대상 신고 시 `NOT_FOUND`.
  - `AdminReportActionServiceTest`(신규): `resolve` 호출 시 대상 콘텐츠 하드 삭제 + 같은 대상의
    다른 `PENDING` 신고까지 `RESOLVED` 처리, `dismiss` 호출 시 콘텐츠는 그대로 두고 신고만
    `DISMISSED`, 이미 삭제된 대상에 `resolve` 호출해도 예외 없이 신고만 `RESOLVED`.
  - `ReportControllerTest`: `POST /api/reports/{targetType}/{targetId}` 인증 필요, 정상 케이스
    `201`/`200` 구분.
- **모바일**: jest 미설정 상태라 자동 테스트는 추가하지 않음. `npx tsc --noEmit` 통과 + 실기기/
  시뮬레이터에서 게시글/댓글/코스댓글 각각 신고 → 관리자 대시보드에서 신고 확인 → 삭제 처리 →
  모바일에서 해당 콘텐츠가 사라졌는지 확인(`mobile/CLAUDE.md` 검증 규칙).

## 검증 기준

- 로그인한 사용자가 게시글/댓글/코스댓글을 신고하면 `201`과 함께 `reports` 테이블에 `PENDING`
  행이 생긴다.
- 같은 사용자가 같은 대상을 다시 신고하면 새 행이 생기지 않고 `200`이 반환된다.
- `/admin/reports`에서 `PENDING` 신고 목록이 보이고, "삭제"를 누르면 원본 콘텐츠가 실제로
  사라지며(모바일에서 재조회 시 `404`) 신고 상태가 `RESOLVED`로 바뀐다.
- "기각"을 누르면 원본 콘텐츠는 그대로 남아 있고 신고 상태만 `DISMISSED`로 바뀐다.
- `docs/api-contract.md`의 `POST /reports/{targetType}/{targetId}` 예시와 실제 구현 동작이
  일치한다.
