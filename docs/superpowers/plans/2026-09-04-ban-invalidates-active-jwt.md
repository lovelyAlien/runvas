# 정지된 계정의 발급된 JWT 즉시 무효화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**배경:** `feat/ugc-safety-compliance`([#72](https://github.com/lovelyAlien/runvas/pull/72)) 최종 리뷰에서 발견:
관리자가 `AdminReportActionService.resolveAndBan()`으로 사용자를 정지시키면 이후 새 로그인은
`requireNotBanned()`가 막지만, 이미 발급된 JWT는 `JwtAuthenticationFilter`가 토큰 블랙리스트만 확인하고
`User.bannedAt`을 보지 않아 만료 전까지(기본 3600초) 계속 유효했다.

**Goal:** 정지 시점 이후 그 사용자가 보유한 모든 기존 JWT(발급 시점·기기 무관)가 다음 요청부터 즉시
거부되게 한다.

**Architecture:** 이미 매 요청 Redis를 조회하는 `TokenBlacklistService`(로그아웃 시 토큰 단위 블랙리스트)를
사용자 단위로 확장한다. `banUser(userId)`가 `auth:banned-user:<userId>` 키를 `JwtProvider`의
`expirationSeconds`만큼의 TTL로 Redis에 쓰고, `JwtAuthenticationFilter`가 매 요청마다 이 키도 함께
확인한다. `AdminReportActionService.resolveAndBan()`이 `user.ban()` 직후 이 메서드를 호출한다. DB 조회를
인증 핫패스에 추가하지 않고, 기기/세션별 토큰 추적도 필요 없다. 설계 상세는
`docs/superpowers/specs/2026-09-04-ban-invalidates-active-jwt-design.md` 참고.

**Tech Stack:** Spring Boot, JUnit5, Mockito, AssertJ, Spring Data Redis(`StringRedisTemplate`).

## Global Constraints

- 커밋 메시지에 `Co-Authored-By`, `codex`, `claude` 등 도구/저작자 표시를 넣지 않는다. Conventional
  Commits 형식을 한글로 쓴다.
- `backend/src/main/java/com/runvas/backend/admin/` 패키지는 `com.runvas.backend.common.ApiException`/
  `ErrorCode`를 쓰고, `backend/src/main/java/com/runvas/auth/`, `backend/src/main/java/com/runvas/global/`
  패키지는 그 패키지의 기존 관례를 그대로 따른다(이번 변경은 새 예외 타입을 도입하지 않는다).
- API 계약(`docs/api-contract.md`)이나 응답 필드는 바뀌지 않는다 — 문서 변경(`docs/admin-dashboard.md`)은
  이미 별도 커밋(`67bb3b5`)으로 반영되어 있으므로 이 플랜에서는 다시 건드리지 않는다.
- Gradle 실행: 반드시 `backend/` 디렉터리에서 `./gradlew`로 실행한다.

---

## Task 1: `JwtProvider`에 설정된 만료 초 노출

**Files:**
- Modify: `backend/src/main/java/com/runvas/auth/service/JwtProvider.java`
- Test: `backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java`

**Interfaces:**
- Produces: `JwtProvider.getExpirationSeconds(): long` — Task 2에서 `TokenBlacklistService`가 이 값을
  Redis 키 TTL로 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java`의 마지막 `@Test` 메서드
(`returnsTokenExpirationInstant`) 뒤, 클래스를 닫는 `}` 앞에 추가:

```java

    @Test
    void returnsConfiguredExpirationSeconds() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);

        assertThat(jwtProvider.getExpirationSeconds()).isEqualTo(3600L);
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.JwtProviderTest"`
Expected: FAIL — `cannot find symbol: method getExpirationSeconds()`

- [ ] **Step 3: 최소 구현 작성**

`backend/src/main/java/com/runvas/auth/service/JwtProvider.java`에서 `getExpiration` 메서드 바로 뒤에
추가:

```java

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.JwtProviderTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/service/JwtProvider.java backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java
git commit -m "feat(auth): JwtProvider가 설정된 만료 초를 노출하도록 변경"
```

---

## Task 2: `TokenBlacklistService`에 유저 단위 정지 마커 추가

**Files:**
- Modify: `backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java`
- Test: `backend/src/test/java/com/runvas/auth/service/TokenBlacklistServiceTest.java`

**Interfaces:**
- Consumes: `JwtProvider.getExpirationSeconds(): long` (Task 1)
- Produces: `TokenBlacklistService.banUser(UUID userId): void`,
  `TokenBlacklistService.isUserBanned(UUID userId): boolean` — Task 3(`JwtAuthenticationFilter`)과
  Task 4(`AdminReportActionService`)가 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/TokenBlacklistServiceTest.java` 상단 import 블록에
`java.util.UUID` 추가:

```java
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
```

마지막 `@Test` 메서드(`isBlacklistedReturnsFalseWhenKeyMissing`) 뒤, 클래스를 닫는 `}` 앞에 추가:

```java

    @Test
    void banUserSetsMarkerWithConfiguredExpirationTtl() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtProvider.getExpirationSeconds()).thenReturn(3600L);

        tokenBlacklistService.banUser(userId);

        verify(valueOperations).set(
                eq("auth:banned-user:" + userId), eq("1"), eq(Duration.ofSeconds(3600)));
    }

    @Test
    void isUserBannedReturnsTrueWhenKeyExists() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.hasKey("auth:banned-user:" + userId)).thenReturn(true);

        assertThat(tokenBlacklistService.isUserBanned(userId)).isTrue();
    }

    @Test
    void isUserBannedReturnsFalseWhenKeyMissing() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.hasKey("auth:banned-user:" + userId)).thenReturn(false);

        assertThat(tokenBlacklistService.isUserBanned(userId)).isFalse();
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.TokenBlacklistServiceTest"`
Expected: FAIL — `cannot find symbol: method banUser`, `method isUserBanned`

- [ ] **Step 3: 최소 구현 작성**

`backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java` 전체를 다음으로 교체:

```java
package com.runvas.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklist:";
    private static final String USER_BAN_KEY_PREFIX = "auth:banned-user:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProvider jwtProvider;

    public TokenBlacklistService(StringRedisTemplate redisTemplate, JwtProvider jwtProvider) {
        this.redisTemplate = redisTemplate;
        this.jwtProvider = jwtProvider;
    }

    public void blacklist(String token) {
        Instant expiresAt = jwtProvider.getExpiration(token);
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", remaining);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }

    public void banUser(UUID userId) {
        redisTemplate.opsForValue().set(
                USER_BAN_KEY_PREFIX + userId, "1", Duration.ofSeconds(jwtProvider.getExpirationSeconds()));
    }

    public boolean isUserBanned(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(USER_BAN_KEY_PREFIX + userId));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.TokenBlacklistServiceTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java backend/src/test/java/com/runvas/auth/service/TokenBlacklistServiceTest.java
git commit -m "feat(auth): TokenBlacklistService에 유저 단위 정지 마커 추가"
```

---

## Task 3: `JwtAuthenticationFilter`가 정지된 유저의 토큰을 거부

**Files:**
- Modify: `backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java`
- Create: `backend/src/test/java/com/runvas/global/security/JwtAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `TokenBlacklistService.isUserBanned(UUID userId): boolean` (Task 2)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/global/security/JwtAuthenticationFilterTest.java` 신규 생성:

```java
package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtProvider, tokenBlacklistService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenTokenValidAndUserNotBanned() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("valid-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("valid-token")).thenReturn(false);
        when(tokenBlacklistService.isUserBanned(userId)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clearsContextWhenUserIsBannedEvenIfTokenNotBlacklisted() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("valid-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("valid-token")).thenReturn(false);
        when(tokenBlacklistService.isUserBanned(userId)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clearsContextWhenTokenBlacklistedRegardlessOfBanStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("blacklisted-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("blacklisted-token")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blacklisted-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.global.security.JwtAuthenticationFilterTest"`
Expected: FAIL — `clearsContextWhenUserIsBannedEvenIfTokenNotBlacklisted`가 실패
(`Authentication`이 채워져 있음, `isUserBanned`를 아직 확인하지 않으므로)

- [ ] **Step 3: 최소 구현 작성**

`backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java`에서 아래 블록을:

```java
                UUID userId = jwtProvider.parseUserId(token);
                if (tokenBlacklistService.isBlacklisted(token)) {
                    SecurityContextHolder.clearContext();
                } else {
```

다음으로 교체:

```java
                UUID userId = jwtProvider.parseUserId(token);
                if (tokenBlacklistService.isBlacklisted(token) || tokenBlacklistService.isUserBanned(userId)) {
                    SecurityContextHolder.clearContext();
                } else {
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.global.security.JwtAuthenticationFilterTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java backend/src/test/java/com/runvas/global/security/JwtAuthenticationFilterTest.java
git commit -m "feat(auth): 정지된 유저의 기존 발급 토큰을 인증 필터에서 거부"
```

---

## Task 4: 정지 처리 시 유저 단위 마커 등록

**Files:**
- Modify: `backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`
- Modify: `backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java`

**Interfaces:**
- Consumes: `TokenBlacklistService.banUser(UUID userId): void` (Task 2)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java`에서 import 블록의
```java
import com.runvas.backend.community.ReportTargetType;
import com.runvas.user.domain.User;
```
를
```java
import com.runvas.backend.community.ReportTargetType;
import com.runvas.auth.service.TokenBlacklistService;
import com.runvas.user.domain.User;
```
로 교체.

필드/생성자 블록:
```java
	private final ReportRepository reportRepository = mock(ReportRepository.class);
	private final PostService postService = mock(PostService.class);
	private final CommentService commentService = mock(CommentService.class);
	private final CourseCommentService courseCommentService = mock(CourseCommentService.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final AdminReportActionService adminReportActionService = new AdminReportActionService(
			reportRepository, postService, commentService, courseCommentService, userRepository);
```
를
```java
	private final ReportRepository reportRepository = mock(ReportRepository.class);
	private final PostService postService = mock(PostService.class);
	private final CommentService commentService = mock(CommentService.class);
	private final CourseCommentService courseCommentService = mock(CourseCommentService.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
	private final AdminReportActionService adminReportActionService = new AdminReportActionService(
			reportRepository, postService, commentService, courseCommentService, userRepository,
			tokenBlacklistService);
```
로 교체.

`resolveAndBanDeletesContentAndBansAuthor` 테스트 본문:
```java
		adminReportActionService.resolveAndBan("report-5");

		verify(postService).getAuthorId("post-1");
		verify(postService).deleteAsAdmin("post-1");
		verify(userRepository).save(author);
		assertThat(author.isBanned()).isTrue();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}
```
를
```java
		adminReportActionService.resolveAndBan("report-5");

		verify(postService).getAuthorId("post-1");
		verify(postService).deleteAsAdmin("post-1");
		verify(userRepository).save(author);
		verify(tokenBlacklistService).banUser(authorUuid);
		assertThat(author.isBanned()).isTrue();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}
```
로 교체.

`resolveAndBanFetchesAuthorBeforeDeletingContent` 테스트 본문의
```java
		var inOrder = org.mockito.Mockito.inOrder(commentService);
		inOrder.verify(commentService).getAuthorId("comment-1");
		inOrder.verify(commentService).deleteAsAdmin("comment-1");
		verify(userRepository, never()).save(any());
	}
```
를
```java
		var inOrder = org.mockito.Mockito.inOrder(commentService);
		inOrder.verify(commentService).getAuthorId("comment-1");
		inOrder.verify(commentService).deleteAsAdmin("comment-1");
		verify(userRepository, never()).save(any());
		verify(tokenBlacklistService, never()).banUser(any());
	}
```
로 교체.

`resolveAndBanOnAlreadyDeletedContentResolvesReportWithoutBanning` 테스트 본문의
```java
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		verify(postService).deleteAsAdmin("post-1");
		verify(userRepository, never()).findById(any());
		verify(userRepository, never()).save(any());
	}
```
를
```java
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		verify(postService).deleteAsAdmin("post-1");
		verify(userRepository, never()).findById(any());
		verify(userRepository, never()).save(any());
		verify(tokenBlacklistService, never()).banUser(any());
	}
```
로 교체.

`resolveAndBanOnAlreadyResolvedReportIsNoOp` 테스트 본문의
```java
		verify(postService, never()).getAuthorId(any());
		verify(postService, never()).deleteAsAdmin(any());
		verify(userRepository, never()).save(any());
	}
}
```
를
```java
		verify(postService, never()).getAuthorId(any());
		verify(postService, never()).deleteAsAdmin(any());
		verify(userRepository, never()).save(any());
		verify(tokenBlacklistService, never()).banUser(any());
	}
}
```
로 교체.

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportActionServiceTest"`
Expected: FAIL — 컴파일 에러(`AdminReportActionService`의 생성자가 아직 5개 인자만 받음)

- [ ] **Step 3: 최소 구현 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`에서 import 블록의
```java
import com.runvas.backend.community.ReportStatus;
import lombok.RequiredArgsConstructor;
```
를
```java
import com.runvas.backend.community.ReportStatus;
import com.runvas.auth.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
```
로 교체.

필드 블록:
```java
	private final ReportRepository reportRepository;
	private final PostService postService;
	private final CommentService commentService;
	private final CourseCommentService courseCommentService;
	private final UserRepository userRepository;
```
를
```java
	private final ReportRepository reportRepository;
	private final PostService postService;
	private final CommentService commentService;
	private final CourseCommentService courseCommentService;
	private final UserRepository userRepository;
	private final TokenBlacklistService tokenBlacklistService;
```
로 교체(`@RequiredArgsConstructor`가 생성자를 자동 생성하므로 필드 추가만으로 충분).

`resolveAndBan` 메서드 안의
```java
		if (authorId != null) {
			userRepository.findById(UUID.fromString(authorId)).ifPresent(user -> {
				user.ban();
				userRepository.save(user);
			});
		}
```
를
```java
		if (authorId != null) {
			userRepository.findById(UUID.fromString(authorId)).ifPresent(user -> {
				user.ban();
				userRepository.save(user);
				tokenBlacklistService.banUser(user.getId());
			});
		}
```
로 교체.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminReportActionServiceTest"`
Expected: PASS (9 tests)

- [ ] **Step 5: 전체 백엔드 테스트 확인 후 커밋**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (다른 곳에서 `new AdminReportActionService(...)`를 호출하는 곳이 없는지 함께
확인됨 — 있다면 여기서 컴파일 에러로 드러난다)

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java
git commit -m "feat(admin): 신고 처리로 정지시킨 유저의 기존 토큰도 즉시 무효화"
```
