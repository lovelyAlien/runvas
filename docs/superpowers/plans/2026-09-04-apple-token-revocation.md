# Apple 계정 삭제 시 토큰 해지(Revoke) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원 탈퇴(하드 삭제) 시점에 Apple 계정에 대해서도 카카오와 대칭인 방식으로 Apple 서버
토큰을 해지(revoke)해, App Store Review Guideline 5.1.1(v) 재거부 위험을 없앤다.

**Architecture:** 로그인 시(`POST /api/auth/apple`) 모바일이 새로 `authorizationCode`를 보내면
백엔드가 이를 Apple 토큰 엔드포인트와 교환해 얻은 refresh token을 `users.apple_refresh_token`에
저장한다(best-effort). 탈퇴 유예기간(30일) 만료 후 `AccountPurgeService.purgeOne()`이 기존 카카오
unlink 분기와 대칭인 Apple 분기에서 저장된 refresh token으로 Apple revoke 엔드포인트를 호출한다
(best-effort). 두 시점 모두 Apple API 호출 실패가 로그인/탈퇴라는 주 흐름을 막지 않는다.

**Tech Stack:** Spring Boot 3.3.7, Java 21, `io.jsonwebtoken`(jjwt) 0.12.6 (client_secret JWT
서명에 재사용), Spring `RestClient`, Flyway, JUnit 5 + Mockito + AssertJ. 모바일은
`expo-apple-authentication`, TypeScript(`fetch` 기반 `authApi.ts`).

## Global Constraints

- 커밋 메시지는 한글 Conventional Commits 형식이며, `Co-Authored-By` 등 AI 저작자 표시를 절대
  붙이지 않는다 (루트 `CLAUDE.md`).
- 커밋에는 의도한 파일만 개별적으로 `git add`한다 — `git add -A`/`git add .` 금지.
- `docs/` 변경은 관련 코드 변경보다 먼저 커밋한다(docs-first 원칙).
- Apple refresh token(`apple_refresh_token`)은 암호화 없이 평문으로 저장한다(브레인스토밍에서
  확정, 설계 문서 참고).
- 로그인 시점의 `authorizationCode` → refresh token 교환과, 탈퇴 시점의 revoke 호출은 모두
  best-effort다 — 실패해도 각각 로그인 성공/계정 삭제라는 주 흐름을 막지 않고 경고 로그만 남긴다.
- Apple client_secret JWT는 매 요청마다 즉석에서 새로 생성하고 캐싱하지 않는다.
- `authorizationCode`는 `POST /api/auth/apple`의 필수 필드다 — 이 기능(Apple 로그인) 자체가 아직
  배포되지 않았으므로(PR #71 미병합) 하위 호환을 고려하지 않는다.
- 관련 설계 문서: `docs/superpowers/specs/2026-09-04-apple-token-revocation-design.md`.

---

## Task 1: 문서 변경 (docs-first)

**Files:**
- Modify: `docs/api-contract.md`
- Modify: `docs/data-model.md`
- Modify: `backend/AGENTS.md`
- Modify: `mobile/AGENTS.md`

**Interfaces:** 없음 (문서 전용 작업, 이후 Task들이 여기서 정의한 필드명/동작 설명을 그대로
구현한다).

- [ ] **Step 1: `docs/api-contract.md`의 `POST /auth/apple` Flow에 토큰 교환 단계 추가**

기존:

```markdown
#### Flow

1. 모바일 앱이 `expo-apple-authentication`으로 Apple 로그인 시트를 띄웁니다.
2. Apple이 모바일 앱에 `identityToken`(JWT)과, 최초 인증 시에만 `fullName`을 반환합니다.
3. 모바일 앱이 `POST /api/auth/apple`로 `identityToken`과(최초 인증이면) `nickname`을 전달합니다.
4. 백엔드는 Apple의 공개키(JWKS, `https://appleid.apple.com/auth/keys`)로 `identityToken` 서명을 검증하고 `iss`(`https://appleid.apple.com`), `aud`(앱 번들 ID)를 검증합니다.
5. 백엔드는 `identityToken`의 `sub`를 `providerUserId`로, `email` 클레임을 이메일로 사용해 `provider = APPLE` 기준으로 Runvas 사용자를 조회하거나 생성합니다.
6. 백엔드는 Runvas 자체 `accessToken`, `user`, `isNewUser`를 모바일 앱에 반환합니다.
```

변경 후:

```markdown
#### Flow

1. 모바일 앱이 `expo-apple-authentication`으로 Apple 로그인 시트를 띄웁니다.
2. Apple이 모바일 앱에 `identityToken`(JWT), `authorizationCode`와, 최초 인증 시에만 `fullName`을 반환합니다.
3. 모바일 앱이 `POST /api/auth/apple`로 `identityToken`, `authorizationCode`와(최초 인증이면) `nickname`을 전달합니다.
4. 백엔드는 Apple의 공개키(JWKS, `https://appleid.apple.com/auth/keys`)로 `identityToken` 서명을 검증하고 `iss`(`https://appleid.apple.com`), `aud`(앱 번들 ID)를 검증합니다.
5. 백엔드는 `identityToken`의 `sub`를 `providerUserId`로, `email` 클레임을 이메일로 사용해 `provider = APPLE` 기준으로 Runvas 사용자를 조회하거나 생성합니다.
6. 백엔드는 `authorizationCode`를 Apple의 토큰 엔드포인트(`https://appleid.apple.com/auth/token`)와 교환해 Apple refresh token을 받아 저장합니다. 이 교환이 실패해도 로그인 자체는 계속 진행됩니다.
7. 백엔드는 Runvas 자체 `accessToken`, `user`, `isNewUser`를 모바일 앱에 반환합니다.
```

- [ ] **Step 2: Request Body 표와 예시 JSON에 `authorizationCode` 추가**

기존 표:

```markdown
| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | string | Y | `APPLE` |
| `identityToken` | string | Y | Apple이 발급한 identity token(JWT) |
| `nickname` | string | N | Apple이 최초 인증 시에만 제공하는 이름. 없으면 서버가 기본 닉네임을 생성 |
```

변경 후:

```markdown
| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | string | Y | `APPLE` |
| `identityToken` | string | Y | Apple이 발급한 identity token(JWT) |
| `authorizationCode` | string | Y | Apple이 로그인 시마다 발급하는 일회용 인가 코드. 탈퇴 시 Apple 토큰 해지에 쓰기 위해 백엔드가 저장한다 |
| `nickname` | string | N | Apple이 최초 인증 시에만 제공하는 이름. 없으면 서버가 기본 닉네임을 생성 |
```

기존 예시:

```json
{
  "provider": "APPLE",
  "identityToken": "apple_identity_token_jwt",
  "nickname": "Seoul Runner"
}
```

변경 후:

```json
{
  "provider": "APPLE",
  "identityToken": "apple_identity_token_jwt",
  "authorizationCode": "apple_authorization_code",
  "nickname": "Seoul Runner"
}
```

- [ ] **Step 3: `docs/api-contract.md`의 `DELETE /me` 설명에 revoke/unlink 언급 추가**

기존 문단(뒤에 이어지는 `#### Auth` 앞) 바로 뒤에 새 문단을 추가한다:

```markdown
회원 탈퇴를 신청합니다. 계정을 즉시 삭제하지 않고 30일 유예기간을 둡니다. 유예기간 중 같은 카카오
또는 Apple 계정으로 다시 로그인하면 자동으로 복구됩니다. 유예기간이 지나면 계정은 하드 삭제되고, 이 사용자가
작성한 코스·게시글·댓글은 삭제되지 않고 작성자 표시만 `"탈퇴한 사용자"`로 바뀝니다
(`profileImageUrl`/`bio`는 `null`).

하드 삭제 시점에 카카오 계정은 카카오 서버에 연동 해제(unlink)를, Apple 계정은 Apple 서버에
토큰 해지(revoke) 요청을 보냅니다. 두 요청 모두 best-effort로 처리되어, 실패해도 계정 삭제
자체는 진행됩니다.

#### Auth
```

- [ ] **Step 4: `docs/data-model.md`의 `User` 표에 `appleRefreshToken` 행 추가**

기존:

```markdown
| `providerUserId` | string | Y | 소셜 로그인 제공자의 사용자 ID. API 응답에는 노출하지 않는 내부 저장값 |
| `nickname` | string | Y | 공개 닉네임 |
```

변경 후:

```markdown
| `providerUserId` | string | Y | 소셜 로그인 제공자의 사용자 ID. API 응답에는 노출하지 않는 내부 저장값 |
| `appleRefreshToken` | string | N | Apple 계정 전용. 탈퇴 시 Apple 토큰 해지(revoke) 요청에 쓰는 내부 저장값. API 응답에는 노출하지 않음 |
| `nickname` | string | Y | 공개 닉네임 |
```

- [ ] **Step 5: `backend/AGENTS.md`의 "현재 확정된 인증 방향" 절 보강**

기존 마지막 두 줄:

```markdown
- Apple 로그인은 `POST /api/auth/apple`에서 모바일 앱이 보낸 `identityToken`(과 선택적 `nickname`)을
  받습니다.
- `identityToken`의 서명을 Apple JWKS로 검증하고, `sub`/`email`을 추출해 카카오 로그인과 동일한
  방식으로 Runvas JWT를 발급합니다.
```

변경 후(두 줄 뒤에 추가):

```markdown
- Apple 로그인은 `POST /api/auth/apple`에서 모바일 앱이 보낸 `identityToken`(과 선택적 `nickname`)을
  받습니다.
- `identityToken`의 서명을 Apple JWKS로 검증하고, `sub`/`email`을 추출해 카카오 로그인과 동일한
  방식으로 Runvas JWT를 발급합니다.
- Apple 로그인 요청에는 `authorizationCode`도 함께 받습니다. 백엔드는 이 값을 Apple 토큰
  엔드포인트와 교환해 얻은 refresh token을 저장해두고, 탈퇴 유예기간이 끝나 계정을 하드 삭제할
  때 Apple에 토큰 해지(revoke)를 요청합니다(`AccountPurgeService`, 카카오 unlink와 동일한
  best-effort 방식).
```

- [ ] **Step 6: `mobile/AGENTS.md`의 "현재 확정된 인증 방향" 절 보강**

기존 마지막 두 줄:

```markdown
- Apple 로그인은 `expo-apple-authentication`의 `signInAsync`로 `identityToken`(최초 인증 시에만
  `fullName`도 함께)을 받습니다.
- `identityToken`과 선택적 `nickname`을 `POST /api/auth/apple`로 백엔드에 보냅니다.
```

변경 후:

```markdown
- Apple 로그인은 `expo-apple-authentication`의 `signInAsync`로 `identityToken`, `authorizationCode`
  (최초 인증 시에만 `fullName`도 함께)를 받습니다.
- `identityToken`, `authorizationCode`와 선택적 `nickname`을 `POST /api/auth/apple`로 백엔드에
  보냅니다. `authorizationCode`는 탈퇴 시 Apple 토큰 해지에 쓰이므로 매 로그인마다 항상 함께
  보냅니다.
```

- [ ] **Step 7: 커밋**

```bash
git add docs/api-contract.md docs/data-model.md backend/AGENTS.md mobile/AGENTS.md
git commit -m "docs: Apple 로그인에 authorizationCode 필드와 탈퇴 시 revoke 동작 반영"
```

---

## Task 2: `apple_refresh_token` 저장 필드 추가

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__add_apple_refresh_token_to_users.sql`
- Modify: `backend/src/main/java/com/runvas/user/domain/User.java`
- Test: `backend/src/test/java/com/runvas/user/domain/UserTest.java`

**Interfaces:**
- Produces: `User.getAppleRefreshToken(): String`, `User.applyAppleRefreshToken(String): void` —
  Task 6(로그인 시 저장)과 Task 7(탈퇴 시 조회)이 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/user/domain/UserTest.java` 마지막 테스트 뒤에 추가:

```java
    @Test
    void applyAppleRefreshToken_저장하면_조회된다() {
        User user = User.createAppleUser("apple-sub-789", "runner@example.com", "Seoul Runner");

        assertThat(user.getAppleRefreshToken()).isNull();

        user.applyAppleRefreshToken("apple-refresh-token-value");

        assertThat(user.getAppleRefreshToken()).isEqualTo("apple-refresh-token-value");
    }
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: 컴파일 실패 — `cannot find symbol: method getAppleRefreshToken()` /
`method applyAppleRefreshToken(String)`

- [ ] **Step 3: 마이그레이션 파일 작성**

`backend/src/main/resources/db/migration/V17__add_apple_refresh_token_to_users.sql`:

```sql
ALTER TABLE users ADD COLUMN apple_refresh_token VARCHAR(2000);
```

- [ ] **Step 4: `User.java`에 필드/getter/setter 추가**

`private Instant deletedAt;` 선언 바로 뒤에 필드 추가:

```java
    @Column(length = 2000)
    private String appleRefreshToken;
```

`getDeletedAt()` 선언 바로 뒤에 getter 추가:

```java
    public String getAppleRefreshToken() { return appleRefreshToken; }
```

`updateProfile(...)` 메서드 뒤에 새 메서드 추가:

```java
    public void applyAppleRefreshToken(String appleRefreshToken) {
        this.appleRefreshToken = appleRefreshToken;
    }
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests "com.runvas.user.domain.UserTest"`
Expected: PASS (5개 테스트 모두 통과)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/resources/db/migration/V17__add_apple_refresh_token_to_users.sql \
        backend/src/main/java/com/runvas/user/domain/User.java \
        backend/src/test/java/com/runvas/user/domain/UserTest.java
git commit -m "feat(backend): Apple refresh token 저장 필드 추가"
```

---

## Task 3: Apple client_secret 생성기

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/AppleClientSecretGenerator.java`
- Test: `backend/src/test/java/com/runvas/auth/service/AppleClientSecretGeneratorTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: `AppleClientSecretGenerator.generate(): String` (ES256 서명된 JWT, 미설정 시
  `IllegalStateException`) — Task 4의 `AppleHttpTokenExchangeClient`와 Task 5의
  `AppleHttpRevokeClient`가 생성자로 주입받아 사용한다.
- 생성자 시그니처:
  `AppleClientSecretGenerator(String teamId, String keyId, String bundleId, String privateKeyPem)`
  (Spring이 `@Value("${runvas.apple.team-id}")` 등으로 주입)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/AppleClientSecretGeneratorTest.java`:

```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AppleClientSecretGeneratorTest {

    @Test
    void generate_필요한_클레임과_kid를_담아_ES256으로_서명한다() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String privateKeyPem = toPem(keyPair.getPrivate());

        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM123456",
                "KEY7890AB",
                "com.runvas.mobile",
                privateKeyPem
        );

        String clientSecret = generator.generate();

        Claims claims = Jwts.parser()
                .verifyWith((PublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret)
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
        assertThat(claims.getSubject()).isEqualTo("com.runvas.mobile");
        assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void generate_프라이빗_키가_설정되지_않았으면_예외() {
        AppleClientSecretGenerator generator =
                new AppleClientSecretGenerator("TEAM123456", "KEY7890AB", "com.runvas.mobile", "");

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
    }

    private static String toPem(PrivateKey privateKey) {
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleClientSecretGeneratorTest"`
Expected: 컴파일 실패 — `cannot find symbol: class AppleClientSecretGenerator`

- [ ] **Step 3: 구현**

`backend/src/main/java/com/runvas/auth/service/AppleClientSecretGenerator.java`:

```java
package com.runvas.auth.service;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleClientSecretGenerator {

    private final String teamId;
    private final String keyId;
    private final String bundleId;
    private final PrivateKey privateKey;

    public AppleClientSecretGenerator(
            @Value("${runvas.apple.team-id}") String teamId,
            @Value("${runvas.apple.key-id}") String keyId,
            @Value("${runvas.apple.bundle-id}") String bundleId,
            @Value("${runvas.apple.private-key}") String privateKeyPem
    ) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.bundleId = bundleId;
        this.privateKey = parsePrivateKeyOrNull(privateKeyPem);
    }

    public String generate() {
        if (privateKey == null || teamId == null || teamId.isBlank() || keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("Apple client secret is not configured");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("kid", keyId).and()
                .issuer(teamId)
                .subject(bundleId)
                .audience().add("https://appleid.apple.com").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private static PrivateKey parsePrivateKeyOrNull(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Apple private key", exception);
        }
    }
}
```

`privateKey`가 `null`인 경우(설정값이 비어있음, 즉 로컬/CI/기존 배포 환경의 기본 상태)를
생성자에서 예외 없이 허용하는 이유: 이 빈은 애플리케이션 시작 시 항상 생성되므로, 여기서 예외를
던지면 Apple 자격증명이 없는 모든 환경(테스트 포함)에서 Spring 컨텍스트 전체가 뜨지 못한다.
실제로 `generate()`가 호출되는 시점(로그인/탈퇴 처리)에만 실패하고, 그 호출부는 모두
best-effort로 감싸여 있다(Task 6, Task 7).

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleClientSecretGeneratorTest"`
Expected: PASS (2개 테스트 모두 통과)

- [ ] **Step 5: `application.yml`에 설정값 추가**

기존:

```yaml
  apple:
    jwks-uri: ${APPLE_JWKS_URI:https://appleid.apple.com/auth/keys}
    bundle-id: ${APPLE_BUNDLE_ID:com.runvas.mobile}
```

변경 후:

```yaml
  apple:
    jwks-uri: ${APPLE_JWKS_URI:https://appleid.apple.com/auth/keys}
    bundle-id: ${APPLE_BUNDLE_ID:com.runvas.mobile}
    token-uri: ${APPLE_TOKEN_URI:https://appleid.apple.com/auth/token}
    revoke-uri: ${APPLE_REVOKE_URI:https://appleid.apple.com/auth/revoke}
    team-id: ${APPLE_TEAM_ID:}
    key-id: ${APPLE_KEY_ID:}
    private-key: ${APPLE_PRIVATE_KEY:}
```

- [ ] **Step 6: 전체 빌드로 컨텍스트 로딩 확인 (설정값이 비어 있어도 기동되는지)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/service/AppleClientSecretGenerator.java \
        backend/src/test/java/com/runvas/auth/service/AppleClientSecretGeneratorTest.java \
        backend/src/main/resources/application.yml
git commit -m "feat(backend): Apple client_secret JWT 생성기 추가"
```

---

## Task 4: Apple 토큰 교환 클라이언트 (authorizationCode → refresh token)

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/AppleTokenExchangeClient.java`
- Create: `backend/src/main/java/com/runvas/auth/service/AppleHttpTokenExchangeClient.java`
- Test: `backend/src/test/java/com/runvas/auth/service/AppleHttpTokenExchangeClientTest.java`

**Interfaces:**
- Consumes: `AppleClientSecretGenerator.generate(): String` (Task 3)
- Produces: `AppleTokenExchangeClient.exchangeForRefreshToken(String authorizationCode): String`
  (실패 시 예외 던짐) — Task 6의 `AppleAuthService`가 주입받아 사용한다.

- [ ] **Step 1: 인터페이스 작성**

`backend/src/main/java/com/runvas/auth/service/AppleTokenExchangeClient.java`:

```java
package com.runvas.auth.service;

public interface AppleTokenExchangeClient {

    String exchangeForRefreshToken(String authorizationCode);
}
```

- [ ] **Step 2: 실패하는 테스트 작성 (HTTP 요청 형태까지 검증 — `MockRestServiceServer` 사용)**

이 클라이언트는 카카오의 `KakaoHttpUnlinkClient`(테스트 없음)나 `KakaoHttpAuthClient`(JSON 파싱
로직만 정적 메서드로 테스트)보다 한 단계 더 검증한다 — `client_id`/`code`/`grant_type` 등 실제
요청 폼 파라미터가 올바르게 실리는지까지 확인한다. `client_secret`은 매 호출 새로 서명되는 JWT라
값 자체는 검증하지 않고 필드 존재 여부만 확인한다.

`backend/src/test/java/com/runvas/auth/service/AppleHttpTokenExchangeClientTest.java`:

```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleHttpTokenExchangeClientTest {

    private MockRestServiceServer server;
    private AppleHttpTokenExchangeClient client;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
                "TEAM123456", "KEY7890AB", "com.runvas.mobile", testPrivateKeyPem());
        client = new AppleHttpTokenExchangeClient(
                builder, clientSecretGenerator, "https://appleid.apple.com/auth/token", "com.runvas.mobile");
    }

    @Test
    void exchangeForRefreshToken_필요한_파라미터를_담아_보내고_refresh_token을_반환한다() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("client_id=com.runvas.mobile");
                    assertThat(body).contains("code=auth-code-1");
                    assertThat(body).contains("grant_type=authorization_code");
                    assertThat(body).contains("client_secret=");
                })
                .andRespond(withSuccess(
                        "{\"refresh_token\":\"refresh-token-value\"}", MediaType.APPLICATION_JSON));

        String refreshToken = client.exchangeForRefreshToken("auth-code-1");

        assertThat(refreshToken).isEqualTo("refresh-token-value");
        server.verify();
    }

    @Test
    void exchangeForRefreshToken_응답에_refresh_token이_없으면_예외() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-token-value\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeForRefreshToken("auth-code-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exchangeForRefreshToken_Apple이_에러_응답을_주면_예외() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.exchangeForRefreshToken("auth-code-3"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String testPrivateKeyPem() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
```

- [ ] **Step 3: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleHttpTokenExchangeClientTest"`
Expected: 컴파일 실패 — `cannot find symbol: class AppleHttpTokenExchangeClient`

- [ ] **Step 4: 구현**

`backend/src/main/java/com/runvas/auth/service/AppleHttpTokenExchangeClient.java`:

```java
package com.runvas.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AppleHttpTokenExchangeClient implements AppleTokenExchangeClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String tokenUri;
    private final String bundleId;

    public AppleHttpTokenExchangeClient(
            RestClient.Builder restClientBuilder,
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${runvas.apple.token-uri}") String tokenUri,
            @Value("${runvas.apple.bundle-id}") String bundleId
    ) {
        this.restClient = restClientBuilder.build();
        this.clientSecretGenerator = clientSecretGenerator;
        this.tokenUri = tokenUri;
        this.bundleId = bundleId;
    }

    @Override
    public String exchangeForRefreshToken(String authorizationCode) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", bundleId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");

        String json = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("Apple token exchange failed with status " + res.getStatusCode());
                })
                .body(String.class);

        return parseRefreshToken(json);
    }

    static String parseRefreshToken(String json) {
        try {
            String token = OBJECT_MAPPER.readTree(json).path("refresh_token").asText();
            if (token.isBlank()) {
                throw new IllegalStateException("Apple token response missing refresh_token");
            }
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse Apple token response", exception);
        }
    }
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleHttpTokenExchangeClientTest"`
Expected: PASS (3개 테스트 모두 통과)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/service/AppleTokenExchangeClient.java \
        backend/src/main/java/com/runvas/auth/service/AppleHttpTokenExchangeClient.java \
        backend/src/test/java/com/runvas/auth/service/AppleHttpTokenExchangeClientTest.java
git commit -m "feat(backend): authorizationCode를 Apple refresh token으로 교환하는 클라이언트 추가"
```

---

## Task 5: Apple 토큰 해지(revoke) 클라이언트

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/AppleRevokeClient.java`
- Create: `backend/src/main/java/com/runvas/auth/service/AppleHttpRevokeClient.java`
- Test: `backend/src/test/java/com/runvas/auth/service/AppleHttpRevokeClientTest.java`

**Interfaces:**
- Consumes: `AppleClientSecretGenerator.generate(): String` (Task 3)
- Produces: `AppleRevokeClient.revoke(String refreshToken): void` (실패 시 예외 던짐) — Task 7의
  `AccountPurgeService`가 주입받아 사용한다.

이 요청은 성공해도 빈 응답 본문만 오므로 JSON 파싱 로직은 없지만, Task 4와 동일하게
`MockRestServiceServer`로 실제 요청 파라미터(`token`, `token_type_hint=refresh_token` 등)가
올바른지 검증한다 — 카카오의 `KakaoHttpUnlinkClient`에는 이런 테스트가 없지만, Apple revoke는
토큰 해지라는 되돌릴 수 없는 부수효과가 있는 호출이라 요청 형태를 직접 검증해둔다.

- [ ] **Step 1: 인터페이스 작성**

`backend/src/main/java/com/runvas/auth/service/AppleRevokeClient.java`:

```java
package com.runvas.auth.service;

public interface AppleRevokeClient {

    void revoke(String refreshToken);
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/auth/service/AppleHttpRevokeClientTest.java`:

```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleHttpRevokeClientTest {

    private MockRestServiceServer server;
    private AppleHttpRevokeClient client;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
                "TEAM123456", "KEY7890AB", "com.runvas.mobile", testPrivateKeyPem());
        client = new AppleHttpRevokeClient(
                builder, clientSecretGenerator, "https://appleid.apple.com/auth/revoke", "com.runvas.mobile");
    }

    @Test
    void revoke_필요한_파라미터를_담아_보낸다() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("client_id=com.runvas.mobile");
                    assertThat(body).contains("token=apple-refresh-token-value");
                    assertThat(body).contains("token_type_hint=refresh_token");
                    assertThat(body).contains("client_secret=");
                })
                .andRespond(withSuccess());

        client.revoke("apple-refresh-token-value");

        server.verify();
    }

    @Test
    void revoke_Apple이_에러_응답을_주면_예외() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.revoke("apple-refresh-token-value"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String testPrivateKeyPem() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
```

- [ ] **Step 3: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleHttpRevokeClientTest"`
Expected: 컴파일 실패 — `cannot find symbol: class AppleHttpRevokeClient`

- [ ] **Step 4: 구현**

`backend/src/main/java/com/runvas/auth/service/AppleHttpRevokeClient.java`:

```java
package com.runvas.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AppleHttpRevokeClient implements AppleRevokeClient {

    private final RestClient restClient;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String revokeUri;
    private final String bundleId;

    public AppleHttpRevokeClient(
            RestClient.Builder restClientBuilder,
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${runvas.apple.revoke-uri}") String revokeUri,
            @Value("${runvas.apple.bundle-id}") String bundleId
    ) {
        this.restClient = restClientBuilder.build();
        this.clientSecretGenerator = clientSecretGenerator;
        this.revokeUri = revokeUri;
        this.bundleId = bundleId;
    }

    @Override
    public void revoke(String refreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", bundleId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        restClient.post()
                .uri(revokeUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("Apple revoke failed with status " + res.getStatusCode());
                })
                .toBodilessEntity();
    }
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleHttpRevokeClientTest"`
Expected: PASS (2개 테스트 모두 통과)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/service/AppleRevokeClient.java \
        backend/src/main/java/com/runvas/auth/service/AppleHttpRevokeClient.java \
        backend/src/test/java/com/runvas/auth/service/AppleHttpRevokeClientTest.java
git commit -m "feat(backend): Apple 토큰 해지(revoke) 클라이언트 추가"
```

---

## Task 6: 로그인 시 Apple refresh token 저장 (`AppleAuthService` 통합)

**Files:**
- Modify: `backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java`
- Modify: `backend/src/main/java/com/runvas/auth/service/AppleAuthService.java`
- Modify: `backend/src/test/java/com/runvas/auth/service/AppleAuthServiceTest.java`
- Modify: `backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AppleTokenExchangeClient.exchangeForRefreshToken(String): String` (Task 4),
  `User.applyAppleRefreshToken(String): void` (Task 2)
- Produces: `AppleAuthService(AppleAuthClient, AppleTokenExchangeClient, UserRepository,
  JwtProvider)` 생성자 시그니처 — Spring DI가 자동 연결하므로 별도 설정 파일 변경은 필요 없다.

- [ ] **Step 1: `AppleLoginRequest.java`에 `authorizationCode` 필드 추가**

기존:

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

변경 후:

```java
package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        @NotBlank String authorizationCode,
        String nickname
) {
}
```

- [ ] **Step 2: `AppleAuthServiceTest.java`를 실패하는 상태로 재작성**

전체 파일을 다음으로 교체한다:

```java
package com.runvas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.runvas.auth.dto.AppleLoginRequest;
import com.runvas.auth.dto.AuthResponse;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AppleAuthServiceTest {

    @Test
    void login_신규_사용자면_생성하고_isNewUser는_true다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        AppleTokenExchangeClient appleTokenExchangeClient = mock(AppleTokenExchangeClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-1", "runner@example.com"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-1"))
                .thenReturn(Optional.empty());
        User savedUser = persisted(User.createAppleUser("apple-sub-1", "runner@example.com", "Seoul Runner"));
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(jwtProvider.createAccessToken(any())).thenReturn("jwt-token");

        AppleAuthService service =
                new AppleAuthService(appleAuthClient, appleTokenExchangeClient, userRepository, jwtProvider);
        AuthResponse response =
                service.login(new AppleLoginRequest("APPLE", "token-abc", "auth-code-1", "Seoul Runner"));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().nickname()).isEqualTo("Seoul Runner");
    }

    @Test
    void login_토큰_교환에_성공하면_refresh_token을_저장한다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        AppleTokenExchangeClient appleTokenExchangeClient = mock(AppleTokenExchangeClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-2", "runner@example.com"));
        User savedUser = persisted(User.createAppleUser("apple-sub-2", "runner@example.com", "Seoul Runner"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-2"))
                .thenReturn(Optional.of(savedUser));
        when(appleTokenExchangeClient.exchangeForRefreshToken("auth-code-2"))
                .thenReturn("apple-refresh-token-2");
        when(jwtProvider.createAccessToken(any())).thenReturn("jwt-token");

        AppleAuthService service =
                new AppleAuthService(appleAuthClient, appleTokenExchangeClient, userRepository, jwtProvider);
        service.login(new AppleLoginRequest("APPLE", "token-abc", "auth-code-2", null));

        assertThat(savedUser.getAppleRefreshToken()).isEqualTo("apple-refresh-token-2");
        verify(userRepository).save(savedUser);
    }

    @Test
    void login_토큰_교환이_실패해도_로그인은_성공한다() {
        AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
        AppleTokenExchangeClient appleTokenExchangeClient = mock(AppleTokenExchangeClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);

        when(appleAuthClient.verifyIdentityToken("token-abc"))
                .thenReturn(new AppleUserInfo("apple-sub-3", "runner@example.com"));
        User savedUser = persisted(User.createAppleUser("apple-sub-3", "runner@example.com", "Seoul Runner"));
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, "apple-sub-3"))
                .thenReturn(Optional.of(savedUser));
        when(appleTokenExchangeClient.exchangeForRefreshToken("auth-code-3"))
                .thenThrow(new IllegalStateException("apple token endpoint down"));
        when(jwtProvider.createAccessToken(any())).thenReturn("jwt-token");

        AppleAuthService service =
                new AppleAuthService(appleAuthClient, appleTokenExchangeClient, userRepository, jwtProvider);
        AuthResponse response =
                service.login(new AppleLoginRequest("APPLE", "token-abc", "auth-code-3", null));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(savedUser.getAppleRefreshToken()).isNull();
    }

    private static User persisted(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-06-22T08:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-06-22T08:00:00Z"));
        return user;
    }
}
```

- [ ] **Step 3: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleAuthServiceTest"`
Expected: 컴파일 실패 — `AppleAuthService`의 생성자가 4개 인자를 받지 않고,
`AppleLoginRequest`의 4번째 위치 인자가 없음

- [ ] **Step 4: `AppleAuthService.java` 수정**

전체 파일을 다음으로 교체한다:

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AppleAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppleAuthService.class);

    private final AppleAuthClient appleAuthClient;
    private final AppleTokenExchangeClient appleTokenExchangeClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AppleAuthService(
            AppleAuthClient appleAuthClient,
            AppleTokenExchangeClient appleTokenExchangeClient,
            UserRepository userRepository,
            JwtProvider jwtProvider
    ) {
        this.appleAuthClient = appleAuthClient;
        this.appleTokenExchangeClient = appleTokenExchangeClient;
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

        applyRefreshTokenIfExchangeSucceeds(loginResult.user(), request.authorizationCode());

        String accessToken = jwtProvider.createAccessToken(loginResult.user().getId());
        return new AuthResponse(accessToken, UserResponse.from(loginResult.user()), loginResult.isNewUser());
    }

    private void applyRefreshTokenIfExchangeSucceeds(User user, String authorizationCode) {
        try {
            String refreshToken = appleTokenExchangeClient.exchangeForRefreshToken(authorizationCode);
            user.applyAppleRefreshToken(refreshToken);
            userRepository.save(user);
        } catch (Exception exception) {
            log.warn("Apple refresh token exchange failed for user {}, proceeding without storing it",
                    user.getId(), exception);
        }
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

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests "com.runvas.auth.service.AppleAuthServiceTest"`
Expected: PASS (3개 테스트 모두 통과)

- [ ] **Step 6: `AuthControllerTest.java`에 `authorizationCode` 반영**

`import` 목록에 추가:

```java
import com.runvas.auth.service.AppleTokenExchangeClient;
```

`@MockBean AppleAuthClient appleAuthClient;` 바로 뒤에 필드 추가:

```java
    @MockBean
    AppleTokenExchangeClient appleTokenExchangeClient;
```

`appleLoginCreatesUserAndReturnsDocumentedResponse` 테스트의 요청 본문을 다음으로 교체:

```java
                        .content("""
                                {
                                  "provider": "APPLE",
                                  "identityToken": "apple-identity-token",
                                  "authorizationCode": "apple-authorization-code",
                                  "nickname": "Seoul Runner"
                                }
                                """))
```

`appleLoginRejectsUnsupportedProvider` 테스트의 요청 본문을 다음으로 교체:

```java
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "identityToken": "apple-identity-token",
                                  "authorizationCode": "apple-authorization-code",
                                  "nickname": "Seoul Runner"
                                }
                                """))
```

- [ ] **Step 7: 컨트롤러 테스트 실행 (Docker 필요, 없으면 자동 skip)**

Run: `./gradlew test --tests "com.runvas.auth.controller.AuthControllerTest"`
Expected: Docker가 있으면 PASS, 없으면 `@Testcontainers(disabledWithoutDocker = true)`에 의해
skip(빌드 실패 아님)

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/runvas/auth/dto/AppleLoginRequest.java \
        backend/src/main/java/com/runvas/auth/service/AppleAuthService.java \
        backend/src/test/java/com/runvas/auth/service/AppleAuthServiceTest.java \
        backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java
git commit -m "feat(backend): Apple 로그인 시 authorizationCode로 refresh token 교환해 저장"
```

---

## Task 7: 탈퇴 시 Apple 토큰 해지 (`AccountPurgeService` 통합)

**Files:**
- Modify: `backend/src/main/java/com/runvas/user/service/AccountPurgeService.java`
- Modify: `backend/src/test/java/com/runvas/user/service/AccountPurgeServiceTest.java`

**Interfaces:**
- Consumes: `AppleRevokeClient.revoke(String): void` (Task 5), `User.getAppleRefreshToken():
  String` (Task 2)
- Produces: `AccountPurgeService(UserRepository, LikeService, BookmarkRepository,
  KakaoUnlinkClient, AppleRevokeClient)` 생성자 시그니처 — `AccountPurgeScheduler`는 Spring DI로
  자동 연결되므로 수정할 필요 없다.

- [ ] **Step 1: `AccountPurgeServiceTest.java`를 실패하는 상태로 재작성**

전체 파일을 다음으로 교체한다:

```java
package com.runvas.user.service;

import com.runvas.auth.service.AppleRevokeClient;
import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeService;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    private final LikeService likeService = mock(LikeService.class);
    private final BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
    private final KakaoUnlinkClient kakaoUnlinkClient = mock(KakaoUnlinkClient.class);
    private final AppleRevokeClient appleRevokeClient = mock(AppleRevokeClient.class);
    private final AccountPurgeService accountPurgeService = new AccountPurgeService(
            userRepository, likeService, bookmarkRepository, kakaoUnlinkClient, appleRevokeClient);

    private static User kakaoUser(String providerUserId) {
        User user = User.createKakaoUser(providerUserId, null, "탈퇴예정", null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.markWithdrawn();
        return user;
    }

    private static User appleUser(String providerUserId, String appleRefreshToken) {
        User user = User.createAppleUser(providerUserId, null, "탈퇴예정");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        if (appleRefreshToken != null) {
            user.applyAppleRefreshToken(appleRefreshToken);
        }
        user.markWithdrawn();
        return user;
    }

    @Test
    void purgesExpiredKakaoUserAfterUnlinkingAndDeletingLikesAndBookmarks() {
        User expired = kakaoUser("kakao-expired");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient).unlink("kakao-expired");
        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void skipsUnlinkForDevProvider() {
        User devUser = User.createDevUser("dev-nickname");
        ReflectionTestUtils.setField(devUser, "id", UUID.randomUUID());
        devUser.markWithdrawn();
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(devUser));

        accountPurgeService.purgeExpiredAccounts();

        verify(kakaoUnlinkClient, never()).unlink(anyString());
        verify(appleRevokeClient, never()).revoke(anyString());
        verify(userRepository).delete(devUser);
    }

    @Test
    void continuesDeletionWhenUnlinkFails() {
        User expired = kakaoUser("kakao-unlink-fails");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));
        doThrow(new IllegalStateException("kakao down")).when(kakaoUnlinkClient).unlink("kakao-unlink-fails");

        accountPurgeService.purgeExpiredAccounts();

        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void purgesExpiredAppleUserAfterRevokingAndDeletingLikesAndBookmarks() {
        User expired = appleUser("apple-expired", "apple-refresh-token-value");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(appleRevokeClient).revoke("apple-refresh-token-value");
        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }

    @Test
    void skipsRevokeWhenAppleRefreshTokenMissing() {
        User expired = appleUser("apple-no-token", null);
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));

        accountPurgeService.purgeExpiredAccounts();

        verify(appleRevokeClient, never()).revoke(anyString());
        verify(userRepository).delete(expired);
    }

    @Test
    void continuesDeletionWhenAppleRevokeFails() {
        User expired = appleUser("apple-revoke-fails", "apple-refresh-token-value");
        when(userRepository.findByDeletedAtLessThanEqual(any(Instant.class))).thenReturn(List.of(expired));
        doThrow(new IllegalStateException("apple down")).when(appleRevokeClient).revoke("apple-refresh-token-value");

        accountPurgeService.purgeExpiredAccounts();

        verify(likeService).unlikeAllByUser(expired.getId().toString());
        verify(bookmarkRepository).deleteAllByIdUserId(expired.getId().toString());
        verify(userRepository).delete(expired);
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests "com.runvas.user.service.AccountPurgeServiceTest"`
Expected: 컴파일 실패 — `AccountPurgeService` 생성자가 5개 인자를 받지 않음,
`User.applyAppleRefreshToken`은 이미 있으므로(Task 2) 이 부분은 실패하지 않음

- [ ] **Step 3: `AccountPurgeService.java` 수정**

전체 파일을 다음으로 교체한다:

```java
package com.runvas.user.service;

import com.runvas.auth.service.AppleRevokeClient;
import com.runvas.auth.service.KakaoUnlinkClient;
import com.runvas.backend.community.BookmarkRepository;
import com.runvas.backend.community.LikeService;
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
    private final LikeService likeService;
    private final BookmarkRepository bookmarkRepository;
    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final AppleRevokeClient appleRevokeClient;

    public AccountPurgeService(
            UserRepository userRepository,
            LikeService likeService,
            BookmarkRepository bookmarkRepository,
            KakaoUnlinkClient kakaoUnlinkClient,
            AppleRevokeClient appleRevokeClient
    ) {
        this.userRepository = userRepository;
        this.likeService = likeService;
        this.bookmarkRepository = bookmarkRepository;
        this.kakaoUnlinkClient = kakaoUnlinkClient;
        this.appleRevokeClient = appleRevokeClient;
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
        } else if (user.getProvider() == AuthProvider.APPLE && user.getAppleRefreshToken() != null) {
            try {
                appleRevokeClient.revoke(user.getAppleRefreshToken());
            } catch (Exception exception) {
                log.warn("Apple revoke failed for user {}, proceeding with deletion", user.getId(), exception);
            }
        }
        likeService.unlikeAllByUser(user.getId().toString());
        bookmarkRepository.deleteAllByIdUserId(user.getId().toString());
        userRepository.delete(user);
    }
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests "com.runvas.user.service.AccountPurgeServiceTest"`
Expected: PASS (6개 테스트 모두 통과)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/runvas/user/service/AccountPurgeService.java \
        backend/src/test/java/com/runvas/user/service/AccountPurgeServiceTest.java
git commit -m "feat(backend): 탈퇴 유예기간 만료 시 Apple 토큰 해지 요청 추가"
```

---

## Task 8: 모바일 — `authorizationCode` 전달

**Files:**
- Modify: `mobile/src/services/authApi.ts`
- Modify: `mobile/src/contexts/AuthContext.tsx`

**Interfaces:**
- Produces: `postAuthApple(identityToken: string, authorizationCode: string, nickname: string |
  null): Promise<AuthResponse>` — Task 1에서 문서화한 `POST /api/auth/apple` 요청 본문과 일치.

- [ ] **Step 1: `authApi.ts`의 `postAuthApple` 시그니처 변경**

기존:

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

변경 후:

```ts
export async function postAuthApple(
  identityToken: string,
  authorizationCode: string,
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
      authorizationCode,
      nickname,
    }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as AuthResponse;
}
```

- [ ] **Step 2: 타입 체크 실행해 호출부 불일치 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: FAIL — `AuthContext.tsx`에서 `postAuthApple` 호출 시 "Expected 3 arguments, but got 2."

- [ ] **Step 3: `AuthContext.tsx`의 `appleLogin` 호출부 수정**

기존:

```ts
      if (!credential.identityToken) {
        throw new Error('Apple 로그인에 실패했습니다.');
      }
      const nickname = credential.fullName?.givenName ?? null;
      const result = await postAuthApple(credential.identityToken, nickname);
```

변경 후:

```ts
      if (!credential.identityToken || !credential.authorizationCode) {
        throw new Error('Apple 로그인에 실패했습니다.');
      }
      const nickname = credential.fullName?.givenName ?? null;
      const result = await postAuthApple(
        credential.identityToken,
        credential.authorizationCode,
        nickname,
      );
```

- [ ] **Step 4: 타입 체크 실행해 통과 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음(종료 코드 0)

- [ ] **Step 5: 커밋**

```bash
git add mobile/src/services/authApi.ts mobile/src/contexts/AuthContext.tsx
git commit -m "feat(mobile): Apple 로그인 시 authorizationCode도 백엔드에 전달"
```

---

## Task 9: 전체 검증

**Files:** 없음(검증 전용, 코드 변경 없음)

- [ ] **Step 1: 백엔드 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. `AuthControllerTest` 등 `@Testcontainers(disabledWithoutDocker =
true)` 테스트는 로컬에 Docker가 없으면 자동으로 skip되며 빌드 실패로 이어지지 않는다. Docker가
있는 환경(CI 포함)에서는 실제로 통과하는지 확인한다.

- [ ] **Step 2: 모바일 타입 체크 재확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: `docs/api-contract.md` 예시와 실제 동작 일치 여부 재확인**

`POST /auth/apple`의 요청 예시(Task 1에서 수정)에 있는 필드가 `AppleLoginRequest`(Task 6)의
필드와 정확히 일치하는지, 응답 예시가 `UserResponse`/`AuthResponse` 필드와 일치하는지 육안으로
대조한다. 불일치가 있으면 문서를 수정하고 별도로 커밋한다(`docs: ...`).

- [ ] **Step 4: 남은 수동 검증 항목 확인(자동화 불가, 사용자 확인 필요)**

다음 항목은 실제 Apple Developer 계정 자격증명(Team ID, Key ID, `.p8` 키)이 있어야 확인할 수
있어 이 플랜의 자동화된 단계로 수행할 수 없다 — 설계 문서의 "테스트" 절 참고:

- 배포 환경에 `APPLE_TEAM_ID`/`APPLE_KEY_ID`/`APPLE_PRIVATE_KEY` 실제 값을 설정.
- 실기기에서 Apple 로그인 후 `users.apple_refresh_token`이 채워지는지 DB에서 직접 확인.
- 테스트 Apple 계정으로 탈퇴 유예기간을 우회(또는 대기)해 `AccountPurgeScheduler` 실행 후 Apple
  쪽에서 실제로 연결이 해지되는지 확인(Apple ID 설정 > 로그인 및 보안 > Sign in with Apple을
  사용하는 앱에서 Runvas가 사라지는지).

이 단계는 커밋하지 않는다 — 사용자에게 결과를 보고하고 배포 체크리스트로 남긴다.
