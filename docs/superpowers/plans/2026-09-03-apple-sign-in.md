# Apple 로그인(Sign in with Apple) 추가 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**배경:** App Store 심사 거부(Guideline 4.8 Design - Login Services). 카카오 로그인만 있고, "이름/이메일만 수집, 이메일 비공개 가능, 동의 없는 광고 추적 없음" 조건을 만족하는 동등한 로그인 수단이 없다는 이유로 거부됨. Apple은 명시적으로 Sign in with Apple이 이 조건을 만족한다고 안내함.

**Goal:** 카카오 로그인과 동등한 선택지로 Sign in with Apple을 추가해 4.8 거부 사유를 해소한다.

**Architecture:** 모바일이 `expo-apple-authentication`으로 Apple identity token을 받아 백엔드 `POST /api/auth/apple`로 전달한다. 백엔드는 Apple의 공개키(JWKS)로 identity token 서명을 검증하고, `sub`(Apple 사용자 ID)를 `provider=APPLE`의 `providerUserId`로 매핑해 기존 카카오 로그인과 동일한 Runvas 사용자/JWT 발급 흐름을 재사용한다. `KakaoAuthService`/`KakaoAuthClient` 구조를 그대로 따라간다.

**Tech Stack:** Spring Boot(Gradle, JUnit5), `io.jsonwebtoken:jjwt` 0.12.6(이미 의존성 있음, JWK 검증에 재사용), React Native/Expo SDK 54, `expo-apple-authentication`.

## Global Constraints

- `providerUserId`(Apple `sub`)는 API 응답에 노출하지 않는다 (루트 `CLAUDE.md`, `docs/api-contract.md` 기존 규칙과 동일).
- 좌표/거리 등 이 저장소의 다른 데이터 규칙과는 무관한 작업이다.
- `backend/`, `mobile/` 변경 전에 `docs/api-contract.md`, `docs/data-model.md`를 먼저 고친다 (루트 `CLAUDE.md` docs-first 원칙).
- 네이티브 의존성은 `npm install`이 아니라 `npx expo install`로 설치한다 (`mobile/AGENTS.md`).
- 커밋 메시지에 `Co-Authored-By`, `codex`, `claude` 등 도구/저작자 표시를 넣지 않는다. Conventional Commits 형식(`feat(auth): ...`)을 한글로 쓴다.
- 사전 확인 필요: Apple Developer 계정에서 앱 ID(`com.runvas.mobile`)에 "Sign in with Apple" capability가 활성화되어 있어야 실제 기기/TestFlight에서 동작한다. 이건 Apple Developer 콘솔 작업이라 이 플랜의 범위 밖이며, 코드 작업과 별개로 사용자가 직접 확인해야 한다.

---

## Task 1: docs 갱신 — `POST /auth/apple` 계약 정의

**Files:**
- Modify: `docs/api-contract.md:1002-1075` (Auth APIs 섹션, `POST /auth/kakao` 바로 뒤에 삽입)
- Modify: `docs/data-model.md:96-108` (User 섹션의 `provider` 필드 설명)

**Interfaces:**
- Produces: 이후 모든 backend/mobile 작업이 참조하는 요청/응답 필드 이름과 타입 — `provider`(`"APPLE"`), `identityToken`(string), `nickname`(string, nullable), 응답은 기존 `AuthResponse`(`accessToken`, `user`, `isNewUser`)와 동일 shape.

- [ ] **Step 1: `docs/api-contract.md`의 `### POST /auth/kakao` 섹션(1075번째 줄, `### POST /auth/logout` 시작 직전) 바로 앞에 아래 섹션을 삽입한다.**

```markdown
### POST /auth/apple

Apple ID로 로그인하거나 신규 회원가입을 처리합니다.

#### Flow

1. 모바일 앱이 `expo-apple-authentication`으로 Apple 로그인 시트를 띄웁니다.
2. Apple이 모바일 앱에 `identityToken`(JWT)과, 최초 인증 시에만 `fullName`을 반환합니다.
3. 모바일 앱이 `POST /api/auth/apple`로 `identityToken`과(최초 인증이면) `nickname`을 전달합니다.
4. 백엔드는 Apple의 공개키(JWKS, `https://appleid.apple.com/auth/keys`)로 `identityToken` 서명을 검증하고 `iss`(`https://appleid.apple.com`), `aud`(앱 번들 ID)를 검증합니다.
5. 백엔드는 `identityToken`의 `sub`를 `providerUserId`로, `email` 클레임을 이메일로 사용해 `provider = APPLE` 기준으로 Runvas 사용자를 조회하거나 생성합니다.
6. 백엔드는 Runvas 자체 `accessToken`, `user`, `isNewUser`를 모바일 앱에 반환합니다.

Apple `identityToken`, `sub`는 API 응답에 포함하지 않습니다.
Apple 사용자 ID는 `providerUserId`로 내부 저장하되 API 응답에 포함하지 않습니다.

#### Auth

`None`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | string | Y | `APPLE` |
| `identityToken` | string | Y | Apple이 발급한 identity token(JWT) |
| `nickname` | string | N | Apple이 최초 인증 시에만 제공하는 이름. 없으면 서버가 기본 닉네임을 생성 |

```json
{
  "provider": "APPLE",
  "identityToken": "apple_identity_token_jwt",
  "nickname": "Seoul Runner"
}
```

#### Response: 200 OK

```json
{
  "accessToken": "jwt_access_token",
  "user": {
    "id": "user_123",
    "email": "runner@example.com",
    "provider": "APPLE",
    "nickname": "Seoul Runner",
    "profileImageUrl": null,
    "bio": null,
    "createdAt": "2026-09-03T08:00:00Z",
    "updatedAt": "2026-09-03T08:00:00Z"
  },
  "isNewUser": true
}
```

`accessToken`은 Runvas API용 JWT입니다. MVP에서는 refresh token을 응답하지 않습니다.
`email`은 Apple이 비공개 릴레이 이메일(`@privaterelay.appleid.com`)을 제공할 수도 있고, 재로그인 시 클레임 자체가 없을 수도 있습니다 — 그 경우 최초 가입 시 저장된 이메일을 유지합니다.
`nickname`은 최초 인증 시 Apple이 제공한 이름을 사용하되, 없으면 서버가 기본 닉네임을 생성합니다.

#### Errors

- `400 VALIDATION_ERROR`: 필수 필드 누락, `provider`가 `APPLE`이 아님
- `401 UNAUTHORIZED`: `identityToken` 서명 검증 실패, `iss`/`aud` 불일치, 만료된 토큰

```

- [ ] **Step 2: `docs/data-model.md:102`의 `provider` 필드 설명을 갱신한다.**

기존:
```markdown
| `provider` | string | Y | 소셜 로그인 제공자. MVP에서는 `KAKAO` |
```

변경:
```markdown
| `provider` | string | Y | 소셜 로그인 제공자. `KAKAO` \| `APPLE` |
```

- [ ] **Step 3: 커밋**

```bash
git add docs/api-contract.md docs/data-model.md
git commit -m "docs: Apple 로그인 API 계약 정의"
```

---

## Task 2: Backend — `AuthProvider.APPLE` + `User.createAppleUser`

**Files:**
- Modify: `backend/src/main/java/com/runvas/user/domain/AuthProvider.java`
- Modify: `backend/src/main/java/com/runvas/user/domain/User.java`
- Test: `backend/src/test/java/com/runvas/user/domain/UserTest.java` (신규 파일 — 기존에 없으면 새로 만든다)

**Interfaces:**
- Produces: `AuthProvider.APPLE` enum 상수, `User.createAppleUser(String providerUserId, String email, String nickname)` 정적 팩토리 — Task 4의 `AppleAuthService`가 이 시그니처를 그대로 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/user/domain/UserTest.java`:
```java
package com.runvas.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void createAppleUser_닉네임이_없으면_기본_닉네임을_사용한다() {
        User user = User.createAppleUser("apple-sub-123", "runner@example.com", null);

        assertThat(user.getProvider()).isEqualTo(AuthProvider.APPLE);
        assertThat(user.getProviderUserId()).isEqualTo("apple-sub-123");
        assertThat(user.getEmail()).isEqualTo("runner@example.com");
        assertThat(user.getNickname()).isEqualTo("Runvas Runner");
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    void createAppleUser_닉네임이_있으면_그대로_사용한다() {
        User user = User.createAppleUser("apple-sub-456", null, "Jeju Runner");

        assertThat(user.getNickname()).isEqualTo("Jeju Runner");
        assertThat(user.getEmail()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: FAIL — `createAppleUser` method does not exist / compile error

- [ ] **Step 3: `AuthProvider`에 `APPLE` 추가**

`backend/src/main/java/com/runvas/user/domain/AuthProvider.java` 전체를 아래로 교체:
```java
package com.runvas.user.domain;

public enum AuthProvider {
    KAKAO,
    APPLE,
    DEV
}
```

- [ ] **Step 4: `User.java`에 `createAppleUser` 팩토리 추가**

`backend/src/main/java/com/runvas/user/domain/User.java:66`(`createKakaoUser` 메서드 끝, `createDevUser` 시작 직전)에 아래 메서드를 삽입:
```java
    public static User createAppleUser(String providerUserId, String email, String nickname) {
        User user = new User();
        user.provider = AuthProvider.APPLE;
        user.providerUserId = providerUserId;
        user.email = email;
        user.nickname = nickname == null || nickname.isBlank() ? "Runvas Runner" : nickname;
        user.profileImageUrl = null;
        user.bio = null;
        return user;
    }
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/runvas/user/domain/AuthProvider.java backend/src/main/java/com/runvas/user/domain/User.java backend/src/test/java/com/runvas/user/domain/UserTest.java
git commit -m "feat(auth): AuthProvider에 APPLE 추가"
```

---

## Task 3: Backend — Apple identity token 검증 클라이언트

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/AppleUserInfo.java`
- Create: `backend/src/main/java/com/runvas/auth/service/AppleAuthClient.java`
- Create: `backend/src/main/java/com/runvas/auth/service/AppleHttpAuthClient.java`
- Modify: `backend/src/main/resources/application.yml` (또는 `application.properties` — 기존 `runvas.kakao.*` 설정이 있는 파일)
- Test: `backend/src/test/java/com/runvas/auth/service/AppleHttpAuthClientTest.java`

**Interfaces:**
- Consumes: `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6 (이미 `backend/build.gradle`에 있음), `org.springframework.web.client.RestClient`(Kakao 클라이언트와 동일 패턴).
- Produces: `AppleUserInfo(String providerUserId, String email)` record, `AppleAuthClient.verifyIdentityToken(String identityToken): AppleUserInfo` — Task 4의 `AppleAuthService`가 이 메서드를 호출한다.

- [ ] **Step 1: `AppleUserInfo` record 작성**

`backend/src/main/java/com/runvas/auth/service/AppleUserInfo.java`:
```java
package com.runvas.auth.service;

public record AppleUserInfo(String providerUserId, String email) {
}
```

- [ ] **Step 2: `AppleAuthClient` 인터페이스 작성**

`backend/src/main/java/com/runvas/auth/service/AppleAuthClient.java`:
```java
package com.runvas.auth.service;

public interface AppleAuthClient {
    AppleUserInfo verifyIdentityToken(String identityToken);
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/AppleHttpAuthClientTest.java`:
```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runvas.global.error.RunvasException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

class AppleHttpAuthClientTest {

    private static final String BUNDLE_ID = "com.runvas.mobile";
    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";

    @Test
    void verifyIdentityToken_유효한_토큰이면_providerUserId와_email을_반환한다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String kid = "test-key-id";

        String jwksJson = buildJwksJson(kid, (RSAPublicKey) keyPair.getPublic());
        String identityToken = Jwts.builder()
                .header().add("kid", kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add(BUNDLE_ID).and()
                .subject("apple-sub-789")
                .claim("email", "runner@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith((RSAPrivateKey) keyPair.getPrivate())
                .compact();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(req -> assertThat(req.getURI().toString()).isEqualTo(JWKS_URI))
                .andRespond(withSuccessJson(jwksJson));

        AppleHttpAuthClient client = new AppleHttpAuthClient(builder, JWKS_URI, BUNDLE_ID);

        AppleUserInfo info = client.verifyIdentityToken(identityToken);

        assertThat(info.providerUserId()).isEqualTo("apple-sub-789");
        assertThat(info.email()).isEqualTo("runner@example.com");
    }

    @Test
    void verifyIdentityToken_aud가_다르면_예외를_던진다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String kid = "test-key-id-2";

        String jwksJson = buildJwksJson(kid, (RSAPublicKey) keyPair.getPublic());
        String identityToken = Jwts.builder()
                .header().add("kid", kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add("com.other.app").and()
                .subject("apple-sub-999")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith((RSAPrivateKey) keyPair.getPrivate())
                .compact();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(req -> true).andRespond(withSuccessJson(jwksJson));

        AppleHttpAuthClient client = new AppleHttpAuthClient(builder, JWKS_URI, BUNDLE_ID);

        assertThatThrownBy(() -> client.verifyIdentityToken(identityToken))
                .isInstanceOf(RunvasException.class);
    }

    private static String buildJwksJson(String kid, RSAPublicKey publicKey) {
        var jwk = Jwks.builder().key(publicKey).id(kid).build();
        return "{\"keys\":[" + jwk.toString() + "]}";
    }

    private static org.springframework.test.web.client.ResponseCreator withSuccessJson(String body) {
        return org.springframework.test.web.client.response.MockRestResponseCreators
                .withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
```

- [ ] **Step 4: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.AppleHttpAuthClientTest"`
Expected: FAIL — `AppleHttpAuthClient` class does not exist

- [ ] **Step 5: `AppleHttpAuthClient` 구현**

`backend/src/main/java/com/runvas/auth/service/AppleHttpAuthClient.java`:
```java
package com.runvas.auth.service;

import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppleHttpAuthClient implements AppleAuthClient {

    private static final String ISSUER = "https://appleid.apple.com";

    private final RestClient restClient;
    private final String jwksUri;
    private final String bundleId;

    public AppleHttpAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${runvas.apple.jwks-uri}") String jwksUri,
            @Value("${runvas.apple.bundle-id}") String bundleId
    ) {
        this.restClient = restClientBuilder.build();
        this.jwksUri = jwksUri;
        this.bundleId = bundleId;
    }

    @Override
    public AppleUserInfo verifyIdentityToken(String identityToken) {
        try {
            Locator<Key> keyLocator = header -> resolveKey(header);
            Claims claims = Jwts.parser()
                    .keyLocator(keyLocator)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            if (!ISSUER.equals(claims.getIssuer())) {
                throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
            }
            if (!claims.getAudience().contains(bundleId)) {
                throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
            }

            String email = claims.get("email", String.class);
            return new AppleUserInfo(claims.getSubject(), email);
        } catch (RunvasException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
        }
    }

    private Key resolveKey(Header header) {
        String kid = (String) header.get("kid");
        String jwksJson = restClient.get()
                .uri(jwksUri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed");
                })
                .body(String.class);

        JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);
        return jwkSet.getKeys().stream()
                .filter(jwk -> kid != null && kid.equals(jwk.getId()))
                .findFirst()
                .map(Jwk::toKey)
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED, "Apple authentication failed"));
    }
}
```

- [ ] **Step 6: `application.yml`에 Apple 설정 추가**

`backend/src/main/resources/application.yml`의 `runvas.kakao` 설정 블록 바로 아래에 추가 (들여쓰기는 기존 `runvas:` 블록 기준에 맞춘다):
```yaml
  apple:
    jwks-uri: ${APPLE_JWKS_URI:https://appleid.apple.com/auth/keys}
    bundle-id: ${APPLE_BUNDLE_ID:com.runvas.mobile}
```

- [ ] **Step 7: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.AppleHttpAuthClientTest"`
Expected: PASS (2 tests). `Jwks.setParser().build().parse(...)`/`Locator` API가 실제 jjwt 0.12.6 시그니처와 미세하게 다르면(예: `parseJwkSet` vs `parse`) 컴파일 에러 메시지를 보고 jjwt 0.12.6 Javadoc 기준으로 메서드명만 맞춰 수정한다 — 검증 로직(서명/`iss`/`aud` 확인) 자체는 바꾸지 않는다.

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/service/AppleUserInfo.java backend/src/main/java/com/runvas/auth/service/AppleAuthClient.java backend/src/main/java/com/runvas/auth/service/AppleHttpAuthClient.java backend/src/main/resources/application.yml backend/src/test/java/com/runvas/auth/service/AppleHttpAuthClientTest.java
git commit -m "feat(auth): Apple identity token 검증 클라이언트 추가"
```

---

## Task 4: Backend — `AppleLoginRequest` + `AppleAuthService` + 엔드포인트

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java`
- Create: `backend/src/main/java/com/runvas/auth/service/AppleAuthService.java`
- Modify: `backend/src/main/java/com/runvas/auth/controller/AuthController.java`
- Test: `backend/src/test/java/com/runvas/auth/service/AppleAuthServiceTest.java`
- Test: `backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java` (기존 파일에 케이스 추가)

**Interfaces:**
- Consumes: `AppleAuthClient.verifyIdentityToken`(Task 3), `User.createAppleUser`(Task 2), `AuthProvider.APPLE`(Task 2), `JwtProvider.createAccessToken`, `UserResponse.from`(기존).
- Produces: `AuthController`의 `POST /api/auth/apple` — 모바일 Task 8이 호출하는 엔드포인트.

- [ ] **Step 1: `AppleLoginRequest` DTO 작성**

`backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java`:
```java
package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        String nickname
) {
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/AppleAuthServiceTest.java`:
```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.runvas.auth.dto.AppleLoginRequest;
import com.runvas.auth.dto.AuthResponse;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppleAuthServiceTest {

    @Test
    void login_신규_사용자면_생성하고_isNewUser는_true다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-1", "runner@example.com"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-1"))
                .thenReturn(Optional.empty());
        User savedUser = User.createAppleUser("apple-sub-1", "runner@example.com", "Seoul Runner");
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenReturn(savedUser);
        when(jwtProvider.createAccessToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn("jwt-token");

        AppleAuthService service = new AppleAuthService(appleAuthClient, userRepository, jwtProvider);
        AuthResponse response = service.login(new AppleLoginRequest("APPLE", "token-abc", "Seoul Runner"));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
    }
}
```

주의: `AuthResponse`/`UserResponse` 필드 접근자 이름(`accessToken()`, `isNewUser()`, `user().nickname()`)은 기존 `backend/src/main/java/com/runvas/auth/dto/AuthResponse.java`, `backend/src/main/java/com/runvas/user/dto/UserResponse.java`가 record인지 class인지에 따라 다르다 — 실행 전에 두 파일을 열어 실제 접근자 이름으로 맞춘다.

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.AppleAuthServiceTest"`
Expected: FAIL — `AppleAuthService` class does not exist

- [ ] **Step 4: `AppleAuthService` 구현** (`KakaoAuthService`와 동일 구조)

`backend/src/main/java/com/runvas/auth/service/AppleAuthService.java`:
```java
package com.runvas.auth.service;

import com.runvas.auth.dto.AppleLoginRequest;
import com.runvas.auth.dto.AuthResponse;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AppleAuthService {

    private final AppleAuthClient appleAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AppleAuthService(AppleAuthClient appleAuthClient, UserRepository userRepository, JwtProvider jwtProvider) {
        this.appleAuthClient = appleAuthClient;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse login(AppleLoginRequest request) {
        if (!AuthProvider.APPLE.name().equals(request.provider())) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "provider must be APPLE");
        }

        AppleUserInfo appleUserInfo = appleAuthClient.verifyIdentityToken(request.identityToken());

        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.APPLE,
                appleUserInfo.providerUserId()
        );
        existingUser.ifPresent(this::restoreIfWithdrawn);
        LoginResult loginResult = existingUser
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> createOrFindRacedUser(appleUserInfo, request.nickname()));

        String accessToken = jwtProvider.createAccessToken(loginResult.user().getId());
        return new AuthResponse(accessToken, UserResponse.from(loginResult.user()), loginResult.isNewUser());
    }

    private void restoreIfWithdrawn(User user) {
        if (user.isDeleted()) {
            user.restore();
            userRepository.save(user);
        }
    }

    private LoginResult createOrFindRacedUser(AppleUserInfo appleUserInfo, String nickname) {
        try {
            User user = userRepository.saveAndFlush(User.createAppleUser(
                    appleUserInfo.providerUserId(),
                    appleUserInfo.email(),
                    nickname
            ));
            return new LoginResult(user, true);
        } catch (DataIntegrityViolationException exception) {
            return userRepository.findByProviderAndProviderUserId(
                    AuthProvider.APPLE,
                    appleUserInfo.providerUserId()
            ).map(user -> new LoginResult(user, false))
                    .orElseThrow(() -> exception);
        }
    }

    private record LoginResult(User user, boolean isNewUser) {
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.auth.service.AppleAuthServiceTest"`
Expected: PASS

- [ ] **Step 6: `AuthController`에 엔드포인트 추가**

`backend/src/main/java/com/runvas/auth/controller/AuthController.java` 전체를 아래로 교체:
```java
package com.runvas.auth.controller;

import com.runvas.auth.dto.AppleLoginRequest;
import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.auth.service.AppleAuthService;
import com.runvas.auth.service.AuthLogoutService;
import com.runvas.auth.service.KakaoAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final AppleAuthService appleAuthService;
    private final AuthLogoutService authLogoutService;

    public AuthController(
            KakaoAuthService kakaoAuthService,
            AppleAuthService appleAuthService,
            AuthLogoutService authLogoutService
    ) {
        this.kakaoAuthService = kakaoAuthService;
        this.appleAuthService = appleAuthService;
        this.authLogoutService = authLogoutService;
    }

    @PostMapping("/kakao")
    AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return kakaoAuthService.login(request);
    }

    @PostMapping("/apple")
    AuthResponse appleLogin(@Valid @RequestBody AppleLoginRequest request) {
        return appleAuthService.login(request);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        authLogoutService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7: `AuthControllerTest`에 통합 테스트 케이스 추가**

`backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java`를 열어 기존 `POST /api/auth/kakao` 테스트 구조(MockMvc 설정, mock bean 방식)를 확인한 뒤, 동일한 패턴으로 `POST /api/auth/apple`에 대해 (1) 유효한 요청이면 200과 `accessToken`을 반환하는 케이스, (2) `provider`가 `APPLE`이 아니면 400을 반환하는 케이스를 추가한다. 기존 파일의 mock 대상(`KakaoAuthService`)을 `AppleAuthService`로 바꿔서 그대로 미러링하면 된다.

- [ ] **Step 8: 전체 백엔드 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java backend/src/main/java/com/runvas/auth/service/AppleAuthService.java backend/src/main/java/com/runvas/auth/controller/AuthController.java backend/src/test/java/com/runvas/auth/service/AppleAuthServiceTest.java backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java
git commit -m "feat(auth): POST /api/auth/apple 엔드포인트 추가"
```

---

## Task 5: Mobile — `expo-apple-authentication` 설치 및 iOS 설정

**Files:**
- Modify: `mobile/package.json` (`npx expo install`이 자동으로 갱신)
- Modify: `mobile/app.config.js`

**Interfaces:**
- Produces: `ios.usesAppleSignIn: true` 설정, `expo-apple-authentication` 플러그인 등록 — Task 7(로그인 버튼)이 이 패키지의 `AppleAuthentication` API를 import한다.

- [ ] **Step 1: 패키지 설치**

Run: `cd mobile && npx expo install expo-apple-authentication`
Expected: `mobile/package.json`의 `dependencies`에 `expo-apple-authentication`이 추가됨

- [ ] **Step 2: `app.config.js`에 iOS 설정 추가**

`mobile/app.config.js`의 `ios: { ... }` 블록에 `usesAppleSignIn: true`를 추가한다 (기존 `supportsTablet`, `bundleIdentifier`, `infoPlist`와 같은 레벨):
```js
    ios: {
      supportsTablet: false,
      bundleIdentifier: 'com.runvas.mobile',
      usesAppleSignIn: true,
      infoPlist: {
        NSLocationWhenInUseUsageDescription: '러닝 코스 생성을 위해 현재 위치가 필요합니다.',
        NSAppTransportSecurity: appTransportSecurity,
        ITSAppUsesNonExemptEncryption: false,
      },
    },
```

`plugins` 배열에는 별도 항목을 추가하지 않는다 — `expo-apple-authentication`의 config plugin은 `ios.usesAppleSignIn` 값을 읽어 엔타이틀먼트를 자동 생성하므로 `plugins`에 등록할 필요가 없다(`mobile/AGENTS.md`의 "config plugin이 없는 패키지를 plugins에 넣지 않는다" 규칙과 반대로, 이 패키지는 `ios` 설정 값만으로 동작하는 케이스).

- [ ] **Step 3: 타입 체크로 검증**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 (아직 이 패키지를 쓰는 코드가 없으므로 통과해야 정상)

- [ ] **Step 4: 커밋**

```bash
git add mobile/package.json mobile/package-lock.json mobile/app.config.js
git commit -m "feat(mobile): expo-apple-authentication 설치 및 iOS 설정"
```

---

## Task 6: Mobile — `postAuthApple` API 함수

**Files:**
- Modify: `mobile/src/services/authApi.ts`

**Interfaces:**
- Consumes: `AuthResponse` 타입(`mobile/src/types/index.ts`, 기존 `postAuthKakao`가 이미 사용 중).
- Produces: `postAuthApple(identityToken: string, nickname: string | null): Promise<AuthResponse>` — Task 7의 `AuthContext.appleLogin`이 호출한다.

- [ ] **Step 1: `authApi.ts`에 함수 추가**

`mobile/src/services/authApi.ts`의 `postAuthKakao` 함수(1-30번째 줄) 바로 뒤에 추가:
```ts
export async function postAuthApple(
  identityToken: string,
  nickname: string | null,
): Promise<AuthResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/apple`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider: 'APPLE',
      identityToken,
      nickname,
    }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as AuthResponse;
}
```

- [ ] **Step 2: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: 커밋**

```bash
git add mobile/src/services/authApi.ts
git commit -m "feat(mobile): postAuthApple API 함수 추가"
```

---

## Task 7: Mobile — `AuthContext.appleLogin` + 로그인 버튼

**Files:**
- Modify: `mobile/src/contexts/AuthContext.tsx`
- Modify: `mobile/src/components/LoginPromptModal.tsx`

**Interfaces:**
- Consumes: `postAuthApple`(Task 6), `expo-apple-authentication`의 `AppleAuthentication.signInAsync`/`AppleAuthenticationButton`(Task 5).
- Produces: `AuthContextValue.appleLogin: () => Promise<void>` — 로그인 버튼이 호출한다.

- [ ] **Step 1: `AuthContext.tsx` import 및 `appleLogin` 추가**

`mobile/src/contexts/AuthContext.tsx:11`의 import를 교체:
```ts
import { deleteMe, postAuthApple, postAuthKakao, postAuthLogout } from '../services/authApi';
```

`AuthContextValue` 인터페이스(18-35번째 줄)에 추가:
```ts
  appleLogin: () => Promise<void>;
```

`submitKakaoCode` 함수(85-102번째 줄) 바로 뒤에 추가:
```ts
  const appleLogin = useCallback(async () => {
    setLoginError(null);
    setIsLoggingIn(true);
    try {
      const AppleAuthentication = await import('expo-apple-authentication');
      const credential = await AppleAuthentication.signInAsync({
        requestedScopes: [
          AppleAuthentication.AppleAuthenticationScope.FULL_NAME,
          AppleAuthentication.AppleAuthenticationScope.EMAIL,
        ],
      });
      if (!credential.identityToken) {
        throw new Error('Apple 로그인에 실패했습니다.');
      }
      const nickname = credential.fullName?.givenName ?? null;
      const result = await postAuthApple(credential.identityToken, nickname);
      await Promise.all([
        SecureStore.setItemAsync(TOKEN_KEY, result.accessToken),
        SecureStore.setItemAsync(USER_KEY, JSON.stringify(result.user)),
      ]);
      setUser(result.user);
      setAccessToken(result.accessToken);
      setPendingNewUserRedirect(result.isNewUser);
      setIsLoginModalVisible(false);
    } catch (e: unknown) {
      const code = (e as { code?: string })?.code;
      if (code !== 'ERR_REQUEST_CANCELED') {
        setLoginError(e instanceof Error ? e.message : 'Apple 로그인에 실패했습니다.');
      }
    } finally {
      setIsLoggingIn(false);
    }
  }, []);
```

`value`를 만드는 `useMemo`(160-197번째 줄) 두 곳(객체 본문과 의존성 배열)에 `appleLogin`을 추가한다.

- [ ] **Step 2: `LoginPromptModal.tsx`에 Apple 버튼 추가**

`mobile/src/components/LoginPromptModal.tsx`의 import를 교체:
```tsx
import React from 'react';
import { Modal, View, Text, TouchableOpacity, ActivityIndicator, StyleSheet, Platform } from 'react-native';
import * as AppleAuthentication from 'expo-apple-authentication';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
```

`const { isLoginModalVisible, closeLoginModal, kakaoLogin, isLoggingIn, loginError } = useAuth();`를 아래로 교체:
```tsx
  const { isLoginModalVisible, closeLoginModal, kakaoLogin, appleLogin, isLoggingIn, loginError } = useAuth();
```

카카오 버튼(`<TouchableOpacity style={styles.kakaoButton} ...>`) 바로 뒤, 닫기 버튼 앞에 추가:
```tsx
          {Platform.OS === 'ios' && (
            <AppleAuthentication.AppleAuthenticationButton
              buttonType={AppleAuthentication.AppleAuthenticationButtonType.SIGN_IN}
              buttonStyle={AppleAuthentication.AppleAuthenticationButtonStyle.BLACK}
              cornerRadius={8}
              style={styles.appleButton}
              onPress={appleLogin}
            />
          )}
```

`styles`(43-91번째 줄)에 추가:
```ts
  appleButton: {
    width: '100%',
    height: 44,
    marginBottom: 8,
  },
```

- [ ] **Step 3: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: `expo start`로 번들링 확인**

Run: `cd mobile && npx expo start &` 후 `curl "http://localhost:8081/index.bundle?platform=ios&dev=true"`
Expected: HTTP 200 (컴포넌트/훅 오류가 있으면 여기서 500과 함께 스택트레이스가 나온다)

- [ ] **Step 5: 커밋**

```bash
git add mobile/src/contexts/AuthContext.tsx mobile/src/components/LoginPromptModal.tsx
git commit -m "feat(mobile): Apple 로그인 버튼 및 로그인 흐름 추가"
```

---

## Task 8: 최종 검증

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 모바일 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: `docs/api-contract.md`의 예시 요청/응답이 실제 구현과 일치하는지 확인**

`POST /auth/apple` 요청 body 필드명(`provider`, `identityToken`, `nickname`)과 `AppleLoginRequest` record 필드명이 정확히 같은지 다시 확인한다.

- [ ] **Step 4: 사용자에게 안내**

Apple Developer 콘솔에서 `com.runvas.mobile` 앱 ID에 "Sign in with Apple" capability가 켜져 있는지 확인이 아직 안 됐다면, 이 시점에 사용자에게 다시 확인을 요청한다 — capability가 꺼져 있으면 실기기/TestFlight 빌드에서 Apple 로그인 버튼을 눌러도 실패한다.
