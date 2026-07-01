# 로그아웃 / 토큰 무효화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그아웃 시 서버가 해당 `accessToken`을 즉시 무효화하도록 `POST /api/auth/logout`을 추가하고, 모바일에 로그아웃 UI를 붙인다.

**Architecture:** JWT는 stateless이므로 Redis에 로그아웃된 토큰을 `TTL = 남은 만료시간`으로 저장하는 블랙리스트를 둔다. `JwtAuthenticationFilter`가 서명 검증 후 블랙리스트를 확인해 무효화된 토큰을 `401`로 거부한다. 모바일은 백엔드 로그아웃이 성공한 경우에만 로컬 토큰을 지운다.

**Tech Stack:** Spring Boot (Java) + Spring Security + `spring-boot-starter-data-redis`(`StringRedisTemplate`) / React Native + Expo (`expo-secure-store`)

## Global Constraints

- 설계 스펙: `docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md` (이미 사용자 승인됨). 이 문서와 충돌하는 구현은 하지 않는다.
- `main`에 직접 커밋/푸시 금지. 반드시 기능 브랜치에서 작업한다 (`superpowers:using-git-worktrees`로 격리된 워크스페이스 생성).
- 커밋 메시지는 Conventional Commits 형식 (`docs: ...`, `feat(auth): ...`, `feat(mobile): ...`, `test(auth): ...`).
- 커밋에는 의도한 파일만 스테이징한다 (`git add <path>`, `git add -A`/`git add .` 금지).
- API/데이터모델/인증 동작이 바뀌면 구현보다 `docs/` 변경을 먼저 커밋한다 (Task 1이 이를 담당).
- 무효화 범위는 로그아웃에 사용된 토큰 1개만 (사용자 전체 세션 무효화 아님).
- 모바일 로그아웃은 백엔드 호출이 성공해야만 로컬 `SecureStore`를 지운다 (실패 시 로그인 상태 유지).
- 백엔드 인증 요청은 `Authorization: Bearer <accessToken>` 헤더를 사용한다 (기존 컨벤션 유지).
- `providerUserId`는 API 응답에 노출하지 않는다 (이번 변경과 무관하지만 기존 규칙 유지 확인).

---

## File Structure

**백엔드 (`backend/`)**
- `src/main/java/com/runvas/auth/service/JwtProvider.java` — 만료 시각 추출 메서드 추가
- `src/main/java/com/runvas/auth/service/TokenBlacklistService.java` — 신규, Redis 블랙리스트 read/write
- `src/main/java/com/runvas/auth/service/AuthLogoutService.java` — 신규, 컨트롤러→블랙리스트 위임
- `src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java` — 블랙리스트 체크 추가
- `src/main/java/com/runvas/global/security/SecurityConfig.java` — 필터 생성자에 `TokenBlacklistService` 전달
- `src/main/java/com/runvas/auth/controller/AuthController.java` — `POST /logout` 추가
- 대응 테스트: `JwtProviderTest`, `TokenBlacklistServiceTest`(신규), `SecurityConfigTest`, `AuthControllerTest`

**모바일 (`mobile/`)**
- `src/services/authApi.ts` — `postAuthLogout` 추가
- `src/contexts/AuthContext.tsx` — `logout()`을 async로 변경 (백엔드 성공 시에만 로컬 정리)
- `src/screens/ProfileScreen.tsx` — 로그아웃 버튼 + 확인 다이얼로그 추가
- `docs/implementations/logout.md` — 신규, 구현 기록 (mobile/CLAUDE.md 5단계 프로세스)

**문서**
- `docs/api-contract.md` — `POST /auth/logout` 엔드포인트 추가

---

### Task 1: `docs/api-contract.md`에 `POST /auth/logout` 추가 (docs-first)

**Files:**
- Modify: `docs/api-contract.md` (Auth APIs 섹션, `### GET /me` 앞에 삽입)

**Interfaces:**
- Produces: `POST /api/auth/logout` 계약 — `Required` auth, 요청 본문 없음, `204 No Content`, 에러 `401 UNAUTHORIZED`. 이후 모든 백엔드/모바일 작업이 이 계약을 구현한다.

- [ ] **Step 1: `docs/api-contract.md`의 `### POST /auth/kakao` 섹션과 `### GET /me` 섹션 사이에 다음 내용을 삽입한다.**

```markdown
### POST /auth/logout

로그아웃하고, 요청에 사용된 `accessToken`을 서버에서 무효화합니다.
무효화 범위는 로그아웃에 사용된 토큰 하나입니다 (동일 사용자의 다른 기기 로그인은 유지됩니다).

#### Auth

`Required`

#### Request Body

없음. 토큰은 `Authorization` 헤더에서 가져옵니다.

#### Response: 204 No Content

응답 본문이 없습니다.

#### Errors

- `401 UNAUTHORIZED`: 로그인하지 않았거나 토큰이 유효하지 않음. 이미 로그아웃된(무효화된) 토큰으로
  재요청한 경우도 동일하게 처리합니다.

```

- [ ] **Step 2: 삽입 위치와 마크다운 렌더링을 확인한다.**

Run: `sed -n '765,845p' docs/api-contract.md`
Expected: `### POST /auth/logout` 섹션이 `### POST /auth/kakao`와 `### GET /me` 사이에 온전한 형태로 보임 (제목 레벨 `###` 일치, 코드블록 닫힘 확인).

- [ ] **Step 3: Commit**

```bash
git add docs/api-contract.md
git commit -m "docs: POST /auth/logout API 계약 추가"
```

---

### Task 2: `JwtProvider`에 만료 시각 추출 메서드 추가

**Files:**
- Modify: `backend/src/main/java/com/runvas/auth/service/JwtProvider.java`
- Test: `backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java`

**Interfaces:**
- Consumes: 없음 (기존 `JwtProvider(String secret, long expirationSeconds)` 생성자, `createAccessToken(UUID)`)
- Produces: `JwtProvider.getExpiration(String token): java.time.Instant` — Task 3의 `TokenBlacklistService`가 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java` 상단 import에 `import java.time.Instant;`를 추가하고, 파일 끝(마지막 `}` 앞)에 다음 테스트를 추가한다.

```java
    @Test
    void returnsTokenExpirationInstant() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);
        UUID userId = UUID.randomUUID();
        Instant beforeCreate = Instant.now();

        String token = jwtProvider.createAccessToken(userId);
        Instant expiration = jwtProvider.getExpiration(token);

        assertThat(expiration).isAfter(beforeCreate.plusSeconds(3599));
        assertThat(expiration).isBefore(beforeCreate.plusSeconds(3601));
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.JwtProviderTest"`
Expected: FAIL — `cannot find symbol: method getExpiration`

- [ ] **Step 3: `JwtProvider`에 메서드 추가**

`backend/src/main/java/com/runvas/auth/service/JwtProvider.java`의 `parseUserId` 메서드 뒤, 클래스 닫는 `}` 앞에 추가:

```java
    public Instant getExpiration(String token) {
        Date expiration = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        return expiration.toInstant();
    }
```

(`Instant`, `Date`는 이미 파일 상단에 import 되어 있음 — 확인만 한다.)

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.JwtProviderTest"`
Expected: PASS (4개 기존 테스트 + 신규 1개, 총 5개)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/auth/service/JwtProvider.java backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java
git commit -m "feat(auth): JwtProvider에 토큰 만료 시각 추출 메서드 추가"
```

---

### Task 3: `TokenBlacklistService` 신규 작성 (Redis 블랙리스트)

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java`
- Create: `backend/src/test/java/com/runvas/auth/service/TokenBlacklistServiceTest.java`

**Interfaces:**
- Consumes: `JwtProvider.getExpiration(String): Instant` (Task 2), Spring Boot 자동 구성된 `StringRedisTemplate` 빈 (별도 `@Bean` 선언 불필요 — `spring-boot-starter-data-redis`가 classpath에 있고 `spring.data.redis.host/port`가 `application.yml`에 설정돼 있으면 자동 생성됨).
- Produces: `TokenBlacklistService.blacklist(String token): void`, `TokenBlacklistService.isBlacklisted(String token): boolean` — Task 4(필터), Task 5(로그아웃 서비스)가 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/TokenBlacklistServiceTest.java` 신규 생성:

```java
package com.runvas.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class TokenBlacklistServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final TokenBlacklistService tokenBlacklistService =
            new TokenBlacklistService(redisTemplate, jwtProvider);

    @Test
    void blacklistsTokenWithRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtProvider.getExpiration("token-1")).thenReturn(Instant.now().plusSeconds(120));

        tokenBlacklistService.blacklist("token-1");

        verify(valueOperations).set(eq("auth:blacklist:token-1"), eq("1"), any(Duration.class));
    }

    @Test
    void doesNotBlacklistAlreadyExpiredToken() {
        when(jwtProvider.getExpiration("expired-token")).thenReturn(Instant.now().minusSeconds(1));

        tokenBlacklistService.blacklist("expired-token");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isBlacklistedReturnsTrueWhenKeyExists() {
        when(redisTemplate.hasKey("auth:blacklist:token-2")).thenReturn(true);

        assertThat(tokenBlacklistService.isBlacklisted("token-2")).isTrue();
    }

    @Test
    void isBlacklistedReturnsFalseWhenKeyMissing() {
        when(redisTemplate.hasKey("auth:blacklist:token-3")).thenReturn(false);

        assertThat(tokenBlacklistService.isBlacklisted("token-3")).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.TokenBlacklistServiceTest"`
Expected: FAIL — `cannot find symbol: class TokenBlacklistService`

- [ ] **Step 3: `TokenBlacklistService` 구현**

`backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java` 신규 생성:

```java
package com.runvas.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklist:";

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
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.TokenBlacklistServiceTest"`
Expected: PASS (4개 테스트)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java backend/src/test/java/com/runvas/auth/service/TokenBlacklistServiceTest.java
git commit -m "feat(auth): Redis 기반 토큰 블랙리스트 서비스 추가"
```

---

### Task 4: `JwtAuthenticationFilter`가 블랙리스트된 토큰을 거부하도록 수정

**Files:**
- Modify: `backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/runvas/global/security/SecurityConfig.java`
- Modify: `backend/src/test/java/com/runvas/global/security/SecurityConfigTest.java`

**Interfaces:**
- Consumes: `TokenBlacklistService.isBlacklisted(String): boolean` (Task 3)
- Produces: `JwtAuthenticationFilter(JwtProvider, TokenBlacklistService, SecurityErrorResponseWriter)` 새 생성자 시그니처 — Task 5 이후 다른 코드가 이 필터를 직접 생성하는 곳은 `SecurityConfig` 한 곳뿐이므로 다른 영향 없음.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/global/security/SecurityConfigTest.java`에서:

1. import 블록에 추가: `import com.runvas.auth.service.TokenBlacklistService;`
2. `@MockBean JwtProvider jwtProvider;` 바로 아래에 추가:

```java
    @MockBean
    TokenBlacklistService tokenBlacklistService;
```

3. `validBearerTokenAuthenticatesRequest` 테스트 뒤, 클래스 닫는 `}` (즉 `TestController` static class 정의 앞)에 추가:

```java
    @Test
    void blacklistedTokenReturnsUnauthorizedEnvelopeAndClearsContext() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseUserId("blacklisted-token")).thenReturn(userId);
        when(tokenBlacklistService.isBlacklisted("blacklisted-token")).thenReturn(true);

        mockMvc.perform(get("/test/protected")
                        .header("Authorization", "Bearer blacklisted-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Authentication is required"))
                .andExpect(jsonPath("$.error.details.length()").value(0));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.global.security.SecurityConfigTest"`
Expected: FAIL — 컴파일 에러 (`SecurityConfig`/`JwtAuthenticationFilter` 생성자가 아직 `TokenBlacklistService`를 받지 않음) 또는 `blacklistedTokenReturnsUnauthorizedEnvelopeAndClearsContext`가 200으로 통과해버리는 실패.

- [ ] **Step 3: `JwtAuthenticationFilter` 수정**

`backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java` 전체를 다음으로 교체:

```java
package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import com.runvas.global.error.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, TokenBlacklistService tokenBlacklistService,
                                    SecurityErrorResponseWriter errorResponseWriter) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length());
            try {
                UUID userId = jwtProvider.parseUserId(token);
                if (tokenBlacklistService.isBlacklisted(token)) {
                    SecurityContextHolder.clearContext();
                    errorResponseWriter.write(response, ErrorCode.UNAUTHORIZED);
                    return;
                }
                RunvasPrincipal principal = new RunvasPrincipal(userId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, token, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                errorResponseWriter.write(response, ErrorCode.UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: `SecurityConfig` 수정**

`backend/src/main/java/com/runvas/global/security/SecurityConfig.java` 전체를 다음으로 교체:

```java
package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                            TokenBlacklistService tokenBlacklistService,
                                            SecurityErrorResponseWriter errorResponseWriter,
                                            RunvasAuthenticationEntryPoint authenticationEntryPoint,
                                            RunvasAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/kakao", "/api/auth/dev-login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/{courseId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/routes/pedestrian").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/courses").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/courses/{courseId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/{courseId}").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, tokenBlacklistService, errorResponseWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

(`/api/auth/logout`은 permitAll 목록에 없으므로 기존 `anyRequest().authenticated()`에 걸려 자동으로 `Required` 인증이 적용된다 — 별도 매처 추가 불필요.)

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.global.security.SecurityConfigTest"`
Expected: PASS (기존 3개 + 신규 1개, 총 4개)

- [ ] **Step 6: 전체 백엔드 테스트로 회귀 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (Docker 필요한 Testcontainers 기반 `AuthControllerTest`는 이 시점엔 아직 컴파일 에러 없이 기존 그대로 통과해야 함 — Task 5에서 더 수정됨)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java backend/src/main/java/com/runvas/global/security/SecurityConfig.java backend/src/test/java/com/runvas/global/security/SecurityConfigTest.java
git commit -m "feat(auth): JwtAuthenticationFilter가 블랙리스트된 토큰을 401로 거부"
```

---

### Task 5: `POST /api/auth/logout` 엔드포인트 추가

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/AuthLogoutService.java`
- Modify: `backend/src/main/java/com/runvas/auth/controller/AuthController.java`
- Modify: `backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `TokenBlacklistService.blacklist(String): void` (Task 3)
- Produces: `POST /api/auth/logout` — `204 No Content`. Task 6(모바일 `authApi.ts`)가 이 계약을 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java`에서:

1. import 블록에 추가:
```java
import com.jayway.jsonpath.JsonPath;
import com.runvas.auth.service.TokenBlacklistService;
import org.springframework.test.web.servlet.MvcResult;
```
그리고 `import static org.mockito.Mockito.when;` 바로 아래(또는 근처)에:
```java
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

2. `@MockBean KakaoAuthClient kakaoAuthClient;` 바로 아래에 추가:

```java
    @MockBean
    TokenBlacklistService tokenBlacklistService;
```

3. 클래스 마지막 테스트(`kakaoLoginAllowsMissingAuthorizationHeader`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
    @Test
    void logoutInvalidatesTokenReturningNoContent() throws Exception {
        when(kakaoAuthClient.fetchUserInfo("authorization-code-logout", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-logout", "runner@example.com", "Seoul Runner", null));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code-logout",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(tokenBlacklistService).blacklist(accessToken);
    }

    @Test
    void blacklistedTokenIsRejectedOnSubsequentRequest() throws Exception {
        when(kakaoAuthClient.fetchUserInfo("authorization-code-blacklisted", "runvas://auth/kakao"))
                .thenReturn(new KakaoUserInfo("kakao-blacklisted", "runner@example.com", "Seoul Runner", null));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code-blacklisted",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        when(tokenBlacklistService.isBlacklisted(accessToken)).thenReturn(true);

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.controller.AuthControllerTest"`
Expected: FAIL — `404` (엔드포인트 없음) 또는 컴파일 에러 (`AuthLogoutService` 없음)

- [ ] **Step 3: `AuthLogoutService` 구현**

`backend/src/main/java/com/runvas/auth/service/AuthLogoutService.java` 신규 생성:

```java
package com.runvas.auth.service;

import org.springframework.stereotype.Service;

@Service
public class AuthLogoutService {

    private final TokenBlacklistService tokenBlacklistService;

    public AuthLogoutService(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }
}
```

- [ ] **Step 4: `AuthController`에 로그아웃 엔드포인트 추가**

`backend/src/main/java/com/runvas/auth/controller/AuthController.java` 전체를 다음으로 교체:

```java
package com.runvas.auth.controller;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.auth.service.AuthLogoutService;
import com.runvas.auth.service.KakaoAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final AuthLogoutService authLogoutService;

    public AuthController(KakaoAuthService kakaoAuthService, AuthLogoutService authLogoutService) {
        this.kakaoAuthService = kakaoAuthService;
        this.authLogoutService = authLogoutService;
    }

    @PostMapping("/kakao")
    AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return kakaoAuthService.login(request);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String token = authorization.substring("Bearer ".length());
        authLogoutService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
```

`/api/auth/logout`은 `SecurityConfig`의 `anyRequest().authenticated()`에 걸리므로, 이 메서드에 도달하는 시점엔 `JwtAuthenticationFilter`가 이미 유효한 `Bearer` 토큰의 존재를 보장한다 — 헤더 null 체크를 추가하지 않는다 (발생할 수 없는 상태를 방어하지 않는다).

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.controller.AuthControllerTest"`
Expected: PASS (기존 3개 + 신규 2개, 총 5개) — Docker가 없으면 `@Testcontainers(disabledWithoutDocker = true)`로 스킵됨.

- [ ] **Step 6: 전체 백엔드 테스트 & 빌드 확인**

Run: `cd backend && ./gradlew test build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/auth/service/AuthLogoutService.java backend/src/main/java/com/runvas/auth/controller/AuthController.java backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java
git commit -m "feat(auth): POST /api/auth/logout 엔드포인트 추가"
```

---

### Task 6: 모바일 `authApi.ts`에 `postAuthLogout` 추가

**Files:**
- Modify: `mobile/src/services/authApi.ts`

**Interfaces:**
- Consumes: `POST /api/auth/logout` (Task 5), `parseApiErrorMessage(response: Response): Promise<string>` (`mobile/src/utils/apiError.ts`, 기존)
- Produces: `postAuthLogout(accessToken: string): Promise<void>` — Task 7의 `AuthContext.logout()`이 사용.

- [ ] **Step 1: `mobile/src/services/authApi.ts` 파일 끝에 추가**

```ts
export async function postAuthLogout(accessToken: string): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
```

(`parseApiErrorMessage`는 이미 파일 상단에서 import 되어 있음 — 확인만 한다.)

- [ ] **Step 2: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 (자동 테스트 러너 미설정 — `mobile/CLAUDE.md` 기준 tsc가 1차 검증)

- [ ] **Step 3: Commit**

```bash
git add mobile/src/services/authApi.ts
git commit -m "feat(mobile): postAuthLogout API 함수 추가"
```

---

### Task 7: `AuthContext.logout()`을 비동기 백엔드 호출로 변경

**Files:**
- Modify: `mobile/src/contexts/AuthContext.tsx`

**Interfaces:**
- Consumes: `postAuthLogout(accessToken: string): Promise<void>` (Task 6)
- Produces: `AuthContextValue.logout: () => Promise<void>` (기존 `() => void`에서 시그니처 변경) — Task 8의 `ProfileScreen`이 사용. 실패 시 로컬 상태를 건드리지 않고 에러를 그대로 throw한다 (호출자가 catch).

- [ ] **Step 1: import 및 인터페이스 수정**

`mobile/src/contexts/AuthContext.tsx` 상단 import에서:

```ts
import { postAuthKakao } from '../services/authApi';
```
를
```ts
import { postAuthKakao, postAuthLogout } from '../services/authApi';
```
로 바꾼다.

`AuthContextValue` 인터페이스의 `logout: () => void;`를 `logout: () => Promise<void>;`로 바꾼다.

- [ ] **Step 2: `logout` 구현 교체**

기존:

```ts
  const logout = useCallback(() => {
    SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {});
    SecureStore.deleteItemAsync(USER_KEY).catch(() => {});
    setUser(null);
    setAccessToken(null);
    setPendingNewUserRedirect(false);
  }, []);
```

를 다음으로 교체:

```ts
  const logout = useCallback(async () => {
    if (accessToken) {
      await postAuthLogout(accessToken);
    }
    await Promise.all([
      SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {}),
      SecureStore.deleteItemAsync(USER_KEY).catch(() => {}),
    ]);
    setUser(null);
    setAccessToken(null);
    setPendingNewUserRedirect(false);
  }, [accessToken]);
```

(`postAuthLogout`이 실패하면 예외가 그대로 던져지고, 이후의 `SecureStore` 삭제/상태 초기화 코드는 실행되지 않는다 — 로그인 상태가 유지된다.)

- [ ] **Step 3: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음. (`logout`의 반환 타입이 인터페이스와 구현 모두 `Promise<void>`로 일치해야 함 — 불일치 시 tsc가 여기서 잡아낸다.)

- [ ] **Step 4: Commit**

```bash
git add mobile/src/contexts/AuthContext.tsx
git commit -m "feat(mobile): AuthContext.logout이 백엔드 로그아웃 성공 시에만 로컬 토큰을 정리하도록 변경"
```

---

### Task 8: `ProfileScreen`에 로그아웃 버튼 + 확인 다이얼로그 추가

**Files:**
- Modify: `mobile/src/screens/ProfileScreen.tsx`

**Interfaces:**
- Consumes: `useAuth().logout(): Promise<void>` (Task 7), `Colors` (`mobile/src/constants/theme.ts`, 기존)
- Produces: 없음 (최종 UI 화면)

- [ ] **Step 1: `mobile/src/screens/ProfileScreen.tsx` 전체를 다음으로 교체**

```tsx
import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';

// tabPress 가드가 명령형 진입(딥링크 등)까지는 막지 못하므로, 화면 진입 시에도 한 번 더
// requireAuth()를 호출하는 방어 가드를 둔다 (Critic 리뷰 반영).
export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const { requireAuth } = useAuthGate();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    requireAuth();
  }, [requireAuth]);

  const handleLogout = useCallback(() => {
    Alert.alert('로그아웃', '로그아웃 하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '로그아웃',
        style: 'destructive',
        onPress: async () => {
          setIsLoggingOut(true);
          try {
            await logout();
          } catch (e: unknown) {
            Alert.alert('오류', e instanceof Error ? e.message : '로그아웃에 실패했습니다.');
          } finally {
            setIsLoggingOut(false);
          }
        },
      },
    ]);
  }, [logout]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        {user ? (
          <>
            <Text style={styles.nickname}>{user.nickname}</Text>
            <TouchableOpacity
              style={[styles.logoutButton, isLoggingOut && styles.logoutButtonDisabled]}
              activeOpacity={0.8}
              disabled={isLoggingOut}
              onPress={handleLogout}
            >
              {isLoggingOut ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.logoutButtonText}>로그아웃</Text>
              )}
            </TouchableOpacity>
          </>
        ) : (
          <Text style={styles.emptyText}>로그인이 필요합니다.</Text>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nickname: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  logoutButton: {
    marginTop: 16,
    backgroundColor: Colors.danger,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 10,
  },
  logoutButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  logoutButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
});
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

카카오 로그인 → 프로필 탭 진입 → 로그아웃 버튼 탭 → 확인 다이얼로그에서 "로그아웃" 선택 → 로그인 화면/비로그인 상태로 전환되는지 확인. 이후 재로그인이 정상 동작하는지 확인.

- [ ] **Step 5: Commit**

```bash
git add mobile/src/screens/ProfileScreen.tsx
git commit -m "feat(mobile): ProfileScreen에 로그아웃 버튼과 확인 다이얼로그 추가"
```

---

### Task 9: 모바일 구현 기록 문서화 + 최종 검증

**Files:**
- Create: `mobile/docs/implementations/logout.md`

**Interfaces:**
- Consumes: 없음
- Produces: 없음 (문서화 산출물)

- [ ] **Step 1: `mobile/docs/implementations/logout.md` 신규 생성**

```markdown
# 로그아웃

구현일: 2026-07-01

## 요약

`POST /api/auth/logout`으로 백엔드에 로그아웃을 요청하고, 성공한 경우에만 `SecureStore`의
`runvas_access_token`/`runvas_user`를 지운다. 실패 시(네트워크 오류 등) 로컬 상태를 그대로 유지해
사용자가 로그인 상태를 잃지 않도록 한다.

## 핵심 결정

- **백엔드 성공이 로그아웃의 전제 조건**: `AuthContext.logout()`은 `postAuthLogout` 실패 시 예외를
  그대로 던지고 `SecureStore` 삭제를 실행하지 않는다. 백엔드가 Redis 블랙리스트로 토큰을 즉시
  무효화하므로(`docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`), 로컬만
  지우고 서버 호출이 실패하면 토큰이 여전히 유효한 채로 기기에서만 로그아웃된 것처럼 보이는 상태를
  방지한다.
- **로그아웃 버튼 위치**: `ProfileScreen` — 사용자 정보를 보여주는 화면이라 자연스러운 위치.
- **확인 다이얼로그**: 기존 삭제 확인 패턴(`SavedRoutesScreen.tsx`)과 동일하게
  `Alert.alert(title, message, [취소, {style: 'destructive'}])` 형태를 사용해 실수 로그아웃을 방지.

## 관련 문서

- `docs/api-contract.md` §POST /auth/logout
- `docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`
```

- [ ] **Step 2: 백엔드 전체 테스트 최종 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 모바일 타입 체크 최종 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: Commit**

```bash
git add mobile/docs/implementations/logout.md
git commit -m "docs(mobile): 로그아웃 구현 기록 추가"
```

---

## Verification (전체 완료 후)

1. `cd backend && ./gradlew test build` → `BUILD SUCCESSFUL`
2. `cd mobile && npx tsc --noEmit` → 에러 없음
3. 백엔드 로컬 실행 (`docker compose up` 등 기존 dev 스크립트로 Postgres+Redis 기동) 후:
   - `POST /api/auth/kakao`로 로그인 → `accessToken` 획득
   - `POST /api/auth/logout` (해당 토큰) → `204`
   - 같은 토큰으로 `GET /api/me` → `401 UNAUTHORIZED`
4. 모바일 실기기/시뮬레이터: 로그인 → 프로필 탭 → 로그아웃 확인 다이얼로그 → 로그아웃 → 재로그인 흐름 확인
5. `docs/api-contract.md`의 `POST /auth/logout` 예시가 실제 응답(204, 빈 본문)과 일치하는지 확인
