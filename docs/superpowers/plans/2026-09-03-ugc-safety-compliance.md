# UGC 안전 컴플라이언스(Guideline 1.2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**배경:** App Store 심사 거부(Guideline 1.2 Safety - User-Generated Content). 신고(41c4620)/차단(6e5fb17) 기능은 이미 있지만, Apple이 요구하는 5가지 중 3가지가 빠져 있다: (1) 부적절 콘텐츠/사용자 무관용 조항이 담긴 이용약관 동의, (2) 콘텐츠 필터링 메커니즘, (3) 신고 접수 24시간 내 콘텐츠 삭제 + 사용자 퇴출. 차단 시 "개발자에게 알림"도 빠져 있다.

**Goal:** 이용약관 동의 게이트, 서버 측 금칙어 필터링, 신고/차단 발생 시 운영자 이메일 알림, 관리자의 사용자 정지(ban) 기능을 추가해 1.2 거부 사유를 해소한다.

**Architecture:** 기존 `Report`/`Block` 도메인(`backend/src/main/java/com/runvas/backend/community/`)과 `AdminReportActionService`를 확장한다. 신규 개념은 `User.termsAgreedAt`(동의 시각), `User.bannedAt`(정지 시각) 두 컬럼과, 신고/차단 발생 시 운영자에게 이메일을 보내는 `AdminNotificationService` 하나다. 모바일은 로그인 버튼을 누르기 전에 약관 동의 게이트를 강제하고, 동의 시각을 카카오/Apple 로그인 요청에 함께 보낸다.

**Tech Stack:** Spring Boot(Gradle, JUnit5, Flyway), `spring-boot-starter-mail`(신규 추가), React Native/Expo.

## Global Constraints

- `backend/`, `mobile/` 변경 전에 `docs/`를 먼저 고친다 (루트 `CLAUDE.md` docs-first 원칙).
- 스키마 변경은 `spring.jpa.hibernate.ddl-auto: validate` + `spring.flyway.enabled: true`이므로, 엔티티에 컬럼을 추가할 때마다 반드시 `backend/src/main/resources/db/migration/`에 Flyway 마이그레이션 파일을 함께 추가한다 (엔티티만 고치면 애플리케이션이 기동 시 스키마 불일치로 실패한다). 마지막 마이그레이션은 `V16__create_blocks.sql`이므로 다음 파일은 `V17`부터 시작한다.
- 커밋 메시지에 `Co-Authored-By`, `codex`, `claude` 등 도구/저작자 표시를 넣지 않는다. Conventional Commits 형식을 한글로 쓴다.
- `backend/src/main/java/com/runvas/backend/community/`, `backend/src/main/java/com/runvas/backend/admin/` 패키지는 `com.runvas.backend.common.ApiException`/`ErrorCode`를 쓴다. `backend/src/main/java/com/runvas/auth/`, `backend/src/main/java/com/runvas/user/` 패키지는 `com.runvas.global.error.RunvasException`/`ErrorCode`를 쓴다 — 이 저장소에 두 에러 계층이 공존하므로 파일 위치에 맞는 쪽을 그대로 따른다(새로 통합하지 않는다).
- 이 플랜의 Task 3은 `docs/superpowers/plans/2026-09-03-apple-sign-in.md`(Apple 로그인 플랜)의 Task 4에서 만든 `AppleLoginRequest`, `AppleAuthService`를 수정한다. 그 플랜을 먼저 실행하지 않았다면, 해당 파일이 아직 없다는 뜻이므로 Apple 관련 스텝은 건너뛰고 카카오 쪽만 적용한 뒤, Apple 로그인 플랜 실행 시점에 이 플랜의 Task 3 diff를 함께 반영한다.
- 이메일 발송은 실제 SMTP 자격 증명(`MAIL_USERNAME`/`MAIL_PASSWORD` 환경변수)이 배포 환경에 설정되어야 실제로 전송된다 — 이 플랜은 코드와 테스트(mock)까지만 다루고, 운영 환경 SMTP 계정 발급/설정은 범위 밖이다(Task 5 마지막에 사용자에게 안내).

---

## Task 1: docs 갱신 — 이용약관, 데이터 모델, API 계약, 운영 정책

**Files:**
- Create: `docs/terms-of-service.md`
- Modify: `docs/data-model.md` (User 섹션)
- Modify: `docs/api-contract.md` (Auth APIs 섹션 request body, 공통 에러)
- Modify: `docs/admin-dashboard.md`
- Modify: `docs/product-scope.md` (운영자 도구 섹션)
- Modify: `docs/support.md`

**Interfaces:**
- Produces: `termsAgreedAt`(ISO 8601 string, `/auth/kakao`·`/auth/apple` 요청 필수 필드), `bannedAt`(User 내부 필드, API 미노출), `403 FORBIDDEN`(정지된 계정) 에러 계약 — 이후 모든 backend/mobile 작업이 이 필드 이름을 그대로 쓴다.

- [ ] **Step 1: `docs/terms-of-service.md` 신규 작성**

```markdown
# RunSketch 이용약관

시행일: 2026년 9월 3일

## 1. 목적

이 약관은 RunSketch(이하 "서비스")를 이용함에 있어 서비스와 이용자의 권리, 의무 및
책임사항을 정하는 것을 목적으로 합니다.

## 2. 이용자의 의무

이용자는 서비스를 이용하며 다음 행위를 해서는 안 됩니다.

- 타인을 비방, 위협, 희롱하거나 명예를 훼손하는 게시글·댓글 작성
- 음란물, 폭력적 콘텐츠, 혐오 표현, 차별적 표현을 포함한 게시글·댓글 작성
- 스팸, 광고, 불법 정보 유포
- 타인의 개인정보를 본인 동의 없이 게시
- 그 밖에 다른 이용자의 정상적인 서비스 이용을 방해하거나 관계 법령을 위반하는 행위

## 3. 부적절한 콘텐츠 및 이용자에 대한 무관용 원칙

서비스는 위 2조를 위반하는 부적절한 콘텐츠와 이를 반복하는 이용자에 대해
**무관용 원칙(zero tolerance)**을 적용합니다.

- 다른 이용자는 부적절한 게시글·댓글을 신고(flag)할 수 있고, 문제를 일으키는 이용자를
  차단(block)할 수 있습니다. 차단 시 해당 이용자의 콘텐츠는 즉시 내 화면에서 숨겨지며,
  운영자에게도 함께 통지됩니다.
- 운영자는 접수된 신고를 검토하여 **24시간 이내에** 위반 콘텐츠를 삭제하고, 위반 정도에
  따라 해당 콘텐츠를 작성한 이용자의 서비스 이용을 제한(계정 정지)할 수 있습니다.
- 계정이 정지된 이용자는 재가입 여부와 관계없이 서비스 이용이 제한됩니다.

## 4. 약관 동의

서비스에 가입하거나 로그인하려는 이용자는 이 약관에 동의해야 합니다. 동의하지 않으면
서비스를 이용할 수 없습니다.

## 5. 문의

이 약관이나 부적절한 콘텐츠·이용자 신고 처리와 관련한 문의는
[고객 지원](./support.md)의 이메일로 연락해주세요.

## 6. 관련 문서

개인정보 처리에 대해서는 [개인정보처리방침](./privacy-policy.md)을 따릅니다.
```

- [ ] **Step 2: `docs/data-model.md`의 User 섹션(96-108번째 줄)에 두 필드 추가**

기존 표의 `deletedAt` 행 바로 뒤에 아래 두 행을 추가:
```markdown
| `termsAgreedAt` | string | Y | 이용약관 최초 동의 시각(ISO 8601). API 응답에는 노출하지 않는 내부 저장값 |
| `bannedAt` | string \| null | N | 운영자가 신고 처리로 계정을 정지시킨 시각(ISO 8601). 채워져 있으면 로그인이 차단된다는 뜻. API 응답에는 노출하지 않는 내부 저장값 |
```

- [ ] **Step 3: `docs/api-contract.md`의 `POST /auth/kakao` Request Body(1030-1044번째 줄)에 필드 추가**

기존 표에 행 추가:
```markdown
| `termsAgreedAt` | string | Y | 이용자가 [이용약관](./terms-of-service.md) 동의 화면에서 동의를 누른 시각(ISO 8601) |
```

예시 JSON도 갱신:
```json
{
  "provider": "KAKAO",
  "authorizationCode": "kakao_authorization_code",
  "redirectUri": "runvas://auth/kakao",
  "termsAgreedAt": "2026-09-03T08:00:00Z"
}
```

Errors 목록(1070-1074번째 줄)에 추가:
```markdown
- `403 FORBIDDEN`: 운영자에 의해 이용이 제한(정지)된 계정
```

Apple 로그인 플랜이 이미 실행되어 `### POST /auth/apple` 섹션이 있다면, 그 Request Body 표에도 동일하게 `termsAgreedAt` 행을 추가하고 예시 JSON과 Errors에도 동일하게 반영한다.

- [ ] **Step 4: `docs/admin-dashboard.md`에 정지(ban) 액션 문서화**

파일을 열어 기존 "신고 처리"/"삭제"/"기각" 설명 방식을 확인한 뒤, 그 옆에 아래 내용을 추가한다(기존 절 제목 스타일에 맞춰 넣는다):

```markdown
### 신고 삭제 + 작성자 정지

`PENDING` 신고 목록에서 "삭제+정지" 버튼을 누르면, 해당 신고 대상 콘텐츠를 삭제하는 것과
동시에 그 콘텐츠를 작성한 이용자의 계정을 정지시킨다. 정지된 계정은 이후 카카오/Apple
로그인 시 `403 FORBIDDEN`으로 거부된다. 단순 삭제("삭제" 버튼)는 콘텐츠만 지우고 계정은
정지시키지 않는다 — 위반 정도가 가벼운 경우 "삭제"만, 반복·심각한 위반은 "삭제+정지"를
사용한다.
```

- [ ] **Step 5: `docs/product-scope.md`의 "운영자 도구" 섹션(76-81번째 줄) 갱신**

기존:
```markdown
## 운영자 도구

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인하기 위한 내부 전용 관리자 대시보드를
`backend/`에서 제공합니다. 세션 기반 운영자 로그인으로만 접근하며, 읽기 전용(통계 조회, 목록
조회)입니다. 위 핵심 사용자 흐름(1~8)이나 MVP 범위/제외 범위와는 무관한 내부 운영 도구이며,
상세 내용은 `docs/admin-dashboard.md`를 따릅니다.
```

변경:
```markdown
## 운영자 도구

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인하고 신고된 콘텐츠를 처리하기 위한
내부 전용 관리자 대시보드를 `backend/`에서 제공합니다. 세션 기반 운영자 로그인으로만
접근합니다. 대부분 읽기 전용(통계 조회, 목록 조회)이지만, 신고 처리(콘텐츠 삭제, 계정 정지)는
예외적으로 상태를 변경하는 관리자 액션입니다. 위 핵심 사용자 흐름(1~8)이나 MVP 범위/제외
범위와는 무관한 내부 운영 도구이며, 상세 내용은 `docs/admin-dashboard.md`를 따릅니다.
```

- [ ] **Step 6: `docs/support.md`에 신고 처리 관련 안내 추가**

"자주 묻는 질문" 섹션 끝에 추가:
```markdown

### 부적절한 게시글이나 이용자를 신고했는데 언제 처리되나요?
신고 접수 후 24시간 이내에 운영자가 검토하여 위반 콘텐츠를 삭제하고, 필요한 경우 해당
이용자의 계정을 정지합니다. 급한 건은 위 이메일로 직접 알려주시면 더 빠르게 처리해드립니다.
```

- [ ] **Step 7: 커밋**

```bash
git add docs/terms-of-service.md docs/data-model.md docs/api-contract.md docs/admin-dashboard.md docs/product-scope.md docs/support.md
git commit -m "docs: 이용약관 신설 및 신고 처리 정책 문서화"
```

---

## Task 2: Backend — `User.termsAgreedAt`/`bannedAt` + 마이그레이션

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__add_terms_agreed_at_and_banned_at_to_users.sql`
- Modify: `backend/src/main/java/com/runvas/user/domain/User.java`
- Test: `backend/src/test/java/com/runvas/user/domain/UserTest.java` (Apple 플랜 Task 2에서 이미 만들었다면 이어서 추가, 없으면 새로 만든다)

**Interfaces:**
- Produces: `User.isBanned(): boolean`, `User.ban(): void`, `User.getBannedAt(): Instant`, `User.agreeToTerms(Instant at): void`, `User.getTermsAgreedAt(): Instant` — Task 3(로그인 서비스), Task 6(관리자 정지 액션)이 이 메서드들을 호출한다.

- [ ] **Step 1: 마이그레이션 파일 작성**

`backend/src/main/resources/db/migration/V17__add_terms_agreed_at_and_banned_at_to_users.sql`:
```sql
ALTER TABLE users
    ADD COLUMN terms_agreed_at TIMESTAMP,
    ADD COLUMN banned_at TIMESTAMP;
```

- [ ] **Step 2: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/user/domain/UserTest.java`에 추가(파일이 이미 있으면 기존 클래스 안에 메서드만 추가):
```java
    @Test
    void ban_호출하면_isBanned가_true가_된다() {
        User user = User.createKakaoUser("kakao-1", "runner@example.com", "Runner", null);

        assertThat(user.isBanned()).isFalse();
        user.ban();
        assertThat(user.isBanned()).isTrue();
        assertThat(user.getBannedAt()).isNotNull();
    }

    @Test
    void agreeToTerms_최초_한_번만_저장되고_이후_호출은_무시한다() {
        User user = User.createKakaoUser("kakao-2", "runner@example.com", "Runner", null);
        java.time.Instant first = java.time.Instant.parse("2026-09-01T00:00:00Z");
        java.time.Instant second = java.time.Instant.parse("2026-09-02T00:00:00Z");

        user.agreeToTerms(first);
        user.agreeToTerms(second);

        assertThat(user.getTermsAgreedAt()).isEqualTo(first);
    }
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: FAIL — `ban()`, `isBanned()`, `agreeToTerms()` method does not exist

- [ ] **Step 4: `User.java`에 필드/메서드 추가**

`backend/src/main/java/com/runvas/user/domain/User.java:51-52`(`deletedAt` 컬럼 선언) 바로 뒤에 필드 추가:
```java
    @Column
    private Instant termsAgreedAt;

    @Column
    private Instant bannedAt;
```

`getDeletedAt()`(101번째 줄) 바로 뒤에 getter 추가:
```java
    public Instant getTermsAgreedAt() { return termsAgreedAt; }
    public Instant getBannedAt() { return bannedAt; }
```

`restore()` 메서드(111-113번째 줄) 바로 뒤에 메서드 추가:
```java
    public boolean isBanned() {
        return bannedAt != null;
    }

    public void ban() {
        this.bannedAt = Instant.now();
    }

    public void agreeToTerms(Instant at) {
        if (this.termsAgreedAt == null) {
            this.termsAgreedAt = at;
        }
    }
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/resources/db/migration/V17__add_terms_agreed_at_and_banned_at_to_users.sql backend/src/main/java/com/runvas/user/domain/User.java backend/src/test/java/com/runvas/user/domain/UserTest.java
git commit -m "feat(user): 약관 동의 시각과 계정 정지 필드 추가"
```

---

## Task 3: Backend — 로그인 시 약관 동의 필수화 + 정지 계정 차단

**Files:**
- Modify: `backend/src/main/java/com/runvas/auth/dto/KakaoLoginRequest.java`
- Modify: `backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java`
- Modify: `backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java` (Apple 로그인 플랜 실행 후에만)
- Modify: `backend/src/main/java/com/runvas/auth/service/AppleAuthService.java` (Apple 로그인 플랜 실행 후에만)
- Test: `backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java` (없으면 새로 만든다)

**Interfaces:**
- Consumes: `User.agreeToTerms`, `User.isBanned`(Task 2).
- Produces: 로그인 요청에 `termsAgreedAt`(필수) 반영, 정지 계정 로그인 시 `RunvasException(ErrorCode.FORBIDDEN)` — Task 7(모바일)이 이 계약에 맞춰 요청을 보낸다.

- [ ] **Step 1: `KakaoLoginRequest`에 필드 추가**

`backend/src/main/java/com/runvas/auth/dto/KakaoLoginRequest.java` 전체를 아래로 교체:
```java
package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record KakaoLoginRequest(
        @NotBlank String provider,
        @NotBlank String authorizationCode,
        @NotBlank String redirectUri,
        @NotNull Instant termsAgreedAt
) {
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java` (파일이 없으면 신규 생성):
```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KakaoAuthServiceTest {

    @Test
    void login_정지된_계정이면_FORBIDDEN을_던진다() {
        KakaoAuthClient kakaoAuthClient = mock(KakaoAuthClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(kakaoAuthClient.fetchUserInfo("code", "redirect"))
                .thenReturn(new KakaoUserInfo("kakao-sub-1", "runner@example.com", "Runner", null));
        User bannedUser = User.createKakaoUser("kakao-sub-1", "runner@example.com", "Runner", null);
        bannedUser.ban();
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-sub-1"))
                .thenReturn(Optional.of(bannedUser));

        KakaoAuthService service = new KakaoAuthService(kakaoAuthClient, userRepository, jwtProvider);
        KakaoLoginRequest request = new KakaoLoginRequest("KAKAO", "code", "redirect", Instant.now());

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(RunvasException.class);
    }
}
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.KakaoAuthServiceTest"`
Expected: FAIL — `KakaoLoginRequest` 생성자 인자 개수 불일치(컴파일 에러) 또는 정지 체크가 없어 예외가 발생하지 않음

- [ ] **Step 4: `KakaoAuthService.login()` 수정**

`backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java:38-46`을 아래로 교체:
```java
        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.KAKAO,
                kakaoUserInfo.providerUserId()
        );
        existingUser.ifPresent(this::requireNotBanned);
        existingUser.ifPresent(this::restoreIfWithdrawn);
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(kakaoUserInfo));
        loginResult.user().agreeToTerms(request.termsAgreedAt());
        userRepository.save(loginResult.user());
```

`restoreIfWithdrawn` 메서드(51-56번째 줄) 바로 뒤에 메서드 추가:
```java
    private void requireNotBanned(User user) {
        if (user.isBanned()) {
            throw new RunvasException(ErrorCode.FORBIDDEN, "이용이 제한된 계정입니다");
        }
    }
```

파일 상단 import에 `com.runvas.global.error.ErrorCode`가 이미 있는지 확인한다(있다면 그대로 둔다).

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.KakaoAuthServiceTest"`
Expected: PASS

- [ ] **Step 6: (Apple 로그인 플랜을 이미 실행했다면) `AppleLoginRequest`/`AppleAuthService`에 동일하게 반영**

`backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java` 전체를 아래로 교체:
```java
package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        String nickname,
        @NotNull Instant termsAgreedAt
) {
}
```

`backend/src/main/java/com/runvas/auth/service/AppleAuthService.java`의 `login()` 메서드에서, `Optional<User> existingUser = ...` 이후 부분을 아래로 교체:
```java
        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.APPLE,
                appleUserInfo.providerUserId()
        );
        existingUser.ifPresent(this::requireNotBanned);
        existingUser.ifPresent(this::restoreIfWithdrawn);
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(appleUserInfo, request.nickname()));
        loginResult.user().agreeToTerms(request.termsAgreedAt());
        userRepository.save(loginResult.user());
```

`restoreIfWithdrawn` 메서드 바로 뒤에 메서드 추가:
```java
    private void requireNotBanned(User user) {
        if (user.isBanned()) {
            throw new RunvasException(ErrorCode.FORBIDDEN, "이용이 제한된 계정입니다");
        }
    }
```

`AppleAuthService.java` 상단 import에 `com.runvas.global.error.ErrorCode`가 없다면 추가한다.

- [ ] **Step 7: 전체 백엔드 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 `AuthControllerTest`가 `KakaoLoginRequest`를 생성하는 곳이 있다면 `termsAgreedAt` 인자가 빠져 컴파일이 깨질 수 있다. 그런 곳을 모두 찾아 `Instant.now()` 등으로 채운다.

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/dto/KakaoLoginRequest.java backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java
git commit -m "feat(auth): 로그인 시 약관 동의 저장 및 정지 계정 차단"
```

(Apple 관련 파일을 함께 수정했다면 `git add`에 `backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java backend/src/main/java/com/runvas/auth/service/AppleAuthService.java`도 포함한다.)

---

## Task 4: Backend — 콘텐츠 필터링 메커니즘

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/community/ObjectionableContentFilter.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/PostService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/CommentService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`
- Test: `backend/src/test/java/com/runvas/backend/community/ObjectionableContentFilterTest.java`

**Interfaces:**
- Produces: `ObjectionableContentFilter.validate(String... texts)` — 금칙어가 있으면 `ApiException(ErrorCode.VALIDATION_ERROR)`를 던지고, 없으면 아무 일도 하지 않는다. `PostService.create`, `CommentService.create`, `CourseCommentService.create`가 콘텐츠 저장 전에 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/community/ObjectionableContentFilterTest.java`:
```java
package com.runvas.backend.community;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runvas.backend.common.ApiException;
import org.junit.jupiter.api.Test;

class ObjectionableContentFilterTest {

    private final ObjectionableContentFilter filter = new ObjectionableContentFilter();

    @Test
    void validate_금칙어가_없으면_통과한다() {
        assertThatCode(() -> filter.validate("오늘 한강 러닝 코스 공유합니다", "10km 완주했어요"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_금칙어가_포함되면_예외를_던진다() {
        assertThatThrownBy(() -> filter.validate("이 코스 만든 놈 시발 진짜"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_공백으로_우회해도_감지한다() {
        assertThatThrownBy(() -> filter.validate("시 발 이런 코스가"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_null_텍스트는_무시한다() {
        assertThatCode(() -> filter.validate("정상 텍스트", null))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.ObjectionableContentFilterTest"`
Expected: FAIL — `ObjectionableContentFilter` class does not exist

- [ ] **Step 3: `ObjectionableContentFilter` 구현**

`backend/src/main/java/com/runvas/backend/community/ObjectionableContentFilter.java`:
```java
package com.runvas.backend.community;

import com.runvas.backend.common.ApiException;
import com.runvas.backend.common.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ObjectionableContentFilter {

    private static final List<String> BANNED_TERMS = List.of(
            "시발", "씨발", "개새끼", "병신", "지랄", "좆같",
            "fuck", "faggot", "nigger", "kill yourself"
    );

    public void validate(String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String normalized = text.toLowerCase().replaceAll("\\s+", "");
            for (String term : BANNED_TERMS) {
                if (normalized.contains(term)) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "부적절한 콘텐츠가 포함되어 있습니다");
                }
            }
        }
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.ObjectionableContentFilterTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: `PostService.create()`에 필터 적용**

`backend/src/main/java/com/runvas/backend/community/PostService.java`의 `private final BlockRepository blockRepository;`(31번째 줄) 바로 뒤에 필드 추가:
```java
	private final ObjectionableContentFilter objectionableContentFilter;
```

`create()` 메서드에서 `validateAttachedCourse(request.attachedCourseId());` 바로 뒤에 추가:
```java
		objectionableContentFilter.validate(request.title(), request.body());
```

- [ ] **Step 6: `CommentService.create()`에 필터 적용**

`backend/src/main/java/com/runvas/backend/community/CommentService.java`의 `private final BlockRepository blockRepository;`(30번째 줄) 바로 뒤에 필드 추가:
```java
	private final ObjectionableContentFilter objectionableContentFilter;
```

`create()` 메서드에서 `Post post = findPostOrThrow(postId);` 바로 뒤에 추가:
```java
		objectionableContentFilter.validate(request.body());
```

- [ ] **Step 7: `CourseCommentService.create()`에 필터 적용**

`backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`의 `private final BlockRepository blockRepository;`(38번째 줄) 바로 뒤에 필드 추가:
```java
	private final ObjectionableContentFilter objectionableContentFilter;
```

`create(String courseId, String body, String parentCommentId)` 메서드(92번째 줄) 첫 줄에 추가:
```java
		objectionableContentFilter.validate(body);
```

- [ ] **Step 8: 전체 백엔드 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — `PostService`/`CommentService`/`CourseCommentService`를 `new`로 직접 생성하는 기존 테스트가 있다면 생성자 인자에 `objectionableContentFilter`(예: `new ObjectionableContentFilter()`)를 추가해야 컴파일된다.

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/ObjectionableContentFilter.java backend/src/main/java/com/runvas/backend/community/PostService.java backend/src/main/java/com/runvas/backend/community/CommentService.java backend/src/main/java/com/runvas/backend/community/CourseCommentService.java backend/src/test/java/com/runvas/backend/community/ObjectionableContentFilterTest.java
git commit -m "feat(community): 게시글/댓글 작성 시 금칙어 필터링 추가"
```

---

## Task 5: Backend — 신고/차단 발생 시 운영자 이메일 알림

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminNotificationService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/ReportService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/BlockService.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminNotificationServiceTest.java`

**Interfaces:**
- Produces: `AdminNotificationService.notifyNewReport(String targetType, String targetId, String reporterId)`, `AdminNotificationService.notifyBlock(String blockerId, String blockedId)` — `ReportService.report()`와 `BlockService.block()`이 신규 건일 때만 호출한다. 두 메서드 모두 발송 실패를 삼키고(로그만 남기고) 호출자에게 예외를 전파하지 않는다.

- [ ] **Step 1: `build.gradle`에 메일 스타터 추가**

`backend/build.gradle`의 `dependencies { ... }` 블록에서 `io.jsonwebtoken` 관련 줄 근처(33-35번째 줄)에 추가:
```groovy
    implementation 'org.springframework.boot:spring-boot-starter-mail'
```

- [ ] **Step 2: `application.yml`에 메일 설정 추가**

`backend/src/main/resources/application.yml`의 `spring:` 블록 안(`cache:` 설정 34-35번째 줄 뒤)에 추가:
```yaml
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

`runvas:` 블록 안(37번째 줄부터)에 추가:
```yaml
  admin:
    notification-email: ${ADMIN_NOTIFICATION_EMAIL:jaeseung425@gmail.com}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminNotificationServiceTest.java`:
```java
package com.runvas.backend.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AdminNotificationServiceTest {

    @Test
    void notifyNewReport_메일을_발송한다() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AdminNotificationService service = new AdminNotificationService(mailSender, "admin@example.com");

        service.notifyNewReport("posts", "post-1", "reporter-1");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyNewReport_발송이_실패해도_예외를_전파하지_않는다() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailException("smtp down") {}).when(mailSender).send(any(SimpleMailMessage.class));
        AdminNotificationService service = new AdminNotificationService(mailSender, "admin@example.com");

        service.notifyNewReport("posts", "post-1", "reporter-1");
    }

    @Test
    void notifyBlock_메일을_발송한다() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AdminNotificationService service = new AdminNotificationService(mailSender, "admin@example.com");

        service.notifyBlock("blocker-1", "blocked-1");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
```

- [ ] **Step 4: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminNotificationServiceTest"`
Expected: FAIL — `AdminNotificationService` class does not exist / `spring-boot-starter-mail` 의존성 미해결

- [ ] **Step 5: `AdminNotificationService` 구현**

`backend/src/main/java/com/runvas/backend/admin/AdminNotificationService.java`:
```java
package com.runvas.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final JavaMailSender mailSender;
    private final String adminEmail;

    public AdminNotificationService(
            JavaMailSender mailSender,
            @Value("${runvas.admin.notification-email}") String adminEmail
    ) {
        this.mailSender = mailSender;
        this.adminEmail = adminEmail;
    }

    public void notifyNewReport(String targetType, String targetId, String reporterId) {
        send(
                "[Runvas] 새 신고 접수",
                "targetType=%s, targetId=%s, reporterId=%s 신고가 접수되었습니다. 관리자 대시보드(/admin/reports)에서 24시간 이내 처리해주세요."
                        .formatted(targetType, targetId, reporterId)
        );
    }

    public void notifyBlock(String blockerId, String blockedId) {
        send(
                "[Runvas] 사용자 차단 발생",
                "blockerId=%s가 blockedId=%s를 차단했습니다. 차단이 반복되는 사용자는 관리자 대시보드(/admin/users)에서 확인해주세요."
                        .formatted(blockerId, blockedId)
        );
    }

    private void send(String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("관리자 알림 메일 발송 실패: {}", exception.getMessage());
        }
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminNotificationServiceTest"`
Expected: PASS (3 tests)

- [ ] **Step 7: `ReportService.report()`에서 신규 신고 시 알림 호출**

`backend/src/main/java/com/runvas/backend/community/ReportService.java`의 `private final CurrentUserProvider currentUserProvider;`(21번째 줄) 바로 뒤에 필드 추가:
```java
	private final AdminNotificationService adminNotificationService;
```

`import com.runvas.backend.auth.CurrentUserProvider;` 바로 뒤에 import 추가:
```java
import com.runvas.backend.admin.AdminNotificationService;
```

`report()` 메서드의 `Report saved = reportRepository.save(...)` 이후, `return` 직전에 추가:
```java
		adminNotificationService.notifyNewReport(targetTypePathValue, targetId, reporterId);
```

- [ ] **Step 8: `BlockService.block()`에서 신규 차단 시 알림 호출**

`backend/src/main/java/com/runvas/backend/community/BlockService.java`의 `private final CurrentUserProvider currentUserProvider;`(24번째 줄) 바로 뒤에 필드 추가:
```java
	private final AdminNotificationService adminNotificationService;
```

`import com.runvas.backend.auth.CurrentUserProvider;` 바로 뒤에 import 추가:
```java
import com.runvas.backend.admin.AdminNotificationService;
```

`block()` 메서드에서 `BlockResponse response = ...` 다음, `return` 직전에 추가:
```java
		if (existing.isEmpty()) {
			adminNotificationService.notifyBlock(blockerId, blockedId);
		}
```

- [ ] **Step 9: 전체 백엔드 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — `ReportService`/`BlockService`를 `new`로 직접 생성하는 기존 테스트가 있다면 생성자 인자에 mock `AdminNotificationService`를 추가해야 컴파일된다.

- [ ] **Step 10: 커밋**

```bash
git add backend/build.gradle backend/src/main/resources/application.yml backend/src/main/java/com/runvas/backend/admin/AdminNotificationService.java backend/src/main/java/com/runvas/backend/community/ReportService.java backend/src/main/java/com/runvas/backend/community/BlockService.java backend/src/test/java/com/runvas/backend/admin/AdminNotificationServiceTest.java
git commit -m "feat(admin): 신고/차단 발생 시 운영자 이메일 알림 추가"
```

- [ ] **Step 11: 사용자에게 안내**

이 시점에 실행자는 사용자에게 "배포 환경에 `MAIL_USERNAME`/`MAIL_PASSWORD`(Gmail이면 앱 비밀번호) 환경변수를 설정해야 실제로 메일이 발송된다"는 점을 안내한다. 설정 전까지는 `notifyNewReport`/`notifyBlock`이 조용히 실패(로그만 남김)하며 신고/차단 자체는 정상 동작한다.

---

## Task 6: Backend — 관리자 "삭제+정지" 액션

**Files:**
- Modify: `backend/src/main/java/com/runvas/backend/community/PostService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/CommentService.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`
- Modify: `backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`
- Modify: `backend/src/main/java/com/runvas/backend/admin/AdminReportController.java`
- Modify: `backend/src/main/resources/templates/admin/reports.html`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java` (없으면 새로 만든다)

**Interfaces:**
- Consumes: `User.ban()`(Task 2).
- Produces: `PostService.getAuthorId(String)`, `CommentService.getAuthorId(String)`, `CourseCommentService.getAuthorId(String)`, `AdminReportActionService.resolveAndBan(String reportId)` — `AdminReportController`가 `/admin/reports/{reportId}/resolve-and-ban`에서 호출한다.

- [ ] **Step 1: 각 콘텐츠 서비스에 `getAuthorId` 추가**

`backend/src/main/java/com/runvas/backend/community/PostService.java`에 (기존 `getById` 메서드 근처에) 추가:
```java
	public String getAuthorId(String postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "게시글이 없습니다"))
				.getAuthorId();
	}
```

`backend/src/main/java/com/runvas/backend/community/CommentService.java`에 추가:
```java
	public String getAuthorId(String commentId) {
		return commentRepository.findById(commentId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "댓글이 없습니다"))
				.getAuthorId();
	}
```

`backend/src/main/java/com/runvas/backend/community/CourseCommentService.java`에 추가:
```java
	public String getAuthorId(String courseCommentId) {
		return courseCommentRepository.findById(courseCommentId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "댓글이 없습니다"))
				.getAuthorId();
	}
```

세 파일 모두 `com.runvas.backend.common.ApiException`, `com.runvas.backend.common.ErrorCode` import가 이미 있는지 확인한다(있다면 그대로 둔다).

- [ ] **Step 2: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java` (없으면 신규 생성 — 기존 파일이 있다면 아래 메서드만 추가):
```java
package com.runvas.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.runvas.backend.community.CommentService;
import com.runvas.backend.community.CourseCommentService;
import com.runvas.backend.community.PostService;
import com.runvas.backend.community.Report;
import com.runvas.backend.community.ReportRepository;
import com.runvas.backend.community.ReportStatus;
import com.runvas.backend.community.ReportTargetType;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminReportActionServiceTest {

    @Test
    void resolveAndBan_콘텐츠를_삭제하고_작성자를_정지한다() {
        ReportRepository reportRepository = mock(ReportRepository.class);
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        CourseCommentService courseCommentService = mock(CourseCommentService.class);
        UserRepository userRepository = mock(UserRepository.class);

        UUID authorUuid = UUID.randomUUID();
        Report report = new Report("reporter-1", ReportTargetType.POST, "post-1", null, null);
        when(reportRepository.findById("report-1")).thenReturn(Optional.of(report));
        when(reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST, "post-1", ReportStatus.PENDING))
                .thenReturn(List.of(report));
        when(postService.getAuthorId("post-1")).thenReturn(authorUuid.toString());
        User author = User.createKakaoUser(authorUuid.toString(), null, "Author", null);
        when(userRepository.findById(authorUuid)).thenReturn(Optional.of(author));

        AdminReportActionService service = new AdminReportActionService(
                reportRepository, postService, commentService, courseCommentService, userRepository);

        service.resolveAndBan("report-1");

        assertThat(author.isBanned()).isTrue();
    }
}
```

주의: `Report` 생성자 시그니처(`backend/src/main/java/com/runvas/backend/community/Report.java`)가 위와 다르면(필드 순서/개수) 실제 생성자에 맞춰 테스트를 고친다.

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportActionServiceTest"`
Expected: FAIL — `resolveAndBan` method does not exist, 생성자 인자 개수 불일치

- [ ] **Step 4: `AdminReportActionService`에 `resolveAndBan` 추가**

`backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java` 전체를 아래로 교체:
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
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.util.UUID;
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
	private final UserRepository userRepository;

	@Transactional
	public void resolve(String reportId) {
		Report report = findOrThrow(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			return;
		}

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
	public void resolveAndBan(String reportId) {
		Report report = findOrThrow(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			return;
		}

		String authorId = switch (report.getTargetType()) {
			case POST -> postService.getAuthorId(report.getTargetId());
			case COMMENT -> commentService.getAuthorId(report.getTargetId());
			case COURSE_COMMENT -> courseCommentService.getAuthorId(report.getTargetId());
		};

		resolve(reportId);

		userRepository.findById(UUID.fromString(authorId)).ifPresent(user -> {
			user.ban();
			userRepository.save(user);
		});
	}

	@Transactional
	public void dismiss(String reportId) {
		Report report = findOrThrow(reportId);
		if (report.getStatus() != ReportStatus.PENDING) {
			return;
		}
		report.dismiss();
	}

	private Report findOrThrow(String reportId) {
		return reportRepository.findById(reportId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "신고가 없습니다"));
	}
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportActionServiceTest"`
Expected: PASS

- [ ] **Step 6: `AdminReportController`에 엔드포인트 추가**

`backend/src/main/java/com/runvas/backend/admin/AdminReportController.java`의 `resolve()` 메서드(42-46번째 줄) 바로 뒤에 추가:
```java
	@PostMapping("/admin/reports/{reportId}/resolve-and-ban")
	String resolveAndBan(@PathVariable String reportId) {
		adminReportActionService.resolveAndBan(reportId);
		return "redirect:/admin/reports";
	}
```

- [ ] **Step 7: `reports.html` 템플릿에 버튼 추가**

`backend/src/main/resources/templates/admin/reports.html:56-59`("삭제" 버튼 폼) 바로 뒤에 추가:
```html
                <form method="post" th:if="${status.name() == 'PENDING'}"
                      th:action="@{'/admin/reports/' + ${report.id()} + '/resolve-and-ban'}" style="display:inline"
                      onsubmit="return confirm('콘텐츠를 삭제하고 작성자 계정을 정지합니다. 계속할까요?');">
                    <button type="submit" class="btn">삭제+정지</button>
                </form>
```

- [ ] **Step 8: 전체 백엔드 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/runvas/backend/community/PostService.java backend/src/main/java/com/runvas/backend/community/CommentService.java backend/src/main/java/com/runvas/backend/community/CourseCommentService.java backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java backend/src/main/java/com/runvas/backend/admin/AdminReportController.java backend/src/main/resources/templates/admin/reports.html backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java
git commit -m "feat(admin): 신고 콘텐츠 삭제와 작성자 계정 정지를 함께 처리하는 액션 추가"
```

---

## Task 7: Mobile — 이용약관 동의 게이트

**Files:**
- Create: `mobile/src/components/TermsAgreementModal.tsx`
- Modify: `mobile/src/contexts/AuthContext.tsx`
- Modify: `mobile/src/components/LoginPromptModal.tsx`
- Modify: `mobile/src/services/authApi.ts`

**Interfaces:**
- Consumes: `expo-secure-store`(기존 패턴), `postAuthKakao`/`postAuthApple`(Task 3의 `termsAgreedAt` 필드 반영).
- Produces: 로그인 시도 전 약관 동의 여부를 로컬에 저장하는 `TERMS_AGREED_KEY` 스토리지 키, `AuthContextValue.hasAgreedToTerms: boolean`, `AuthContextValue.agreeToTerms: () => Promise<void>`.

- [ ] **Step 1: `authApi.ts`의 로그인 함수에 `termsAgreedAt` 반영**

`mobile/src/services/authApi.ts`의 `postAuthKakao` 함수를 아래로 교체:
```ts
export async function postAuthKakao(
  authorizationCode: string,
  redirectUri: string,
  termsAgreedAt: string,
): Promise<AuthResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/kakao`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider: 'KAKAO',
      authorizationCode,
      redirectUri,
      termsAgreedAt,
    }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as AuthResponse;
}
```

Apple 로그인 플랜을 이미 실행했다면, `postAuthApple` 함수에도 동일하게 `termsAgreedAt: string` 파라미터를 추가하고 요청 body에 포함시킨다.

- [ ] **Step 2: `TermsAgreementModal.tsx` 작성**

`mobile/src/components/TermsAgreementModal.tsx`:
```tsx
import React from 'react';
import { Modal, View, Text, TouchableOpacity, StyleSheet, Linking } from 'react-native';
import { Colors } from '../constants/theme';

const TERMS_URL = 'https://github.com/runvas/runvas/blob/main/docs/terms-of-service.md';

interface Props {
  visible: boolean;
  onAgree: () => void;
  onCancel: () => void;
}

export default function TermsAgreementModal({ visible, onAgree, onCancel }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>이용약관 동의</Text>
          <Text style={styles.body}>
            RunSketch는 부적절한 콘텐츠와 이용자에 대해 무관용 원칙을 적용합니다. 서비스를
            이용하려면 이용약관에 동의해야 합니다.
          </Text>
          <TouchableOpacity onPress={() => Linking.openURL(TERMS_URL)}>
            <Text style={styles.link}>이용약관 전문 보기</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.agreeButton} onPress={onAgree} activeOpacity={0.8}>
            <Text style={styles.agreeButtonLabel}>동의하고 계속하기</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={onCancel} activeOpacity={0.7}>
            <Text style={styles.cancelLabel}>취소</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 14,
    padding: 20,
  },
  title: { fontSize: 16, fontWeight: '700', color: Colors.gray900, marginBottom: 8 },
  body: { fontSize: 13, color: Colors.gray500, marginBottom: 12, lineHeight: 18 },
  link: { fontSize: 13, color: Colors.gray900, fontWeight: '600', marginBottom: 16 },
  agreeButton: {
    backgroundColor: Colors.gray900,
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 8,
  },
  agreeButtonLabel: { fontSize: 13, fontWeight: '700', color: Colors.white },
  cancelLabel: { textAlign: 'center', color: Colors.gray500, marginTop: 4, fontWeight: '600' },
});
```

`Colors` 상수(`mobile/src/constants/theme.ts`)에 `white`/`gray900`/`gray500`가 이미 있는지 확인한다(`LoginPromptModal.tsx`가 이미 이 이름들을 쓰고 있으므로 있을 것이다).

- [ ] **Step 3: `AuthContext.tsx`에 동의 상태 관리 추가**

`mobile/src/contexts/AuthContext.tsx:15-16`(`TOKEN_KEY`, `USER_KEY` 상수) 바로 뒤에 추가:
```ts
const TERMS_AGREED_AT_KEY = 'runvas_terms_agreed_at';
```

`AuthContextValue` 인터페이스에 추가:
```ts
  hasAgreedToTerms: boolean;
  isTermsModalVisible: boolean;
  requestTermsAgreement: () => void;
  agreeToTerms: () => Promise<void>;
  cancelTermsAgreement: () => void;
```

`AuthProvider` 함수 본문 상단(`isKakaoWebViewVisible` state 선언 46번째 줄) 근처에 state 추가:
```ts
  const [termsAgreedAt, setTermsAgreedAt] = useState<string | null>(null);
  const [isTermsModalVisible, setIsTermsModalVisible] = useState(false);
```

초기화 `useEffect`(49-72번째 줄)의 `Promise.all([...])`에 저장된 동의 시각도 함께 불러오도록 수정 — `SecureStore.getItemAsync(TOKEN_KEY)`, `SecureStore.getItemAsync(USER_KEY)` 옆에 `SecureStore.getItemAsync(TERMS_AGREED_AT_KEY)`를 추가하고, 결과 배열 구조분해에 `storedTermsAgreedAt`을 추가한 뒤 `if (storedTermsAgreedAt) setTermsAgreedAt(storedTermsAgreedAt);`를 넣는다(기존 토큰 복원 로직과 같은 블록, 실패해도 무시하는 catch 안에 있으면 된다).

`kakaoLogin` 함수(74-83번째 줄) 시작 부분을 아래로 교체 — 약관 동의가 안 되어 있으면 로그인 대신 약관 모달을 띄운다:
```ts
  const kakaoLogin = useCallback(() => {
    if (!termsAgreedAt) {
      setIsTermsModalVisible(true);
      return;
    }
    if (!KAKAO_REST_API_KEY) {
      setLoginError('EXPO_PUBLIC_KAKAO_APP_KEY가 설정되지 않았습니다.');
      return;
    }
    setLoginError(null);
    setIsLoggingIn(true);
    setIsLoginModalVisible(false);
    setIsKakaoWebViewVisible(true);
  }, [termsAgreedAt]);
```

`submitKakaoCode` 함수(85-102번째 줄) 안의 `postAuthKakao(code, KAKAO_REDIRECT_URI)` 호출을 아래로 교체:
```ts
      const result = await postAuthKakao(code, KAKAO_REDIRECT_URI, termsAgreedAt!);
```

Apple 로그인 플랜을 이미 실행했다면 `appleLogin`의 `postAuthApple(credential.identityToken, nickname)` 호출도 `postAuthApple(credential.identityToken, nickname, termsAgreedAt!)`로 바꾸고, `appleLogin`도 `kakaoLogin`과 동일하게 함수 맨 앞에 `if (!termsAgreedAt) { setIsTermsModalVisible(true); return; }`를 추가한다.

`closeLoginModal` 함수(145-147번째 줄) 바로 뒤에 함수 추가:
```ts
  const requestTermsAgreement = useCallback(() => {
    setIsTermsModalVisible(true);
  }, []);

  const agreeToTerms = useCallback(async () => {
    const now = new Date().toISOString();
    await SecureStore.setItemAsync(TERMS_AGREED_AT_KEY, now);
    setTermsAgreedAt(now);
    setIsTermsModalVisible(false);
    setIsLoginModalVisible(true);
  }, []);

  const cancelTermsAgreement = useCallback(() => {
    setIsTermsModalVisible(false);
  }, []);
```

`value`를 만드는 `useMemo`의 객체 본문과 의존성 배열 양쪽에 `hasAgreedToTerms: termsAgreedAt !== null`(계산 값이므로 객체 본문에는 `hasAgreedToTerms: !!termsAgreedAt` 형태로, 의존성 배열에는 `termsAgreedAt`을 추가), `isTermsModalVisible`, `requestTermsAgreement`, `agreeToTerms`, `cancelTermsAgreement`를 추가한다.

- [ ] **Step 4: `LoginPromptModal.tsx`에 약관 모달 연결**

`mobile/src/components/LoginPromptModal.tsx`의 import에 추가:
```tsx
import TermsAgreementModal from './TermsAgreementModal';
```

`useAuth()` 구조분해에 `isTermsModalVisible`, `agreeToTerms`, `cancelTermsAgreement`를 추가하고, 컴포넌트가 반환하는 최상위 `<Modal>` 형제로 아래를 추가:
```tsx
      <TermsAgreementModal
        visible={isTermsModalVisible}
        onAgree={agreeToTerms}
        onCancel={cancelTermsAgreement}
      />
```

(즉 `return (...)`이 `<>...</>` 프래그먼트로 두 `Modal`을 함께 감싸도록 바꾼다.)

- [ ] **Step 5: 타입 체크 및 번들 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

Run: `cd mobile && npx expo start &` 후 `curl "http://localhost:8081/index.bundle?platform=ios&dev=true"`
Expected: HTTP 200

- [ ] **Step 6: 커밋**

```bash
git add mobile/src/components/TermsAgreementModal.tsx mobile/src/contexts/AuthContext.tsx mobile/src/components/LoginPromptModal.tsx mobile/src/services/authApi.ts
git commit -m "feat(mobile): 로그인 전 이용약관 동의 게이트 추가"
```

---

## Task 8: Mobile — 지원 화면 노출 + 계정 정지 에러 메시지

**Files:**
- Create: `mobile/src/screens/SupportScreen.tsx`
- Modify: 설정/프로필 화면(예: `mobile/src/screens/ProfileScreen.tsx` 또는 설정 메뉴가 있는 화면 — 아래 Step 1에서 실제 파일을 찾는다)
- Modify: 네비게이션 설정 파일(예: `mobile/App.tsx` 또는 `mobile/src/navigation/*` — 아래 Step 1에서 실제 파일을 찾는다)

**Interfaces:**
- Produces: `SupportScreen` — 설정 메뉴에서 이 화면으로 이동하는 진입점 하나를 추가한다.

- [ ] **Step 1: 설정 화면과 네비게이터 위치 확인**

Run: `cd mobile && grep -rl "회원 탈퇴\|withdraw" src/screens`

이 커맨드 결과로 나오는 화면 파일(탈퇴 메뉴가 있는 설정류 화면일 가능성이 높다)을 열어 메뉴 항목이 어떤 컴포넌트/스타일로 나열되어 있는지 확인하고, `mobile/App.tsx`(또는 `src/navigation/` 아래 파일)를 열어 화면 등록 패턴(`Stack.Screen name=... component=...` 등)을 확인한다. 이후 Step은 그 패턴을 그대로 따라간다.

- [ ] **Step 2: `SupportScreen.tsx` 작성**

`mobile/src/screens/SupportScreen.tsx`:
```tsx
import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, Linking, StyleSheet } from 'react-native';
import { Colors } from '../constants/theme';

const SUPPORT_EMAILS = ['tkfdkskarl78@gmail.com', 'jaeseung425@gmail.com'];

export default function SupportScreen() {
  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>고객 지원</Text>
      <Text style={styles.body}>
        RunSketch 이용 중 문의사항이나 부적절한 콘텐츠·이용자 신고 관련 문의는 아래 이메일로
        연락해주세요. 신고 접수 후 24시간 이내에 검토합니다.
      </Text>
      {SUPPORT_EMAILS.map((email) => (
        <TouchableOpacity key={email} onPress={() => Linking.openURL(`mailto:${email}`)}>
          <Text style={styles.email}>{email}</Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.white },
  content: { padding: 20 },
  title: { fontSize: 18, fontWeight: '700', color: Colors.gray900, marginBottom: 12 },
  body: { fontSize: 13, color: Colors.gray500, lineHeight: 18, marginBottom: 16 },
  email: { fontSize: 14, fontWeight: '600', color: Colors.gray900, marginBottom: 8 },
});
```

- [ ] **Step 3: 설정 화면에 메뉴 항목 추가, 네비게이터에 화면 등록**

Step 1에서 찾은 설정 화면 파일에 "고객 지원" 메뉴 항목을 기존 메뉴 항목(예: 회원 탈퇴)과 같은 스타일로 추가하고, `onPress`에서 네비게이션으로 `SupportScreen`으로 이동시킨다. Step 1에서 찾은 네비게이터 파일에도 기존 화면 등록 패턴 그대로 `SupportScreen`을 추가한다. (실제 코드는 이 저장소의 네비게이션 라이브러리와 파일 구조에 따라 달라지므로, Step 1에서 확인한 기존 화면 등록 한 건을 그대로 복사해 이름만 바꾼다.)

- [ ] **Step 4: `AuthContext.tsx`에서 403 에러 메시지 사람이 읽을 수 있게 처리**

`mobile/src/utils/apiError.ts`를 열어 `parseApiErrorMessage`가 백엔드 에러 응답의 `message` 필드를 그대로 쓰는지 확인한다. 그렇다면(백엔드가 이미 "이용이 제한된 계정입니다"라는 한글 메시지를 내려주므로) 추가 처리가 필요 없다 — 이 스텝은 그 사실을 확인만 하고 넘어간다. 만약 `parseApiErrorMessage`가 상태 코드별로 고정 문구를 덮어쓰는 구조라면, `403` 케이스에서 백엔드 `message`를 그대로 노출하도록 예외 처리를 추가한다.

- [ ] **Step 5: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 6: 커밋**

```bash
git add mobile/src/screens/SupportScreen.tsx
git commit -m "feat(mobile): 앱 내 고객 지원 화면 추가"
```

(Step 3에서 수정한 설정 화면/네비게이터 파일, Step 4에서 수정했다면 `apiError.ts`도 함께 `git add`한다.)

---

## Task 9: 최종 검증

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 모바일 타입 체크 및 번들 확인**

Run: `cd mobile && npx tsc --noEmit`
Run: `cd mobile && npx expo start &` 후 `curl "http://localhost:8081/index.bundle?platform=ios&dev=true"`
Expected: 에러 없음, HTTP 200

- [ ] **Step 3: `docs/api-contract.md` 예시와 실제 구현 필드 일치 확인**

`termsAgreedAt` 필드명이 `KakaoLoginRequest`/(있다면)`AppleLoginRequest`, `docs/api-contract.md`, 모바일 `authApi.ts` 세 곳 모두 동일한지 다시 확인한다.

- [ ] **Step 4: 수동 시나리오 확인**

로컬 백엔드/모바일을 띄운 상태에서: (1) 최초 실행 시 로그인 버튼을 누르면 약관 동의 모달이 먼저 뜨는지, (2) 동의 후 카카오 로그인이 정상 진행되는지, (3) 관리자 대시보드(`/admin/reports`)에서 "삭제+정지" 버튼을 누르면 해당 작성자가 이후 로그인 시 거부되는지 직접 확인한다.

- [ ] **Step 5: 사용자에게 스크린 레코딩 안내**

Apple이 요구한 대로, 다음 세 가지를 보여주는 실기기 화면 녹화를 준비해서 App Store Connect 재제출 시 첨부해야 한다는 점을 사용자에게 안내한다: (1) 가입/로그인 전 이용약관 동의 화면, (2) 부적절한 콘텐츠 신고 메커니즘, (3) 이용자 차단 메커니즘.
