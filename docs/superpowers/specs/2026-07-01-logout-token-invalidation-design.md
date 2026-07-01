# 로그아웃 / 토큰 무효화 설계

작성일: 2026-07-01
관련 문서: `docs/api-contract.md` §Auth APIs, `mobile/AGENTS.md`, `docs/superpowers/specs/2026-06-29-mobile-kakao-auth-design.md`

## 배경

현재 Runvas `accessToken`은 순수 stateless JWT다 (`JwtProvider`, 기본 만료 1시간).
서버는 서명만 검증할 뿐 상태를 저장하지 않으므로, 로그아웃해도 토큰 자체는 만료 시각까지 계속 유효하다.
모바일 `AuthContext.logout()`은 이미 정의돼 있지만 `SecureStore`만 지울 뿐 백엔드 호출이 없다.

이 설계는 "로그아웃 시 서버가 해당 토큰을 즉시 무효화한다"는 요구사항을 구현 범위로 정의한다.

## 목표

- 로그아웃한 `accessToken`은 자연 만료 전이라도 이후 요청에서 거부된다.
- 무효화 범위는 로그아웃에 사용된 **그 토큰 하나**로 한정한다 (동일 사용자의 다른 기기 세션은 유지).
- 모바일은 백엔드 로그아웃이 성공한 경우에만 로컬 토큰/사용자 정보를 지운다.

## 아키텍처: Redis 블랙리스트

JWT는 상태가 없으므로 만료 전 강제 무효화에는 별도 저장소가 필요하다.
`backend`에는 이미 Redis 의존성이 있다(현재는 T-Map 보행자 경로 탐색 캐시 용도, `RoutingService`).
같은 인프라를 재사용해 로그아웃된 토큰을 블랙리스트로 저장한다.

- **로그아웃 시**: 토큰의 `exp` claim에서 남은 만료 시간을 계산해, Redis에
  `auth:blacklist:<token>` 키를 `TTL = exp - now`로 저장한다. 자연 만료 시점에 Redis 키도 함께 사라지므로
  블랙리스트가 무한히 쌓이지 않는다. 이미 만료된 토큰이면 저장하지 않는다(무의미).
- **인증 시**: `JwtAuthenticationFilter`가 서명 검증에 성공한 뒤, `SecurityContext`를 설정하기 전에
  Redis에서 해당 토큰이 블랙리스트에 있는지 확인한다. 있으면 서명이 유효한 토큰이라도 무효 토큰과
  동일하게 `401 UNAUTHORIZED`로 응답하고 필터 체인을 중단한다.

Redis 장애 시 fail-open/fail-closed 전략은 이번 범위에서 다루지 않는다 (Redis는 이미 라우팅 캐시로
운영 중인 필수 인프라로 취급한다).

## API 계약

`docs/api-contract.md` §Auth APIs에 다음 엔드포인트를 추가한다.

### POST /auth/logout

로그아웃하고, 요청에 사용된 `accessToken`을 서버에서 무효화한다.

**Auth**: `Required`

**Request Body**: 없음 (토큰은 `Authorization` 헤더에서 가져옴)

**Response**: `204 No Content` (본문 없음)

**Errors**:
- `401 UNAUTHORIZED`: 로그인하지 않았거나 토큰이 유효하지 않음. 이미 로그아웃된(블랙리스트된) 토큰으로
  재요청한 경우도 동일하게 처리한다 — 별도 에러 코드를 만들지 않는다.

실제 경로: `POST /api/auth/logout` (`/api` prefix).

## 백엔드 구현

### `JwtProvider`

`parseUserId` 외에 토큰의 만료 시각을 꺼내는 메서드를 추가한다 (예: `getExpiration(String token): Instant`).

### `TokenBlacklistService` (신규)

- `blacklist(String token)`: `JwtProvider.getExpiration`으로 남은 초를 계산해
  `redisTemplate.opsForValue().set("auth:blacklist:" + token, "1", Duration.ofSeconds(remaining))` 실행.
  남은 시간이 0 이하면 저장하지 않는다.
- `isBlacklisted(String token): boolean`: `redisTemplate.hasKey("auth:blacklist:" + token)`.

`RedisTemplate<String, String>`을 사용한다 (`RoutingService`가 쓰는 것과 동일한 Redis 인스턴스).

### `JwtAuthenticationFilter`

`parseUserId` 성공 직후, `SecurityContextHolder`에 인증 정보를 설정하기 전에
`tokenBlacklistService.isBlacklisted(token)`을 확인한다. `true`면 기존 `JwtException` 처리 분기와
동일하게 `SecurityContextHolder.clearContext()` + `errorResponseWriter.write(response, ErrorCode.UNAUTHORIZED)` 후 반환한다.

### `AuthController` / `AuthLogoutService` (신규)

- `POST /api/auth/logout` 추가.
- 컨트롤러는 인증된 `Authentication.getCredentials()`에서 원본 토큰 문자열을 꺼낸다
  (`JwtAuthenticationFilter`가 이미 `UsernamePasswordAuthenticationToken`의 credentials 자리에 토큰
  원문을 넣고 있으므로 재파싱이 필요 없다).
- `AuthLogoutService.logout(String token)` → `tokenBlacklistService.blacklist(token)` 호출만 위임하고
  `204 No Content`를 반환한다.

## 모바일 구현

### `src/services/authApi.ts`

`postAuthLogout(accessToken: string): Promise<void>` 추가.
`POST /api/auth/logout`을 `Authorization: Bearer <accessToken>` 헤더로 호출하고, 실패 시 기존
`parseApiErrorMessage`로 에러 메시지를 던진다.

### `src/contexts/AuthContext.tsx`

- `logout()`을 `async`로 변경, 시그니처를 `logout: () => Promise<void>`로 변경.
- 백엔드 호출이 **성공한 경우에만** `SecureStore`(`TOKEN_KEY`, `USER_KEY`) 삭제 + `user`/`accessToken`
  state를 초기화한다.
- 실패 시 로컬 상태를 그대로 유지하고, `postAuthLogout`이 던진 예외를 그대로 호출자에게 전파한다
  (context에 별도 `logoutError` state는 두지 않는다 — 로그아웃을 트리거하는 화면이 `ProfileScreen`
  하나뿐이라 호출부에서 바로 `try/catch`하는 편이 더 단순하다).

### `src/screens/ProfileScreen.tsx`

- 닉네임 아래에 로그아웃 버튼을 추가한다.
- 버튼 탭 → `Alert.alert('로그아웃', '로그아웃하시겠어요?', [취소, 확인])` → 확인 시 `await logout()`.
- `isLoggingOut` 로컬 상태로 진행 중 버튼 비활성화(중복 탭 방지).
- `logout()`이 던진 예외를 `try/catch`로 잡아 `Alert.alert('오류', ...)`로 안내하고 화면에 남는다
  (로그인 상태 유지).

## 에러 처리 / 엣지 케이스

| 상황 | 처리 |
|------|------|
| 이미 만료된 토큰으로 로그아웃 요청 | 서명 검증 단계에서 `JwtException`으로 걸러져 `401`. 블랙리스트 로직에 도달하지 않음 (곧 자연 만료되므로 무해) |
| 같은 토큰으로 로그아웃 2번 호출 | 첫 호출로 블랙리스트 등록 → 두 번째 호출은 필터의 블랙리스트 체크에서 `401` |
| 모바일 로그아웃 API 호출 실패(네트워크/서버 오류) | 로컬 상태 유지, `try/catch`로 잡아 `Alert`로 표시, 사용자는 재시도 가능 |
| 로그아웃 중 다른 화면에서 401을 먼저 받는 경우 | 범위 밖. 기존 401 처리 동작을 그대로 따름 |

## MVP 제외 범위

- 사용자 전체 세션(다른 기기) 로그아웃 — 이번엔 현재 토큰만 무효화
- Redis 장애 시 fail-open/fail-closed 전략
- Refresh token (기존과 동일하게 MVP에서 제외)

## 테스트

- **백엔드**:
  - `JwtProviderTest`: 만료 시각 파싱 테스트 추가
  - `TokenBlacklistServiceTest`(신규): blacklist 후 `isBlacklisted` true, TTL 설정 확인
  - `AuthControllerTest`: 로그아웃 성공(204) 케이스 추가
  - `JwtAuthenticationFilter` 관련 테스트: 블랙리스트된 토큰으로 요청 시 401 케이스 추가
- **모바일**: jest 미설정 상태라 자동 테스트는 추가하지 않음. `npx tsc --noEmit` 통과 + 실기기/시뮬레이터에서
  로그인 → 로그아웃(확인 다이얼로그 포함) → 재로그인 흐름 수동 확인 (`mobile/CLAUDE.md` 검증 규칙).

## 검증 기준

- `POST /api/auth/logout` 호출 후 같은 토큰으로 `GET /api/me` 호출 시 `401 UNAUTHORIZED`
- 로그아웃하지 않은 다른 기기의 토큰은 계속 정상 동작
- 모바일: 로그아웃 성공 시 `SecureStore` 토큰/사용자 정보 삭제, 실패 시 유지
- `docs/api-contract.md`의 `POST /auth/logout` 예시와 실제 구현 동작 일치
