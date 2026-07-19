# 회원 탈퇴 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `DELETE /api/me`로 회원 탈퇴를 신청하면 30일 소프트 삭제 유예기간을 두고, 그 안에 재로그인하면
자동 복구되며, 유예기간이 지나면 배치가 계정을 하드 삭제하되 사용자가 쓴 코스·게시글·댓글은 남기고
작성자만 "탈퇴한 사용자"로 표시한다.

**Architecture:** `users.deleted_at` 컬럼으로 소프트 삭제 상태를 표현한다. 탈퇴 신청 시점에는 사유를
익명 테이블에 기록하고 현재 세션 토큰만 블랙리스트 처리할 뿐, 콘텐츠·좋아요·북마크·카카오 연동은
건드리지 않는다. 매일 도는 `@Scheduled` 배치가 30일 지난 소프트 삭제 계정을 찾아 카카오 unlink(best
-effort) → 좋아요/북마크 삭제 → 사용자 행 하드 삭제 순서로 처리한다. 카카오 로그인 시 기존 사용자가
소프트 삭제 상태면 자동으로 복구한다. 작성자 조회가 실패하는 시점(하드 삭제 후)에 대비해 커뮤니티
응답 3곳에 "탈퇴한 사용자" placeholder를 추가한다.

**Tech Stack:** Spring Boot (Java 21) + Spring Data JPA + Flyway + Spring Security + Redis
(`TokenBlacklistService` 재사용) / React Native + Expo (`expo-secure-store`)

## Global Constraints

- 설계 스펙: `docs/superpowers/specs/2026-07-18-account-withdrawal-design.md` (사용자 승인됨). 이
  문서와 충돌하는 구현은 하지 않는다.
- `main`에 직접 커밋/푸시 금지. 반드시 기능 브랜치에서 작업한다 (`superpowers:using-git-worktrees`로
  격리된 워크스페이스 생성 — 저장소 루트가 다른 작업에 쓰이고 있을 수 있으므로 `git worktree list`로
  먼저 확인).
- 새 워크트리에서는 작업 시작 전 `sh scripts/setup-git-hooks.sh`를 실행해 커밋 메시지 훅을 켠다.
- 커밋 메시지는 Conventional Commits 형식 (`docs: ...`, `feat(user): ...`, `feat(mobile): ...`,
  `test(user): ...`). 커밋 메시지에 `Co-Authored-By`, `codex`, `claude` 같은 도구/저작자 표시를
  넣지 않는다 — CI가 이를 실패시킨다.
- 커밋에는 의도한 파일만 스테이징한다 (`git add <path>`, `git add -A`/`git add .` 금지).
- API/데이터모델이 바뀌면 구현보다 `docs/` 변경을 먼저 커밋한다 (Task 1이 이를 담당).
- 유예기간은 정확히 30일. 탈퇴 신청 시점에는 콘텐츠·좋아요·북마크·카카오 연동을 절대 건드리지 않는다
  (복구 가능성 보존).
- 탈퇴 사유(`withdrawal_feedback`)는 사용자 식별자를 전혀 포함하지 않는다 — `user_id`/`authorId`
  컬럼을 추가하지 않는다.
- 카카오 unlink는 유예기간이 끝나 하드 삭제될 때만 호출한다. 실패해도 하드 삭제는 그대로 진행한다.
- 작성자 조회 실패 시 표시 문구는 정확히 `"탈퇴한 사용자"` (다른 문구로 바꾸지 않는다).
- 백엔드 인증 요청은 `Authorization: Bearer <accessToken>` 헤더를 사용한다 (기존 컨벤션 유지).
- `providerUserId`는 API 응답에 노출하지 않는다 (기존 규칙, 이번 변경과 무관하게 유지 확인).

---

## File Structure

**문서 (`docs/`)**
- `docs/api-contract.md` — `DELETE /me` 엔드포인트 추가
- `docs/data-model.md` — `User.deletedAt`(내부 전용), `WithdrawalReason`, 탈퇴한 사용자 표시 정책 추가

**백엔드 (`backend/`)**
- `src/main/resources/db/migration/V12__add_deleted_at_to_users.sql` — 신규
- `src/main/resources/db/migration/V13__create_withdrawal_feedback.sql` — 신규
- `src/main/java/com/runvas/user/domain/User.java` — `deletedAt`/`isDeleted`/`markWithdrawn`/`restore` 추가
- `src/main/java/com/runvas/user/domain/WithdrawalReason.java` — 신규 열거형
- `src/main/java/com/runvas/user/domain/WithdrawalFeedback.java` — 신규 엔티티
- `src/main/java/com/runvas/user/repository/UserRepository.java` — `findByDeletedAtLessThanEqual` 추가
- `src/main/java/com/runvas/user/repository/WithdrawalFeedbackRepository.java` — 신규
- `src/main/java/com/runvas/user/dto/WithdrawRequest.java` — 신규
- `src/main/java/com/runvas/user/service/AccountWithdrawalService.java` — 신규
- `src/main/java/com/runvas/user/controller/MeController.java` — `DELETE /me` 추가
- `src/main/java/com/runvas/auth/service/KakaoAuthService.java` — 로그인 시 자동 복구
- `src/main/java/com/runvas/user/dto/PublicProfileResponse.java` — `withdrawn()` 추가
- `src/main/java/com/runvas/backend/community/dto/PublicProfile.java` — `withdrawn()` 추가
- `src/main/java/com/runvas/backend/community/PostService.java` — 작성자 조회 fallback
- `src/main/java/com/runvas/backend/community/CommentService.java` — 작성자 조회 fallback
- `src/main/java/com/runvas/backend/community/CourseCommentService.java` — 작성자 조회 fallback
- `src/main/resources/application.yml` — `runvas.kakao.admin-key`, `runvas.kakao.unlink-uri` 추가
- `src/main/java/com/runvas/auth/service/KakaoUnlinkClient.java` — 신규 인터페이스
- `src/main/java/com/runvas/auth/service/KakaoHttpUnlinkClient.java` — 신규 구현체
- `src/main/java/com/runvas/backend/community/LikeRepository.java` — `deleteAllByIdUserId` 추가
- `src/main/java/com/runvas/backend/community/BookmarkRepository.java` — `deleteAllByIdUserId` 추가
- `src/main/java/com/runvas/user/service/AccountPurgeService.java` — 신규
- `src/main/java/com/runvas/user/service/AccountPurgeScheduler.java` — 신규
- `src/main/java/com/runvas/RunvasApplication.java` — `@EnableScheduling` 추가

**모바일 (`mobile/`)**
- `src/types/index.ts` — `WithdrawalReason` 타입 추가
- `src/services/authApi.ts` — `deleteMe` 추가
- `src/contexts/AuthContext.tsx` — `withdraw()` 추가
- `src/components/WithdrawalReasonModal.tsx` — 신규
- `src/screens/ProfileScreen.tsx` — 탈퇴 버튼 + 모달 연결
- `mobile/docs/implementations/account-withdrawal.md` — 신규, 구현 기록

---

### Task 1: `docs/api-contract.md` + `docs/data-model.md` 갱신 (docs-first)

**Files:**
- Modify: `docs/api-contract.md` (Auth APIs 섹션, `### PATCH /me`와 `## Post APIs` 사이에 삽입)
- Modify: `docs/data-model.md` (User 섹션, `## Post` 앞에 삽입)

**Interfaces:**
- Produces: `DELETE /api/me` 계약 — `Required` auth, body `{ reason, reasonDetail }`, `204 No
  Content`, 에러 `400 VALIDATION_ERROR`/`401 UNAUTHORIZED`. 이후 모든 백엔드/모바일 작업이 이
  계약을 구현한다. `WithdrawalReason` 5개 값과 "탈퇴한 사용자" 표시 정책도 이 문서가 기준이 된다.

- [ ] **Step 1: `docs/api-contract.md`의 `### PATCH /me` 섹션과 `## Post APIs` 섹션 사이에 다음 내용을 삽입한다.**

```markdown
### DELETE /me

회원 탈퇴를 신청합니다. 계정을 즉시 삭제하지 않고 30일 유예기간을 둡니다. 유예기간 중 같은 카카오
계정으로 다시 로그인하면 자동으로 복구됩니다. 유예기간이 지나면 계정은 하드 삭제되고, 이 사용자가
작성한 코스·게시글·댓글은 삭제되지 않고 작성자 표시만 `"탈퇴한 사용자"`로 바뀝니다
(`profileImageUrl`/`bio`는 `null`).

#### Auth

`Required`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | Y | `NOT_USING`, `MISSING_FEATURES`, `BUGS_OR_ERRORS`, `PRIVACY_CONCERN`, `OTHER` 중 하나 |
| `reasonDetail` | string \| null | N | `reason`이 `OTHER`일 때만 필수 (1-200자) |

```json
{
  "reason": "MISSING_FEATURES",
  "reasonDetail": null
}
```

#### Response: 204 No Content

응답 본문이 없습니다. 요청에 사용된 `accessToken`은 `POST /auth/logout`과 동일하게 즉시
블랙리스트 처리됩니다 (다른 기기의 세션은 유예기간 동안 계속 유효합니다).

이미 탈퇴 신청되어 유예기간 중인 계정이 다시 이 API를 호출하면 사유를 다시 기록하거나 유예기간을
연장하지 않고 `204`만 반환합니다 (멱등 처리).

#### Errors

- `400 VALIDATION_ERROR`: `reason` 누락/미지원 값, `reason`이 `OTHER`인데 `reasonDetail` 누락
- `401 UNAUTHORIZED`: 로그인하지 않음

```

- [ ] **Step 2: `docs/data-model.md`의 `## User` 섹션 필드 표 마지막 행(`updatedAt`) 다음에 `deletedAt` 행을 추가한다.**

`updatedAt` 행은 `Post`/`Comment`/`CourseComment` 표에도 똑같이 등장하므로, User 표에만 있는
`runningPaceSecPerKm` 행부터 이어지는 3줄을 통째로 찾아서 교체해 위치를 명확히 한다.
`docs/data-model.md`에서 다음 표 행 3줄:

```markdown
| `runningPaceSecPerKm` | number | N | 달리기 페이스 (초/km). 기본값 360 (6:00/km). 본인에게만 노출 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |
| `updatedAt` | string | Y | ISO 8601 수정 시각 |
```

을 (User 섹션의 것) 다음으로 교체한다:

```markdown
| `runningPaceSecPerKm` | number | N | 달리기 페이스 (초/km). 기본값 360 (6:00/km). 본인에게만 노출 |
| `createdAt` | string | Y | ISO 8601 생성 시각 |
| `updatedAt` | string | Y | ISO 8601 수정 시각 |
| `deletedAt` | string \| null | N | 탈퇴 신청 시각(ISO 8601). 채워져 있으면 30일 유예기간 중이라는 뜻. API 응답에는 노출하지 않는 내부 저장값 |
```

- [ ] **Step 3: `docs/data-model.md`의 `## User` 섹션과 `## PublicProfile` 섹션 사이에 다음 섹션을 추가한다.**

```markdown
## WithdrawalReason

회원 탈퇴 신청 시 선택하는 사유. `DELETE /me` 요청에서만 쓰이며, 사용자 식별자와 연결되지 않는
별도 익명 통계로만 기록된다 (API 응답에는 등장하지 않음).

| 값 | 설명 |
| --- | --- |
| `NOT_USING` | 자주 사용하지 않아요 |
| `MISSING_FEATURES` | 원하는 코스·기능이 없어요 |
| `BUGS_OR_ERRORS` | 오류·버그가 많아요 |
| `PRIVACY_CONCERN` | 개인정보가 걱정돼요 |
| `OTHER` | 기타 (이 경우 `reasonDetail` 1-200자 필수) |

## 탈퇴한 사용자 표시

계정이 하드 삭제된 뒤에도 그 사용자가 작성한 `Course`/`Post`/`Comment`/`CourseComment`는 삭제되지
않는다. 이 콘텐츠의 작성자를 나타내는 `PublicProfile`은 다음처럼 고정된 값으로 채워진다.

| 필드 | 값 |
| --- | --- |
| `id` | 원래 `authorId` 그대로 유지 |
| `nickname` | 고정 문구 `"탈퇴한 사용자"` |
| `profileImageUrl` | `null` |
| `bio` | `null` |
```

- [ ] **Step 4: 삽입 위치와 마크다운 렌더링을 확인한다.**

Run: `grep -n "### DELETE /me\|### PATCH /me\|## Post APIs" docs/api-contract.md`
Expected: `### PATCH /me`, `### DELETE /me`, `## Post APIs` 순서로 출력됨.

Run: `grep -n "## WithdrawalReason\|## 탈퇴한 사용자 표시\|## PublicProfile\|## User$" docs/data-model.md`
Expected: `## User`, `## WithdrawalReason`, `## 탈퇴한 사용자 표시`, `## PublicProfile` 순서로 출력됨.

- [ ] **Step 5: Commit**

```bash
git add docs/api-contract.md docs/data-model.md
git commit -m "docs: 회원 탈퇴 API 계약과 탈퇴한 사용자 표시 정책 추가"
```

---

### Task 2: 소프트 삭제 컬럼 + 익명 탈퇴 사유 테이블 마이그레이션

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__add_deleted_at_to_users.sql`
- Create: `backend/src/main/resources/db/migration/V13__create_withdrawal_feedback.sql`

**Interfaces:**
- Produces: `users.deleted_at` 컬럼, `withdrawal_feedback` 테이블 — Task 3(`User` 엔티티), Task 4
  (`WithdrawalFeedback` 엔티티)가 이 스키마를 그대로 매핑한다.

- [ ] **Step 1: `backend/src/main/resources/db/migration/V12__add_deleted_at_to_users.sql` 신규 생성**

```sql
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
```

- [ ] **Step 2: `backend/src/main/resources/db/migration/V13__create_withdrawal_feedback.sql` 신규 생성**

```sql
CREATE TABLE withdrawal_feedback (
    id VARCHAR(36) PRIMARY KEY,
    reason_code VARCHAR(30) NOT NULL,
    reason_detail VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 3: 마이그레이션이 깨지지 않는지 컴파일/부팅으로 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL` (Flyway는 애플리케이션 컨텍스트가 뜨는 다음 태스크의 테스트에서 실제로
적용된다 — 이 단계에서는 SQL 문법 오류만 있어도 이후 모든 `@SpringBootTest`가 깨지므로, Task 3의
첫 테스트 실행에서 결과적으로 검증된다).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__add_deleted_at_to_users.sql backend/src/main/resources/db/migration/V13__create_withdrawal_feedback.sql
git commit -m "feat(user): 소프트 삭제 컬럼과 익명 탈퇴 사유 테이블 마이그레이션 추가"
```

---

### Task 3: `User` 엔티티에 소프트 삭제 상태 추가

**Files:**
- Modify: `backend/src/main/java/com/runvas/user/domain/User.java`
- Create: `backend/src/test/java/com/runvas/user/domain/UserTest.java`

**Interfaces:**
- Consumes: `V12__add_deleted_at_to_users.sql`의 `deleted_at` 컬럼 (Task 2)
- Produces: `User.isDeleted(): boolean`, `User.markWithdrawn(): void`, `User.restore(): void`,
  `User.getDeletedAt(): Instant` — Task 5(`AccountWithdrawalService`), Task 6(`KakaoAuthService`),
  Task 9(`AccountPurgeService`)가 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/user/domain/UserTest.java` 신규 생성:

```java
package com.runvas.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void newUserIsNotDeleted() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);

        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void markWithdrawnSetsDeletedAtToNow() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);

        user.markWithdrawn();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void restoreClearsDeletedAt() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);
        user.markWithdrawn();

        user.restore();

        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: FAIL — `cannot find symbol: method isDeleted`

- [ ] **Step 3: `User`에 필드/메서드 추가**

`backend/src/main/java/com/runvas/user/domain/User.java`에서 `updatedAt` 필드 선언 바로 뒤에 추가:

```java
    @Column
    private Instant deletedAt;
```

`getUpdatedAt()` 게터 뒤에 추가:

```java
    public Instant getDeletedAt() { return deletedAt; }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markWithdrawn() {
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: PASS (3개 테스트)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/user/domain/User.java backend/src/test/java/com/runvas/user/domain/UserTest.java
git commit -m "feat(user): User에 소프트 삭제 상태(deletedAt) 추가"
```

---

### Task 4: `WithdrawalReason` 열거형 + `WithdrawalFeedback` 익명 기록

**Files:**
- Create: `backend/src/main/java/com/runvas/user/domain/WithdrawalReason.java`
- Create: `backend/src/main/java/com/runvas/user/domain/WithdrawalFeedback.java`
- Create: `backend/src/main/java/com/runvas/user/repository/WithdrawalFeedbackRepository.java`
- Create: `backend/src/test/java/com/runvas/user/repository/WithdrawalFeedbackRepositoryTest.java`

**Interfaces:**
- Consumes: `withdrawal_feedback` 테이블 (Task 2)
- Produces: `WithdrawalReason` 열거형(`NOT_USING`, `MISSING_FEATURES`, `BUGS_OR_ERRORS`,
  `PRIVACY_CONCERN`, `OTHER`), `WithdrawalFeedback.of(WithdrawalReason, String): WithdrawalFeedback`,
  `WithdrawalFeedbackRepository extends JpaRepository<WithdrawalFeedback, String>` — Task 5
  (`AccountWithdrawalService`), Task 6(`WithdrawRequest`)이 사용.

- [ ] **Step 1: `WithdrawalReason` 신규 생성**

`backend/src/main/java/com/runvas/user/domain/WithdrawalReason.java`:

```java
package com.runvas.user.domain;

public enum WithdrawalReason {
    NOT_USING,
    MISSING_FEATURES,
    BUGS_OR_ERRORS,
    PRIVACY_CONCERN,
    OTHER
}
```

- [ ] **Step 2: 실패하는 리포지토리 테스트 작성**

`backend/src/test/java/com/runvas/user/repository/WithdrawalFeedbackRepositoryTest.java` 신규 생성:

```java
package com.runvas.user.repository;

import com.runvas.user.domain.WithdrawalFeedback;
import com.runvas.user.domain.WithdrawalReason;
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
class WithdrawalFeedbackRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    WithdrawalFeedbackRepository withdrawalFeedbackRepository;

    @Test
    void savesFeedbackWithoutAnyUserReference() {
        WithdrawalFeedback feedback = WithdrawalFeedback.of(WithdrawalReason.OTHER, "탈퇴 사유 직접입력");

        withdrawalFeedbackRepository.saveAndFlush(feedback);

        WithdrawalFeedback found = withdrawalFeedbackRepository.findById(feedback.getId()).orElseThrow();
        assertThat(found.getReasonCode()).isEqualTo(WithdrawalReason.OTHER);
        assertThat(found.getReasonDetail()).isEqualTo("탈퇴 사유 직접입력");
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.repository.WithdrawalFeedbackRepositoryTest"`
Expected: FAIL — 컴파일 에러 (`WithdrawalFeedback`, `WithdrawalFeedbackRepository` 없음)

- [ ] **Step 4: `WithdrawalFeedback` 엔티티 구현**

`backend/src/main/java/com/runvas/user/domain/WithdrawalFeedback.java` 신규 생성:

```java
package com.runvas.user.domain;

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

// docs/data-model.md WithdrawalReason — 사용자 식별자를 전혀 포함하지 않는 익명 통계 기록.
// 계정이 나중에 하드 삭제되어도 이 테이블의 행은 영향받지 않는다.
@Entity
@Table(name = "withdrawal_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private WithdrawalReason reasonCode;

    @Column(name = "reason_detail", length = 200)
    private String reasonDetail;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private WithdrawalFeedback(WithdrawalReason reasonCode, String reasonDetail) {
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
    }

    public static WithdrawalFeedback of(WithdrawalReason reasonCode, String reasonDetail) {
        return new WithdrawalFeedback(reasonCode, reasonDetail);
    }
}
```

- [ ] **Step 5: `WithdrawalFeedbackRepository` 구현**

`backend/src/main/java/com/runvas/user/repository/WithdrawalFeedbackRepository.java` 신규 생성:

```java
package com.runvas.user.repository;

import com.runvas.user.domain.WithdrawalFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalFeedbackRepository extends JpaRepository<WithdrawalFeedback, String> {
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.repository.WithdrawalFeedbackRepositoryTest"`
Expected: PASS (1개 테스트) — 이 시점에 Task 2의 마이그레이션(V12, V13)도 실제로 적용되며 검증된다.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/user/domain/WithdrawalReason.java backend/src/main/java/com/runvas/user/domain/WithdrawalFeedback.java backend/src/main/java/com/runvas/user/repository/WithdrawalFeedbackRepository.java backend/src/test/java/com/runvas/user/repository/WithdrawalFeedbackRepositoryTest.java
git commit -m "feat(user): 익명 탈퇴 사유 기록(WithdrawalFeedback) 추가"
```

---

### Task 5: `DELETE /me` 엔드포인트 — 탈퇴 신청 + 사유 기록 + 토큰 블랙리스트

**Files:**
- Create: `backend/src/main/java/com/runvas/user/dto/WithdrawRequest.java`
- Create: `backend/src/main/java/com/runvas/user/service/AccountWithdrawalService.java`
- Create: `backend/src/test/java/com/runvas/user/service/AccountWithdrawalServiceTest.java`
- Modify: `backend/src/main/java/com/runvas/user/controller/MeController.java`
- Modify: `backend/src/test/java/com/runvas/user/controller/MeControllerTest.java`

**Interfaces:**
- Consumes: `User.isDeleted/markWithdrawn` (Task 3), `WithdrawalReason`,
  `WithdrawalFeedback.of/WithdrawalFeedbackRepository` (Task 4), 기존
  `TokenBlacklistService.blacklist(String): void` (`com.runvas.auth.service`, 이미 존재).
- Produces: `DELETE /api/me` — `204 No Content`. `AccountWithdrawalService.withdraw(UUID userId,
  String token, WithdrawalReason reason, String reasonDetail): void` — Task 6(로그인 자동 복구)와는
  독립적이지만 같은 `User` 상태를 공유한다.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`backend/src/test/java/com/runvas/user/service/AccountWithdrawalServiceTest.java` 신규 생성:

```java
package com.runvas.user.service;

import com.runvas.auth.service.TokenBlacklistService;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.User;
import com.runvas.user.domain.WithdrawalFeedback;
import com.runvas.user.domain.WithdrawalReason;
import com.runvas.user.repository.UserRepository;
import com.runvas.user.repository.WithdrawalFeedbackRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountWithdrawalServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WithdrawalFeedbackRepository withdrawalFeedbackRepository = mock(WithdrawalFeedbackRepository.class);
    private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
    private final AccountWithdrawalService accountWithdrawalService =
            new AccountWithdrawalService(userRepository, withdrawalFeedbackRepository, tokenBlacklistService);

    private static User persistedUser() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Seoul Runner", null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void marksUserWithdrawnRecordsFeedbackAndBlacklistsToken() {
        User user = persistedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        accountWithdrawalService.withdraw(user.getId(), "token-1", WithdrawalReason.NOT_USING, null);

        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);
        verify(withdrawalFeedbackRepository).save(any(WithdrawalFeedback.class));
        verify(tokenBlacklistService).blacklist("token-1");
    }

    @Test
    void rejectsOtherReasonWithoutDetail() {
        User user = persistedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountWithdrawalService.withdraw(user.getId(), "token-1", WithdrawalReason.OTHER, null))
                .isInstanceOfSatisfying(RunvasException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(userRepository, never()).save(any());
        verify(tokenBlacklistService, never()).blacklist(any());
    }

    @Test
    void rejectsReasonDetailLongerThan200Characters() {
        User user = persistedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        String tooLong = "a".repeat(201);

        assertThatThrownBy(() -> accountWithdrawalService.withdraw(user.getId(), "token-1", WithdrawalReason.OTHER, tooLong))
                .isInstanceOfSatisfying(RunvasException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void secondWithdrawCallOnAlreadyDeletedUserOnlyBlacklistsToken() {
        User user = persistedUser();
        user.markWithdrawn();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        accountWithdrawalService.withdraw(user.getId(), "token-2", WithdrawalReason.NOT_USING, null);

        verify(userRepository, never()).save(any());
        verify(withdrawalFeedbackRepository, never()).save(any());
        verify(tokenBlacklistService).blacklist("token-2");
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.service.AccountWithdrawalServiceTest"`
Expected: FAIL — 컴파일 에러 (`AccountWithdrawalService` 없음)

- [ ] **Step 3: `WithdrawRequest` DTO 구현**

`backend/src/main/java/com/runvas/user/dto/WithdrawRequest.java` 신규 생성:

```java
package com.runvas.user.dto;

import com.runvas.user.domain.WithdrawalReason;
import jakarta.validation.constraints.NotNull;

public record WithdrawRequest(@NotNull WithdrawalReason reason, String reasonDetail) {
}
```

- [ ] **Step 4: `AccountWithdrawalService` 구현**

`backend/src/main/java/com/runvas/user/service/AccountWithdrawalService.java` 신규 생성:

```java
package com.runvas.user.service;

import com.runvas.auth.service.TokenBlacklistService;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.User;
import com.runvas.user.domain.WithdrawalFeedback;
import com.runvas.user.domain.WithdrawalReason;
import com.runvas.user.repository.UserRepository;
import com.runvas.user.repository.WithdrawalFeedbackRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWithdrawalService {

    private static final int MAX_REASON_DETAIL_LENGTH = 200;

    private final UserRepository userRepository;
    private final WithdrawalFeedbackRepository withdrawalFeedbackRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public AccountWithdrawalService(
            UserRepository userRepository,
            WithdrawalFeedbackRepository withdrawalFeedbackRepository,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.userRepository = userRepository;
        this.withdrawalFeedbackRepository = withdrawalFeedbackRepository;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Transactional
    public void withdraw(UUID userId, String token, WithdrawalReason reason, String reasonDetail) {
        if (reason == WithdrawalReason.OTHER && (reasonDetail == null || reasonDetail.isBlank())) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "reasonDetail is required when reason is OTHER");
        }
        if (reasonDetail != null && reasonDetail.length() > MAX_REASON_DETAIL_LENGTH) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "reasonDetail must be at most 200 characters");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));

        if (!user.isDeleted()) {
            user.markWithdrawn();
            userRepository.save(user);
            withdrawalFeedbackRepository.save(WithdrawalFeedback.of(reason, reasonDetail));
        }

        tokenBlacklistService.blacklist(token);
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.service.AccountWithdrawalServiceTest"`
Expected: PASS (4개 테스트)

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`backend/src/test/java/com/runvas/user/controller/MeControllerTest.java` 상단 import 블록에 추가:

```java
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

(이미 있는 `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;`는
그대로 둔다.)

클래스 마지막 테스트(`unauthenticatedRequestReturns401`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
    @Test
    void withdrawMarksAccountDeletedAndBlacklistsToken() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-withdraw", "runner@example.com", "Seoul Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(delete("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "NOT_USING" }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawWithOtherReasonRequiresDetail() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-withdraw-other", "runner@example.com", "Seoul Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(delete("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "OTHER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withdrawWithoutAuthReturns401() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "NOT_USING" }
                                """))
                .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 7: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.controller.MeControllerTest"`
Expected: FAIL — `404` (`DELETE /api/me` 없음)

- [ ] **Step 8: `MeController`에 `DELETE /me` 추가**

`backend/src/main/java/com/runvas/user/controller/MeController.java` 전체를 다음으로 교체:

```java
package com.runvas.user.controller;

import com.runvas.backend.community.BookmarkService;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.global.security.RunvasPrincipal;
import com.runvas.user.domain.User;
import com.runvas.user.dto.MeResponse;
import com.runvas.user.dto.UpdateMeRequest;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.dto.WithdrawRequest;
import com.runvas.user.repository.UserRepository;
import com.runvas.user.service.AccountWithdrawalService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository userRepository;
    private final BookmarkService bookmarkService;
    private final AccountWithdrawalService accountWithdrawalService;

    public MeController(
            UserRepository userRepository,
            BookmarkService bookmarkService,
            AccountWithdrawalService accountWithdrawalService
    ) {
        this.userRepository = userRepository;
        this.bookmarkService = bookmarkService;
        this.accountWithdrawalService = accountWithdrawalService;
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal RunvasPrincipal principal) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(principal.userId())
                .map(user -> new MeResponse(UserResponse.from(user)))
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));
    }

    @GetMapping("/me/bookmarked-courses")
    Map<String, Object> listBookmarkedCourses(@AuthenticationPrincipal RunvasPrincipal principal) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        BookmarkService.ListResult result = bookmarkService.listByUser();
        return Map.of("courses", result.courses(), "pageInfo", result.pageInfo());
    }

    @PatchMapping("/me")
    MeResponse updateMe(
            @AuthenticationPrincipal RunvasPrincipal principal,
            @RequestBody @Valid UpdateMeRequest request
    ) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));
        user.updateProfile(request.nickname(), request.profileImageUrl(), request.bio(), request.runningPaceSecPerKm());
        userRepository.save(user);
        return new MeResponse(UserResponse.from(user));
    }

    @DeleteMapping("/me")
    ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal RunvasPrincipal principal,
            Authentication authentication,
            @RequestBody @Valid WithdrawRequest request
    ) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        String token = (String) authentication.getCredentials();
        accountWithdrawalService.withdraw(principal.userId(), token, request.reason(), request.reasonDetail());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 9: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.controller.MeControllerTest"`
Expected: PASS (기존 2개 + 신규 3개, 총 5개)

- [ ] **Step 10: 전체 백엔드 테스트로 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/runvas/user/dto/WithdrawRequest.java backend/src/main/java/com/runvas/user/service/AccountWithdrawalService.java backend/src/test/java/com/runvas/user/service/AccountWithdrawalServiceTest.java backend/src/main/java/com/runvas/user/controller/MeController.java backend/src/test/java/com/runvas/user/controller/MeControllerTest.java
git commit -m "feat(user): DELETE /api/me로 회원 탈퇴 신청(소프트 삭제) 추가"
```

---

### Task 6: 카카오 재로그인 시 소프트 삭제 계정 자동 복구

**Files:**
- Modify: `backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java`
- Modify: `backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java`

**Interfaces:**
- Consumes: `User.isDeleted/restore` (Task 3)
- Produces: 없음 (기존 `KakaoAuthService.login(KakaoLoginRequest): AuthResponse` 동작 변경 —
  소프트 삭제 상태였던 사용자가 로그인하면 `deletedAt`이 지워짐)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java`에서 `logsInExistingKakaoUserWithoutCreatingAnotherUser` 테스트 뒤, `treatsDuplicateCreateRaceAsExistingUserLogin` 테스트 앞에 추가:

```java
    @Test
    void loginRestoresSoftDeletedUserAndClearsDeletedAt() {
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "authorization-code", "runvas://auth/kakao");
        User withdrawnUser = persisted(User.createKakaoUser("kakao-123", "runner@example.com", "Seoul Runner", null));
        withdrawnUser.markWithdrawn();
        when(kakaoAuthClient.fetchUserInfo("authorization-code", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-123", "runner@example.com", "Seoul Runner", null));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(withdrawnUser));

        AuthResponse response = kakaoAuthService.login(request);

        assertThat(response.isNewUser()).isFalse();
        assertThat(withdrawnUser.isDeleted()).isFalse();
        verify(userRepository).save(withdrawnUser);
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.KakaoAuthServiceTest"`
Expected: FAIL — `withdrawnUser.isDeleted()`가 여전히 `true`

- [ ] **Step 3: `KakaoAuthService.login` 수정**

`backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java`의 `login` 메서드에서 다음
블록:

```java
        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.KAKAO,
                kakaoUserInfo.providerUserId()
        );
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(kakaoUserInfo));
```

을 다음으로 교체:

```java
        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.KAKAO,
                kakaoUserInfo.providerUserId()
        );
        existingUser.ifPresent(this::restoreIfWithdrawn);
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(kakaoUserInfo));
```

같은 클래스 안, `createOrFindRacedUser` 메서드 앞에 새 private 메서드를 추가:

```java
    private void restoreIfWithdrawn(User user) {
        if (user.isDeleted()) {
            user.restore();
            userRepository.save(user);
        }
    }
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.KakaoAuthServiceTest"`
Expected: PASS (기존 5개 + 신규 1개, 총 6개)

- [ ] **Step 5: 전체 백엔드 테스트로 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java
git commit -m "feat(auth): 유예기간 중 재로그인 시 소프트 삭제 계정 자동 복구"
```

---

### Task 7: 작성자가 탈퇴한 경우 "탈퇴한 사용자" placeholder 표시

**Files:**
- Modify: `backend/src/main/java/com/runvas/user/dto/PublicProfileResponse.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/dto/PublicProfile.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/PostService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/CommentService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/PostControllerTest.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/CourseCommentControllerTest.java`

**Interfaces:**
- Consumes: 없음 (기존 `User`, `UserRepository`)
- Produces: `PublicProfileResponse.withdrawn(String authorId): PublicProfileResponse`,
  `PublicProfile.withdrawn(String authorId): PublicProfile` — 하드 삭제 이후(Task 9의
  `AccountPurgeService`가 실행된 뒤) 이 콘텐츠들이 500 대신 정상 응답을 반환하게 만든다.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 3개 작성**

`backend/src/test/java/com/runvas/backend/community/PostControllerTest.java` 상단 import에
`import com.runvas.user.repository.UserRepository;`는 이미 있으므로 그대로 두고, 클래스 마지막
테스트(`listRejectsZeroLimit`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
	@Test
	void showsWithdrawnPlaceholderWhenAuthorNoLongerExists() throws Exception {
		String accessToken = createUserAndToken("author-to-withdraw");
		String postId = createPost(accessToken, "탈퇴 전 작성한 글", "본문");
		User author = userRepository.findAll().stream()
				.filter(u -> u.getNickname().equals("author-to-withdraw"))
				.findFirst()
				.orElseThrow();
		userRepository.delete(author);

		mockMvc.perform(get("/api/posts/" + postId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.post.author.nickname").value("탈퇴한 사용자"))
				.andExpect(jsonPath("$.post.author.profileImageUrl").doesNotExist())
				.andExpect(jsonPath("$.post.author.bio").doesNotExist());
	}
```

`backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java` 클래스 마지막
테스트(`listRejectsZeroLimit`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
	@Test
	void showsWithdrawnPlaceholderWhenCommentAuthorNoLongerExists() throws Exception {
		String accessToken = createUserAndToken("commenter-to-withdraw");
		String postId = createPost(accessToken);
		createComment(accessToken, postId, "탈퇴 전 작성한 댓글");
		User author = userRepository.findAll().stream()
				.filter(u -> u.getNickname().equals("commenter-to-withdraw"))
				.findFirst()
				.orElseThrow();
		userRepository.delete(author);

		mockMvc.perform(get("/api/posts/" + postId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[0].author.nickname").value("탈퇴한 사용자"))
				.andExpect(jsonPath("$.comments[0].author.profileImageUrl").doesNotExist());
	}
```

`backend/src/test/java/com/runvas/backend/community/CourseCommentControllerTest.java`에는 이미
`createComment(String courseId, String token, String body): String` 헬퍼가 있다(파일 끝
`private String createComment(...)`). 마지막 테스트 뒤, 클래스 닫는 `}` 앞에 추가:

```java
	@Test
	void showsWithdrawnPlaceholderWhenCourseCommentAuthorNoLongerExists() throws Exception {
		String token = createUserToken("course-commenter-to-withdraw");
		String courseId = createCourse(authorIdFromToken(token), CourseVisibility.PUBLIC);
		createComment(courseId, token, "탈퇴 전 작성한 코스 댓글");
		User author = userRepository.findAll().stream()
				.filter(u -> u.getNickname().equals("course-commenter-to-withdraw"))
				.findFirst()
				.orElseThrow();
		userRepository.delete(author);

		mockMvc.perform(get("/api/courses/" + courseId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[0].author.nickname").value("탈퇴한 사용자"))
				.andExpect(jsonPath("$.comments[0].author.profileImageUrl").doesNotExist());
	}
```

`CourseCommentControllerTest.java` 상단 import에 `import com.runvas.user.domain.User;`와
`import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;`이 이미
있는지 확인하고 없으면 추가한다.

- [ ] **Step 2: 테스트 실행해서 실패(500) 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostControllerTest" --tests "com.runvas.backend.community.CommentControllerTest" --tests "com.runvas.backend.community.CourseCommentControllerTest"`
Expected: FAIL — 신규 테스트 3개가 `500`을 반환해 `status().isOk()` 기대와 불일치

- [ ] **Step 3: `PublicProfileResponse`에 `withdrawn()` 추가**

`backend/src/main/java/com/runvas/user/dto/PublicProfileResponse.java` 전체를 다음으로 교체:

```java
package com.runvas.user.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1. Post/Comment 작성자 정보에 쓴다.
// id는 UserResponse.from()과 동일하게 "user_" + UUID 포맷으로 통일한다.
public record PublicProfileResponse(String id, String nickname, String profileImageUrl, String bio) {

	private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

	public static PublicProfileResponse from(User user) {
		return new PublicProfileResponse(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}

	// docs/data-model.md "탈퇴한 사용자 표시" — 작성자 계정이 하드 삭제된 뒤에도 콘텐츠는 남기고
	// 작성자 표시만 고정 문구로 대체한다.
	public static PublicProfileResponse withdrawn(String authorId) {
		return new PublicProfileResponse(authorId, WITHDRAWN_NICKNAME, null, null);
	}
}
```

- [ ] **Step 4: `PublicProfile`에 `withdrawn()` 추가**

`backend/src/main/java/com/runvas/backend/community/dto/PublicProfile.java` 전체를 다음으로 교체:

```java
package com.runvas.backend.community.dto;

import com.runvas.user.domain.User;

// docs/data-model.md PublicProfile과 1:1 — 커뮤니티 응답에서 User 전체 대신 이 필드만 노출한다.
public record PublicProfile(String id, String nickname, String profileImageUrl, String bio) {

	private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

	public static PublicProfile from(User user) {
		return new PublicProfile(
				"user_" + user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio());
	}

	// docs/data-model.md "탈퇴한 사용자 표시" — 작성자 계정이 하드 삭제된 뒤에도 콘텐츠는 남기고
	// 작성자 표시만 고정 문구로 대체한다.
	public static PublicProfile withdrawn(String authorId) {
		return new PublicProfile(authorId, WITHDRAWN_NICKNAME, null, null);
	}
}
```

- [ ] **Step 5: `PostService.toResponse`에서 fallback 적용**

`backend/src/main/java/com/runvas/backend/community/PostService.java`에서 다음 메서드:

```java
	private PostResponse toResponse(Post post, boolean likedByMe) {
		User author = userRepository.findById(UUID.fromString(post.getAuthorId()))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return PostResponse.from(post, PublicProfileResponse.from(author), likedByMe);
	}
```

을 다음으로 교체:

```java
	private PostResponse toResponse(Post post, boolean likedByMe) {
		PublicProfileResponse author = userRepository.findById(UUID.fromString(post.getAuthorId()))
				.map(PublicProfileResponse::from)
				.orElseGet(() -> PublicProfileResponse.withdrawn(post.getAuthorId()));
		return PostResponse.from(post, author, likedByMe);
	}
```

(이 파일에서 더 이상 쓰이지 않는 `ApiException`/`ErrorCode` import가 다른 메서드에서도 쓰이는지
확인한다 — `validateAttachedCourse`가 여전히 `ApiException`을 쓰므로 import는 그대로 둔다.)

- [ ] **Step 6: `CommentService.toResponse`에서 fallback 적용**

`backend/src/main/java/com/runvas/backend/community/CommentService.java`에서 다음 메서드:

```java
	private CommentResponse toResponse(Comment comment) {
		User author = userRepository.findById(UUID.fromString(comment.getAuthorId()))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return CommentResponse.from(comment, PublicProfileResponse.from(author));
	}
```

을 다음으로 교체:

```java
	private CommentResponse toResponse(Comment comment) {
		PublicProfileResponse author = userRepository.findById(UUID.fromString(comment.getAuthorId()))
				.map(PublicProfileResponse::from)
				.orElseGet(() -> PublicProfileResponse.withdrawn(comment.getAuthorId()));
		return CommentResponse.from(comment, author);
	}
```

(`ApiException`/`ErrorCode` import가 이 파일의 다른 메서드에서도 쓰이는지 확인한다 —
`findPostOrThrow`/`findCommentOrThrow`/`requireAuthor`가 여전히 쓰므로 import는 그대로 둔다.)

- [ ] **Step 7: `CourseCommentService.resolveAuthor`에서 fallback 적용**

`backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`에서 다음 메서드:

```java
	private PublicProfile resolveAuthor(String authorId) {
		User author = userRepository
				.findById(UUID.fromString(authorId))
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "작성자를 찾을 수 없습니다"));
		return PublicProfile.from(author);
	}
```

을 다음으로 교체:

```java
	private PublicProfile resolveAuthor(String authorId) {
		return userRepository.findById(UUID.fromString(authorId))
				.map(PublicProfile::from)
				.orElseGet(() -> PublicProfile.withdrawn(authorId));
	}
```

(`ApiException`/`ErrorCode` import가 이 파일의 다른 메서드에서도 쓰이는지 확인한다 —
`findCourseOrThrow`/`findCommentOrThrow`/`requireAuthor` 등이 여전히 쓰므로 import는 그대로 둔다.)

- [ ] **Step 8: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostControllerTest" --tests "com.runvas.backend.community.CommentControllerTest" --tests "com.runvas.backend.community.CourseCommentControllerTest"`
Expected: PASS (기존 테스트 전부 + 신규 3개)

- [ ] **Step 9: 전체 백엔드 테스트로 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/runvas/user/dto/PublicProfileResponse.java backend/src/main/java/com/runvas/backend/community/dto/PublicProfile.java backend/src/main/java/com/runvas/backend/community/PostService.java backend/src/main/java/com/runvas/backend/community/CommentService.java backend/src/main/java/com/runvas/backend/community/CourseCommentService.java backend/src/test/java/com/runvas/backend/community/PostControllerTest.java backend/src/test/java/com/runvas/backend/community/CommentControllerTest.java backend/src/test/java/com/runvas/backend/community/CourseCommentControllerTest.java
git commit -m "feat(community): 작성자 계정이 하드 삭제되면 \"탈퇴한 사용자\"로 표시"
```

---

### Task 8: 카카오 unlink 클라이언트 (하드 삭제 시점에만 사용)

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/runvas/auth/service/KakaoUnlinkClient.java`
- Create: `backend/src/main/java/com/runvas/auth/service/KakaoHttpUnlinkClient.java`

**Interfaces:**
- Consumes: `runvas.kakao.admin-key`, `runvas.kakao.unlink-uri` 설정 (신규)
- Produces: `KakaoUnlinkClient.unlink(String providerUserId): void` — Task 9의
  `AccountPurgeService`가 테스트에서는 목(mock)으로, 운영에서는 `KakaoHttpUnlinkClient` 빈으로 사용.

이 클라이언트는 실제 카카오 서버 응답 파싱이 필요 없고(성공/실패만 판단), 이 저장소의
`KakaoHttpAuthClient`도 실제 HTTP 왕복을 단위 테스트로 검증하지 않는 것과 같은 이유로 별도
`KakaoHttpUnlinkClientTest`는 작성하지 않는다 — 동작 계약은 Task 9의
`AccountPurgeServiceTest`가 `KakaoUnlinkClient` 인터페이스를 목으로 검증한다.

- [ ] **Step 1: `application.yml`에 admin key와 unlink URI 설정 추가**

`backend/src/main/resources/application.yml`에서:

```yaml
  kakao:
    token-uri: ${KAKAO_TOKEN_URI:https://kauth.kakao.com/oauth/token}
    user-info-uri: ${KAKAO_USER_INFO_URI:https://kapi.kakao.com/v2/user/me}
    rest-api-key: ${KAKAO_REST_API_KEY:}
    client-secret: ${KAKAO_CLIENT_SECRET:}
```

를 다음으로 교체:

```yaml
  kakao:
    token-uri: ${KAKAO_TOKEN_URI:https://kauth.kakao.com/oauth/token}
    user-info-uri: ${KAKAO_USER_INFO_URI:https://kapi.kakao.com/v2/user/me}
    unlink-uri: ${KAKAO_UNLINK_URI:https://kapi.kakao.com/v1/user/unlink}
    rest-api-key: ${KAKAO_REST_API_KEY:}
    client-secret: ${KAKAO_CLIENT_SECRET:}
    admin-key: ${KAKAO_ADMIN_KEY:}
```

- [ ] **Step 2: `KakaoUnlinkClient` 인터페이스 생성**

`backend/src/main/java/com/runvas/auth/service/KakaoUnlinkClient.java` 신규 생성:

```java
package com.runvas.auth.service;

public interface KakaoUnlinkClient {

    void unlink(String providerUserId);
}
```

- [ ] **Step 3: `KakaoHttpUnlinkClient` 구현**

`backend/src/main/java/com/runvas/auth/service/KakaoHttpUnlinkClient.java` 신규 생성:

```java
package com.runvas.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoHttpUnlinkClient implements KakaoUnlinkClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoHttpUnlinkClient.class);

    private final RestClient restClient;
    private final String unlinkUri;
    private final String adminKey;

    public KakaoHttpUnlinkClient(
            RestClient.Builder restClientBuilder,
            @Value("${runvas.kakao.unlink-uri}") String unlinkUri,
            @Value("${runvas.kakao.admin-key}") String adminKey
    ) {
        this.restClient = restClientBuilder.build();
        this.unlinkUri = unlinkUri;
        this.adminKey = adminKey;
    }

    @Override
    public void unlink(String providerUserId) {
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("Kakao admin key is not configured; skipping unlink for provider user {}", providerUserId);
            return;
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);

        restClient.post()
                .uri(unlinkUri)
                .header("Authorization", "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("Kakao unlink failed with status " + res.getStatusCode());
                })
                .toBodilessEntity();
    }
}
```

- [ ] **Step 4: 빌드로 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.yml backend/src/main/java/com/runvas/auth/service/KakaoUnlinkClient.java backend/src/main/java/com/runvas/auth/service/KakaoHttpUnlinkClient.java
git commit -m "feat(auth): 카카오 unlink 클라이언트 추가 (계정 하드 삭제 시점 전용)"
```

---

### Task 9: 유예기간 만료 계정 하드 삭제 배치

**Files:**
- Modify: `backend/src/main/java/com/runvas/user/repository/UserRepository.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/LikeRepository.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/BookmarkRepository.java`
- Create: `backend/src/main/java/com/runvas/user/service/AccountPurgeService.java`
- Create: `backend/src/test/java/com/runvas/user/service/AccountPurgeServiceTest.java`
- Create: `backend/src/main/java/com/runvas/user/service/AccountPurgeScheduler.java`
- Modify: `backend/src/main/java/com/runvas/RunvasApplication.java`

**Interfaces:**
- Consumes: `User.isDeleted/getProvider/getProviderUserId` (Task 3), `KakaoUnlinkClient.unlink`
  (Task 8), `UserRepository.findByDeletedAtLessThanEqual` (본 태스크에서 추가)
- Produces: `AccountPurgeService.purgeExpiredAccounts(): void` — `AccountPurgeScheduler`가 매일
  호출. 이 태스크가 이번 기능의 마지막 백엔드 태스크다.

- [ ] **Step 1: `UserRepository`에 조회 메서드 추가**

`backend/src/main/java/com/runvas/user/repository/UserRepository.java` 전체를 다음으로 교체:

```java
package com.runvas.user.repository;

import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    List<User> findByDeletedAtLessThanEqual(Instant threshold);
}
```

- [ ] **Step 2: `LikeRepository`/`BookmarkRepository`에 삭제 메서드 추가**

`backend/src/main/java/com/runvas/backend/community/LikeRepository.java` 전체를 다음으로 교체:

```java
package com.runvas.backend.community;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Like.LikeId> {

	void deleteAllByIdUserId(String userId);
}
```

`backend/src/main/java/com/runvas/backend/community/BookmarkRepository.java`에서:

```java
public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.BookmarkId> {

	List<Bookmark> findByIdUserIdOrderByCreatedAtDesc(String userId);
}
```

을 다음으로 교체:

```java
public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.BookmarkId> {

	List<Bookmark> findByIdUserIdOrderByCreatedAtDesc(String userId);

	void deleteAllByIdUserId(String userId);
}
```

- [ ] **Step 3: 실패하는 서비스 테스트 작성**

`backend/src/test/java/com/runvas/user/service/AccountPurgeServiceTest.java` 신규 생성:

```java
package com.runvas.user.service;

import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeRepository;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPurgeServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
    private final KakaoUnlinkClient kakaoUnlinkClient = mock(KakaoUnlinkClient.class);
    private final AccountPurgeService accountPurgeService =
            new AccountPurgeService(userRepository, likeRepository, bookmarkRepository, kakaoUnlinkClient);

    private static User kakaoUser(String providerUserId) {
        User user = User.createKakaoUser(providerUserId, null, "탈퇴예정", null);
        ReflectionTestUtils.setField(user, "id", java.util.UUID.randomUUID());
        user.markWithdrawn();
        return user;
    }

    @Test
    void purgesExpiredKakaoUserAfterUnlinkingAndDeletingLikesAndBookmarks() {
        User expired = kakaoUser("kakao-expired");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient).unlink("kakao-expired");
        verify(likeRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void skipsUnlinkForDevProvider() {
        User devUser = User.createDevUser("dev-nickname");
        ReflectionTestUtils.setField(devUser, "id", java.util.UUID.randomUUID());
        devUser.markWithdrawn();
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(devUser));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient, never()).unlink(anyString());
        verify(userRepository).delete(devUser);
    }

    @Test
    void continuesDeletionWhenUnlinkFails() {
        User expired = kakaoUser("kakao-unlink-fails");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));
        doThrow(new IllegalStateException("kakao down")).when(kakaoUnlinkClient).unlink("kakao-unlink-fails");

        accountPurgeService.purgeExpiredAccounts();

        verify(likeRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }
}
```

- [ ] **Step 4: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.service.AccountPurgeServiceTest"`
Expected: FAIL — 컴파일 에러 (`AccountPurgeService` 없음)

- [ ] **Step 5: `AccountPurgeService` 구현**

`backend/src/main/java/com/runvas/user/service/AccountPurgeService.java` 신규 생성:

```java
package com.runvas.user.service;

import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeRepository;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPurgeService {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeService.class);
    private static final int GRACE_PERIOD_DAYS = 30;

    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final KakaoUnlinkClient kakaoUnlinkClient;

    public AccountPurgeService(
            UserRepository userRepository,
            LikeRepository likeRepository,
            BookmarkRepository bookmarkRepository,
            KakaoUnlinkClient kakaoUnlinkClient
    ) {
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.kakaoUnlinkClient = kakaoUnlinkClient;
    }

    @Transactional
    public void purgeExpiredAccounts() {
        Instant threshold = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        List<User> expired = userRepository.findByDeletedAtLessThanEqual(threshold);
        for (User user : expired) {
            purgeOne(user);
        }
    }

    private void purgeOne(User user) {
        if (user.getProvider() == AuthProvider.KAKAO) {
            try {
                kakaoUnlinkClient.unlink(user.getProviderUserId());
            } catch (Exception exception) {
                log.warn("Kakao unlink failed for user {}, proceeding with deletion", user.getId(), exception);
            }
        }
        likeRepository.deleteAllByIdUserId(user.getId().toString());
        bookmarkRepository.deleteAllByIdUserId(user.getId().toString());
        userRepository.delete(user);
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.service.AccountPurgeServiceTest"`
Expected: PASS (3개 테스트)

- [ ] **Step 7: `AccountPurgeScheduler` 구현**

`backend/src/main/java/com/runvas/user/service/AccountPurgeScheduler.java` 신규 생성:

```java
package com.runvas.user.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccountPurgeScheduler {

    private final AccountPurgeService accountPurgeService;

    public AccountPurgeScheduler(AccountPurgeService accountPurgeService) {
        this.accountPurgeService = accountPurgeService;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpiredAccounts() {
        accountPurgeService.purgeExpiredAccounts();
    }
}
```

- [ ] **Step 8: `RunvasApplication`에 `@EnableScheduling` 추가**

`backend/src/main/java/com/runvas/RunvasApplication.java` 전체를 다음으로 교체:

```java
package com.runvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableScheduling
public class RunvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunvasApplication.class, args);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
```

- [ ] **Step 9: 전체 백엔드 테스트 & 빌드 확인**

Run: `cd backend && ./gradlew test build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/runvas/user/repository/UserRepository.java backend/src/main/java/com/runvas/backend/community/LikeRepository.java backend/src/main/java/com/runvas/backend/community/BookmarkRepository.java backend/src/main/java/com/runvas/user/service/AccountPurgeService.java backend/src/test/java/com/runvas/user/service/AccountPurgeServiceTest.java backend/src/main/java/com/runvas/user/service/AccountPurgeScheduler.java backend/src/main/java/com/runvas/RunvasApplication.java
git commit -m "feat(user): 유예기간 만료 계정을 매일 하드 삭제하는 배치 추가"
```

---

### Task 10: 모바일 — `WithdrawalReason` 타입 + `deleteMe` API 함수

**Files:**
- Modify: `mobile/src/types/index.ts`
- Modify: `mobile/src/services/authApi.ts`

**Interfaces:**
- Consumes: `DELETE /api/me` (Task 1, Task 5), `parseApiErrorMessage(response: Response):
  Promise<string>` (`mobile/src/utils/apiError.ts`, 기존), `isLogoutStatusAccepted(status: number):
  boolean` (`mobile/src/utils/authSession.ts`, 기존)
- Produces: `WithdrawalReason` 타입, `deleteMe(reason: WithdrawalReason, reasonDetail: string |
  null, accessToken: string): Promise<void>` — Task 11의 `AuthContext.withdraw()`가 사용.

- [ ] **Step 1: `mobile/src/types/index.ts`에 `WithdrawalReason` 타입 추가**

`mobile/src/types/index.ts` 파일 끝에 추가:

```ts
export type WithdrawalReason =
  | 'NOT_USING'
  | 'MISSING_FEATURES'
  | 'BUGS_OR_ERRORS'
  | 'PRIVACY_CONCERN'
  | 'OTHER';
```

- [ ] **Step 2: `mobile/src/services/authApi.ts`에 `deleteMe` 추가**

`mobile/src/services/authApi.ts` 상단 import에서:

```ts
import { AuthResponse, MeResponse, UpdateMeRequest } from '../types';
```

를

```ts
import { AuthResponse, MeResponse, UpdateMeRequest, WithdrawalReason } from '../types';
```

로 바꾼다. 파일 끝(`postAuthLogout` 함수 뒤)에 추가:

```ts
export async function deleteMe(
  reason: WithdrawalReason,
  reasonDetail: string | null,
  accessToken: string,
): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/me`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ reason, reasonDetail }),
  });

  if (!isLogoutStatusAccepted(response.status)) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
```

- [ ] **Step 3: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: Commit**

```bash
git add mobile/src/types/index.ts mobile/src/services/authApi.ts
git commit -m "feat(mobile): deleteMe API 함수와 WithdrawalReason 타입 추가"
```

---

### Task 11: 모바일 — `AuthContext.withdraw()`

**Files:**
- Modify: `mobile/src/contexts/AuthContext.tsx`

**Interfaces:**
- Consumes: `deleteMe(reason, reasonDetail, accessToken): Promise<void>` (Task 10)
- Produces: `AuthContextValue.withdraw: (reason: WithdrawalReason, reasonDetail: string | null) =>
  Promise<void>` — Task 12의 `WithdrawalReasonModal` 제출 핸들러와 Task 13의 `ProfileScreen`이 사용.
  실패 시 로컬 상태를 건드리지 않고 에러를 그대로 throw한다 (`logout()`과 동일한 계약).

- [ ] **Step 1: import 및 인터페이스 수정**

`mobile/src/contexts/AuthContext.tsx` 상단 import에서:

```ts
import { User } from '../types';
import { postAuthKakao, postAuthLogout } from '../services/authApi';
```

를

```ts
import { User, WithdrawalReason } from '../types';
import { deleteMe, postAuthKakao, postAuthLogout } from '../services/authApi';
```

로 바꾼다.

`AuthContextValue` 인터페이스의 `logout: () => Promise<void>;` 바로 뒤에 추가:

```ts
  withdraw: (reason: WithdrawalReason, reasonDetail: string | null) => Promise<void>;
```

- [ ] **Step 2: `withdraw` 구현 추가**

`logout` 함수 정의 뒤에 다음을 추가:

```ts
  const withdraw = useCallback(
    async (reason: WithdrawalReason, reasonDetail: string | null) => {
      if (!accessToken) return;
      await deleteMe(reason, reasonDetail, accessToken);
      await Promise.all([
        SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {}),
        SecureStore.deleteItemAsync(USER_KEY).catch(() => {}),
      ]);
      setUser(null);
      setAccessToken(null);
      setPendingNewUserRedirect(false);
    },
    [accessToken],
  );
```

`value`를 만드는 `useMemo` 호출의 객체 리터럴에서 `logout,` 바로 뒤에 `withdraw,`를 추가하고,
의존성 배열(두 번째 인자)에도 `logout,` 바로 뒤에 `withdraw,`를 추가한다.

- [ ] **Step 3: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: Commit**

```bash
git add mobile/src/contexts/AuthContext.tsx
git commit -m "feat(mobile): AuthContext에 회원 탈퇴(withdraw) 추가"
```

---

### Task 12: 모바일 — `WithdrawalReasonModal` 컴포넌트

**Files:**
- Create: `mobile/src/components/WithdrawalReasonModal.tsx`

**Interfaces:**
- Consumes: `WithdrawalReason` 타입 (Task 10), `Colors` (`mobile/src/constants/theme.ts`, 기존)
- Produces: `WithdrawalReasonModal` 컴포넌트, props `{ visible: boolean; onConfirm: (reason:
  WithdrawalReason, reasonDetail: string | null) => void; onClose: () => void; isSubmitting:
  boolean }` — Task 13의 `ProfileScreen`이 사용.

- [ ] **Step 1: `mobile/src/components/WithdrawalReasonModal.tsx` 신규 생성**

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
import { WithdrawalReason } from '../types';
import { Colors } from '../constants/theme';

const REASON_OPTIONS: { value: WithdrawalReason; label: string }[] = [
  { value: 'NOT_USING', label: '자주 사용하지 않아요' },
  { value: 'MISSING_FEATURES', label: '원하는 코스·기능이 없어요' },
  { value: 'BUGS_OR_ERRORS', label: '오류·버그가 많아요' },
  { value: 'PRIVACY_CONCERN', label: '개인정보가 걱정돼요' },
  { value: 'OTHER', label: '기타' },
];

interface WithdrawalReasonModalProps {
  visible: boolean;
  onConfirm: (reason: WithdrawalReason, reasonDetail: string | null) => void;
  onClose: () => void;
  isSubmitting: boolean;
}

export default function WithdrawalReasonModal({
  visible,
  onConfirm,
  onClose,
  isSubmitting,
}: WithdrawalReasonModalProps) {
  const [selectedReason, setSelectedReason] = useState<WithdrawalReason | null>(null);
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
          <Text style={styles.title}>회원 탈퇴</Text>
          <Text style={styles.subtitle}>탈퇴 사유를 선택해주세요.</Text>

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

          <Text style={styles.notice}>
            탈퇴 후 30일 동안은 같은 카카오 계정으로 로그인하면 계정이 복구돼요. 30일이 지나면
            작성한 코스/게시글은 남지만 작성자는 &apos;탈퇴한 사용자&apos;로 표시돼요.
          </Text>

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
                <Text style={styles.confirmButtonText}>탈퇴하기</Text>
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
  notice: {
    fontSize: 12,
    color: Colors.gray400,
    marginTop: 12,
    marginBottom: 16,
    lineHeight: 18,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 10,
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

- [ ] **Step 2: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/components/WithdrawalReasonModal.tsx
git commit -m "feat(mobile): 탈퇴 사유 선택 모달(WithdrawalReasonModal) 추가"
```

---

### Task 13: 모바일 — `ProfileScreen` 연결 + 구현 기록 + 최종 검증

**Files:**
- Modify: `mobile/src/screens/ProfileScreen.tsx`
- Create: `mobile/docs/implementations/account-withdrawal.md`

**Interfaces:**
- Consumes: `useAuth().withdraw(reason, reasonDetail): Promise<void>` (Task 11),
  `WithdrawalReasonModal` (Task 12)
- Produces: 없음 (최종 UI 화면 + 구현 기록)

- [ ] **Step 1: `ProfileScreen.tsx`에 탈퇴 버튼과 모달 연결**

`mobile/src/screens/ProfileScreen.tsx` 상단 import에서:

```ts
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { patchMe } from '../services/authApi';
import PaceSelector from '../components/PaceSelector';
```

를

```ts
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { patchMe } from '../services/authApi';
import PaceSelector from '../components/PaceSelector';
import WithdrawalReasonModal from '../components/WithdrawalReasonModal';
import { WithdrawalReason } from '../types';
```

로 바꾼다.

`export default function ProfileScreen() {` 내부, `const { user, logout, updateUser, accessToken } = useAuth();` 바로 뒤에 있는 state 선언들 다음에 추가:

```ts
  const [isWithdrawalModalOpen, setIsWithdrawalModalOpen] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
```

`const { user, logout, updateUser, accessToken } = useAuth();`를 다음으로 바꾼다:

```ts
  const { user, logout, withdraw, updateUser, accessToken } = useAuth();
```

`handleLogout` 콜백 정의 뒤에 다음을 추가:

```ts
  const handleWithdraw = useCallback(
    async (reason: WithdrawalReason, reasonDetail: string | null) => {
      setIsWithdrawing(true);
      try {
        await withdraw(reason, reasonDetail);
        setIsWithdrawalModalOpen(false);
      } catch (e: unknown) {
        Alert.alert('오류', e instanceof Error ? e.message : '탈퇴에 실패했습니다.');
      } finally {
        setIsWithdrawing(false);
      }
    },
    [withdraw],
  );
```

로그아웃 버튼(`<TouchableOpacity style={[styles.logoutButton, ...]}>...</TouchableOpacity>`) 바로
뒤, `</>`(user 블록을 닫는 fragment) 앞에 추가:

```tsx
            <TouchableOpacity
              style={styles.withdrawButton}
              activeOpacity={0.6}
              onPress={() => setIsWithdrawalModalOpen(true)}
            >
              <Text style={styles.withdrawButtonText}>회원 탈퇴</Text>
            </TouchableOpacity>
```

`</SafeAreaView>` 직전, `<PaceSelector ... />` 컴포넌트 뒤에 추가:

```tsx
      <WithdrawalReasonModal
        visible={isWithdrawalModalOpen}
        onConfirm={handleWithdraw}
        onClose={() => setIsWithdrawalModalOpen(false)}
        isSubmitting={isWithdrawing}
      />
```

`StyleSheet.create({...})` 안, `logoutButtonText` 스타일 정의 뒤에 추가:

```ts
  withdrawButton: {
    marginTop: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  withdrawButtonText: {
    color: Colors.gray400,
    fontSize: 12,
    fontWeight: '500',
  },
```

- [ ] **Step 2: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: 개발 서버로 번들 확인**

Run:
```bash
cd mobile && npx expo start &
sleep 8
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"
```
Expected: `200`. 확인 후 `npx expo start` 프로세스를 종료한다.

- [ ] **Step 4: 실기기/시뮬레이터에서 수동 확인**

카카오 로그인 → 프로필 탭 진입 → "회원 탈퇴" 버튼 탭 → 사유 5개 중 하나 선택("기타" 선택 시
텍스트 입력 필수 확인) → "탈퇴하기" 탭 → 로그인 화면/비로그인 상태로 전환되는지 확인. 이후 같은
카카오 계정으로 재로그인해 정상 로그인(자동 복구)되는지 확인.

- [ ] **Step 5: `mobile/docs/implementations/account-withdrawal.md` 신규 생성**

```markdown
# 회원 탈퇴

구현일: 2026-07-18

## 요약

`DELETE /api/me`로 탈퇴를 신청하면 계정은 즉시 삭제되지 않고 30일 소프트 삭제 유예기간에
들어간다. 이 기간 안에 같은 카카오 계정으로 로그인하면 백엔드가 자동으로 계정을 복구하므로,
모바일에는 별도 "복구" 화면이 없다 — 재로그인이 곧 복구다.

## 핵심 결정

- **탈퇴 사유 모달이 곧 최종 확인**: `WithdrawalReasonModal`의 "탈퇴하기" 버튼을 누르는 것 자체가
  최종 확인이므로, 로그아웃처럼 별도 `Alert.alert` 확인창을 추가로 띄우지 않는다. 대신 모달 안에
  30일 유예기간과 재로그인 시 자동 복구된다는 안내 문구를 넣었다.
- **백엔드 성공이 전제 조건**: `AuthContext.withdraw()`는 `deleteMe` 실패 시 예외를 그대로 던지고
  `SecureStore` 삭제를 실행하지 않는다 — `logout()`과 동일한 원칙(`docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`).
- **버튼 위치**: 로그아웃 버튼보다 눈에 덜 띄는 텍스트 버튼으로 그 아래 배치해 실수로 누르기
  어렵게 했다.

## 관련 문서

- `docs/api-contract.md` §DELETE /me
- `docs/superpowers/specs/2026-07-18-account-withdrawal-design.md`
```

- [ ] **Step 6: 백엔드 전체 테스트 최종 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 모바일 타입 체크 최종 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 8: Commit**

```bash
git add mobile/src/screens/ProfileScreen.tsx mobile/docs/implementations/account-withdrawal.md
git commit -m "feat(mobile): ProfileScreen에 회원 탈퇴 버튼과 사유 선택 모달 연결"
```

---

## Verification (전체 완료 후)

1. `cd backend && ./gradlew test build` → `BUILD SUCCESSFUL`
2. `cd mobile && npx tsc --noEmit` → 에러 없음
3. 백엔드 로컬 실행 후:
   - `POST /api/auth/kakao`로 로그인 → `accessToken` 획득
   - `DELETE /api/me` (`{ "reason": "NOT_USING" }`) → `204`
   - 같은 토큰으로 `GET /api/me` → `401 UNAUTHORIZED`
   - 같은 카카오 계정으로 `POST /api/auth/kakao` 재로그인 → `200`, `isNewUser: false` (자동 복구)
4. `AccountPurgeService.purgeExpiredAccounts()`를 단위/통합 테스트가 아닌 수동 배치 실행 또는
   테스트 데이터로 30일 지난 `deletedAt`을 만들어 실행 → 해당 사용자의 게시글 작성자가
   `"탈퇴한 사용자"`로 표시되는지 확인
5. 모바일 실기기/시뮬레이터: 로그인 → 프로필 탭 → 회원 탈퇴 → 사유 선택 → 탈퇴 → 재로그인(자동
   복구) 흐름 확인
6. `docs/api-contract.md`의 `DELETE /me` 예시가 실제 동작(204, 빈 본문)과 일치하는지 확인
