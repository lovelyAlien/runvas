# Apple 계정 삭제 시 토큰 해지(Revoke) 설계

작성일: 2026-09-04
관련 문서: `docs/api-contract.md` §POST /auth/apple, §DELETE /me; `docs/data-model.md` §User;
`feat/apple-sign-in`(PR #71) — 이번 기능은 이 브랜치를 베이스로 한다.

## 배경

App Store Review Guideline 4.8 재제출 대응으로 `feat/apple-sign-in`(PR #71, 아직 미병합)에서
Sign in with Apple을 추가했다. 브랜치 전체 리뷰에서 다음 gap이 발견됐다: 카카오 계정은 탈퇴
유예기간(30일) 만료 시 `AccountPurgeService.purgeOne()`이 카카오 서버에 연동 해제(unlink)를
요청하는데(`user.getProvider() == AuthProvider.KAKAO` 분기), Apple 계정은 이 분기가 없어
DB에서만 삭제되고 Apple 쪽 인증 연결은 살아있게 된다.

App Store Review Guideline 5.1.1(v)는 Sign in with Apple을 제공하면서 계정 삭제 기능도
제공하는 앱은, 계정 삭제 시 Apple의 Sign in with Apple REST API로 토큰을 해지(revoke)할 것을
요구한다. 이번 4.8 재제출이 통과되더라도 이 gap 때문에 다음 심사에서 5.1.1(v)로 재거부될 위험이
있다.

## 정책 결정 (브레인스토밍 확정 사항)

- **저장 방식**: Apple refresh token은 암호화 없이 평문 컬럼으로 저장한다. 이 코드베이스에는
  필드 단위 암호화 선례가 전혀 없고(이메일, `providerUserId` 등 모두 평문), 이번 기능만을 위해
  새 암호화 키 관리·로테이션 체계를 도입하는 건 범위를 벗어난다. DB 접근 통제가 기존과 동일한
  신뢰 경계다.
- **로그인 시점 토큰 교환은 best-effort**: `authorizationCode` → Apple refresh token 교환이
  실패해도(Apple API 장애 등) 로그인 자체는 성공시킨다. 카카오 unlink 실패가 탈퇴 처리를 막지
  않는 것과 동일한 완화 전략이다.
- **탈퇴(하드 삭제) 시점 revoke도 best-effort**: `AccountPurgeService.purgeOne()`의 기존 카카오
  분기와 완전히 대칭인 구조로 Apple 분기를 추가한다. revoke 실패는 로그만 남기고 삭제를 막지
  않는다.
- **`authorizationCode`는 필수 필드로 추가**: Apple 로그인 기능 자체가 아직 배포되지 않았으므로
  (PR #71 미병합) 하위 호환을 고려할 필요가 없다.
- **카카오 unlink 동작도 이번에 문서화**: 지금 `docs/`에는 카카오 unlink에 대한 언급이 전혀
  없다(순수 구현 세부사항으로만 존재). Apple revoke를 추가하며 `DELETE /me` 설명에 두 provider
  모두 한 줄로 명시한다 — 카카오 쪽 동작 자체를 바꾸는 것은 아니다.
- **client_secret JWT는 매 요청마다 새로 생성, 캐싱하지 않음**: Apple은 client_secret의 `exp`를
  최대 6개월까지 허용하지만, 여기서는 요청 시점마다 즉석에서 만들고 만료를 몇 분으로 짧게 잡는다.
  캐싱/로테이션 로직이 필요 없어 구현이 단순해진다(서명 자체는 가벼운 연산).

## Apple REST API 참고 (2026-09-04 기준 조사)

- **토큰 교환**: `POST https://appleid.apple.com/auth/token`, `application/x-www-form-urlencoded`로
  `client_id`(=번들 ID), `client_secret`(JWT), `code`(=`authorizationCode`),
  `grant_type=authorization_code`. 응답 JSON에 `access_token`/`refresh_token`/`id_token` 포함.
  Apple refresh token은 revoke되거나 사용자가 Apple ID 설정에서 앱 연결을 끊기 전까지 만료되지
  않는다 — 별도 주기적 갱신이 필요 없다.
- **토큰 해지**: `POST https://appleid.apple.com/auth/revoke`, 동일한 form-urlencoded로
  `client_id`, `client_secret`, `token`(=저장해둔 refresh token), `token_type_hint=refresh_token`.
  성공 시 `200 OK` (빈 응답 본문).
- **client_secret JWT**: 헤더 `{alg: ES256, kid: <Key ID>}`, 페이로드
  `{iss: <Team ID>, sub: <번들 ID>, aud: "https://appleid.apple.com", iat, exp}`.
  `.p8` 프라이빗 키로 ES256 서명. `exp`는 `iat` 기준 최대 6개월(15,777,000초)까지만 허용된다.

## 데이터 모델 변경

### `docs/data-model.md`의 `User` 표에 추가

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `appleRefreshToken` | string | N | Apple 계정 전용. 탈퇴 시 Apple 토큰 해지(revoke) 요청에 쓰는 내부 저장값. API 응답에는 노출하지 않음 |

### `users` 테이블 마이그레이션 (신규, `V17__add_apple_refresh_token_to_users.sql`)

```sql
ALTER TABLE users ADD COLUMN apple_refresh_token VARCHAR(2000);
```

Apple/Kakao/Dev 사용자 모두 이 컬럼을 공유하되 Apple 사용자만 값이 채워진다. `NOT NULL` 제약을
두지 않는다 — 교환이 실패했거나 이 기능 배포 전에 가입한 Apple 사용자는 계속 `NULL`이다.

## API 계약 변경 (모바일 ↔ 백엔드)

### `docs/api-contract.md`의 `POST /auth/apple` 변경

Flow에 다음 단계를 추가한다(기존 5단계 다음, 6단계였던 "Runvas JWT 반환"은 7단계로 밀림):

> 6. 백엔드는 `authorizationCode`를 Apple의 토큰 엔드포인트(`https://appleid.apple.com/auth/token`)와
>    교환해 Apple refresh token을 받아 저장한다. 이 교환이 실패해도 로그인 자체는 계속 진행된다.

Request Body 표에 필드 추가:

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `authorizationCode` | string | Y | Apple이 로그인 시마다 발급하는 일회용 인가 코드. 탈퇴 시 Apple 토큰 해지에 사용하기 위해 백엔드가 저장한다 |

예시 요청 본문에도 `authorizationCode` 추가. 응답 본문(`AuthResponse`)은 변경 없음 — refresh
token은 내부 저장값이라 API 응답에 절대 포함하지 않는다.

Errors에 항목 추가하지 않는다 — `authorizationCode` 교환 실패는 로그인 실패로 이어지지 않으므로
새 에러 케이스가 생기지 않는다. (단, `authorizationCode` 필드 자체가 누락되면 기존
`400 VALIDATION_ERROR: 필수 필드 누락`에 해당한다.)

### `docs/api-contract.md`의 `DELETE /me` 설명 보강

기존 "유예기간이 지나면 계정은 하드 삭제되고 ..." 문단 뒤에 한 줄 추가:

> 하드 삭제 시점에 카카오 계정은 카카오 서버에 연동 해제(unlink)를, Apple 계정은 Apple 서버에
> 토큰 해지(revoke) 요청을 보낸다. 두 요청 모두 best-effort로 처리되어, 실패해도 계정 삭제
> 자체는 진행된다.

## 백엔드 구현

### `AppleClientSecretGenerator` (신규, `com.runvas.auth.service`)

```java
@Component
public class AppleClientSecretGenerator {

    private final String teamId;
    private final String keyId;
    private final String bundleId;
    private final PrivateKey privateKey; // .p8(PKCS8) 파싱, ES256

    public String generate() {
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
}
```

`AppleHttpAuthClient`가 이미 쓰는 `io.jsonwebtoken`(jjwt)을 그대로 재사용한다 — 새 의존성 불필요.
`AppleHttpTokenExchangeClient`와 `AppleHttpRevokeClient` 둘 다 이 컴포넌트를 주입받아 매 요청마다
새 client_secret을 만든다.

### `AppleTokenExchangeClient` / `AppleHttpTokenExchangeClient` (신규)

`KakaoUnlinkClient`/`KakaoHttpUnlinkClient`와 동일하게 인터페이스+구현체로 분리(테스트에서 mock
하기 쉽게).

```java
public interface AppleTokenExchangeClient {
    String exchangeForRefreshToken(String authorizationCode);
}

@Component
public class AppleHttpTokenExchangeClient implements AppleTokenExchangeClient {
    // POST {tokenUri} (form-urlencoded)
    // client_id=bundleId, client_secret=clientSecretGenerator.generate(),
    // code=authorizationCode, grant_type=authorization_code
    // 응답 JSON에서 refresh_token 파싱, 없으면 예외
}
```

### `AppleLoginRequest.java` 수정

```java
public record AppleLoginRequest(
        @NotBlank String provider,
        @NotBlank String identityToken,
        @NotBlank String authorizationCode,
        String nickname
) {
}
```

### `AppleAuthService.login()` 수정

로그인 결과(`loginResult`)를 만든 뒤, `accessToken` 생성 전에 best-effort로 토큰 교환을 시도한다
(카카오 unlink와 동일한 try/catch/log.warn 패턴):

```java
try {
    String refreshToken = appleTokenExchangeClient.exchangeForRefreshToken(request.authorizationCode());
    loginResult.user().applyAppleRefreshToken(refreshToken);
    userRepository.save(loginResult.user());
} catch (Exception exception) {
    log.warn("Apple refresh token exchange failed for user {}, proceeding without storing it",
            loginResult.user().getId(), exception);
}
```

### `AppleRevokeClient` / `AppleHttpRevokeClient` (신규)

```java
public interface AppleRevokeClient {
    void revoke(String refreshToken);
}

@Component
public class AppleHttpRevokeClient implements AppleRevokeClient {
    // POST {revokeUri} (form-urlencoded)
    // client_id=bundleId, client_secret=clientSecretGenerator.generate(),
    // token=refreshToken, token_type_hint=refresh_token
}
```

### `AccountPurgeService.purgeOne()` 수정

기존 카카오 분기 옆에 Apple 분기를 추가한다:

```java
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
```

`appleRefreshToken == null`인 경우(교환 실패했거나 이 기능 배포 전 가입) revoke 호출 자체를
생략한다 — 카카오가 admin key 미설정 시 건너뛰는 것과 같은 층위의 방어.

### `User.java` 수정

```java
@Column(length = 2000)
private String appleRefreshToken;

public String getAppleRefreshToken() { return appleRefreshToken; }

public void applyAppleRefreshToken(String appleRefreshToken) {
    this.appleRefreshToken = appleRefreshToken;
}
```

### `application.yml` 수정

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

`APPLE_PRIVATE_KEY`는 `.p8` 파일의 PEM 내용을 담는다. 여러 줄인 PEM을 환경변수 하나에 안전하게
넣는 방법(예: base64 인코딩 후 디코드, 또는 `\n` 리터럴을 실제 개행으로 치환)은 배포 환경
(`docs/deployment.md`, CI 시크릿)에 맞춰 구현 시점에 정한다 — 이 설계 문서의 범위는 애플리케이션
쪽 파싱 로직까지다.

## 모바일 구현

### `mobile/src/contexts/AuthContext.tsx`의 `appleLogin()` 수정

`credential.authorizationCode`를 꺼내 `postAuthApple`에 함께 전달한다(`identityToken`처럼 매
로그인마다 Apple이 내려주는 값 — `fullName`과 달리 최초 인증 시에만 오는 값이 아니다).

```ts
const result = await postAuthApple(
  credential.identityToken,
  credential.authorizationCode,
  nickname,
);
```

### `mobile/src/services/authApi.ts`의 `postAuthApple()` 수정

```ts
export async function postAuthApple(
  identityToken: string,
  authorizationCode: string,
  nickname: string | null,
): Promise<AuthResponse> {
  // ...
  body: JSON.stringify({
    provider: 'APPLE',
    identityToken,
    authorizationCode,
    nickname,
  }),
  // ...
}
```

### `mobile/AGENTS.md`의 "현재 확정된 인증 방향" 절 보강

Apple 로그인 설명에 `authorizationCode`도 함께 보낸다는 내용을 추가한다.

## 에러 처리 / 엣지 케이스

| 상황 | 처리 |
| --- | --- |
| `authorizationCode` 필드 누락 | `400 VALIDATION_ERROR` (기존 "필수 필드 누락" 규칙 그대로 적용) |
| Apple 토큰 교환 API 장애/타임아웃 | 로그인은 성공, `apple_refresh_token`은 저장되지 않음(기존 값이 있었다면 그대로 유지) → 이후 탈퇴 시 revoke 생략 |
| Apple revoke API 장애/타임아웃 | 경고 로그만 남기고 계정 삭제는 정상 진행 |
| `apple_refresh_token`이 없는 상태로 탈퇴 처리됨 | revoke 호출 자체를 생략(카카오의 admin key 미설정 케이스와 동일한 방어 층위) |
| 유예기간 중 같은 Apple 계정으로 재로그인해 복구 | 재로그인 시 `authorizationCode`가 다시 오므로 `apple_refresh_token`도 새로 교환되어 갱신됨(기존 값 덮어씀) |

## 범위 제외

- `apple_refresh_token` 필드 암호화 저장 (앞선 정책 결정에 따라 평문)
- Apple refresh token의 주기적 갱신/유효성 점검 배치 (revoke 전까지 만료되지 않으므로 불필요)
- 토큰 교환 응답의 `access_token`/`id_token` 저장 — revoke에는 refresh token만 필요하므로 버림
- client_secret JWT 캐싱/재사용 — 매 요청 즉석 생성
- 카카오 unlink 로직 자체 변경 — 문서화만 보강, 기존 동작 유지

## 테스트

- **백엔드** (`./gradlew test`):
  - `AppleClientSecretGeneratorTest`(신규): 생성된 JWT의 `iss`/`sub`/`aud`/`kid` 클레임과 ES256
    서명이 올바른지 검증.
  - `AppleHttpTokenExchangeClientTest`/`AppleHttpRevokeClientTest`(신규): `KakaoHttpUnlinkClient`
    테스트와 동일한 패턴으로 mock 서버 성공/실패 응답에 대한 동작 검증.
  - `AppleAuthServiceTest`: 토큰 교환 성공 시 `apple_refresh_token`이 저장되는지, 교환이 실패해도
    로그인 자체(`accessToken` 반환)는 성공하는지 검증.
  - `AccountPurgeServiceTest`: Apple 사용자 탈퇴 시 저장된 refresh token으로 `AppleRevokeClient
    .revoke()`가 호출되는지, revoke가 실패해도 삭제가 계속되는지, `apple_refresh_token`이
    `null`이면 revoke 호출 자체가 생략되는지 — 기존 Kakao 케이스들과 대칭으로 추가.
- **모바일**: `npx tsc --noEmit` 통과. `postAuthApple` 시그니처 변경이 `AuthContext.tsx` 호출부와
  맞는지 타입 레벨에서 확인 (jest 등 러너 미설정 상태는 기존과 동일).
- **실물 검증(수동, Apple 개발자 자격증명 필요)**: 실제 Team ID/Key ID/`.p8` 키가 준비된 뒤,
  실기기에서 Apple 로그인 → `apple_refresh_token`이 DB에 채워지는지 확인. Revoke 자체는 30일
  유예기간을 거치는 스케줄러 경로라 자동화 테스트로는 로직만 검증하고, 실 서버향 revoke 호출은
  테스트 Apple 계정으로 유예기간을 우회하는 별도 수동 확인이 필요하다(자동으로 수행할 수 없음).

## 검증 기준

- `POST /api/auth/apple`에 `authorizationCode`를 포함해 로그인하면 `users.apple_refresh_token`이
  채워진다.
- 탈퇴 유예기간(30일) 경과 후 스케줄러가 Apple 사용자를 purge할 때 `AppleRevokeClient.revoke()`가
  저장된 refresh token으로 호출된다.
- revoke 호출이 실패해도 계정은 정상적으로 하드 삭제된다(예외가 전파되어 배치 전체가 멈추지
  않는다).
- `apple_refresh_token`이 없는 Apple 사용자를 purge해도 예외 없이 삭제가 완료된다.
- `docs/api-contract.md`의 `POST /auth/apple` 예시 요청/응답과 실제 구현 동작이 일치한다
  (응답 바디는 기존과 동일, 요청 바디에만 `authorizationCode`가 추가됨).
