# 정지된 계정의 발급된 JWT 즉시 무효화 — 설계

## 배경

`feat/ugc-safety-compliance`([#72](https://github.com/lovelyAlien/runvas/pull/72)) 최종 리뷰에서 발견:
관리자가 `AdminReportActionService.resolveAndBan()`으로 사용자를 정지시키면 `User.bannedAt`이
설정되고, 이후 **새 로그인 시도**는 `KakaoAuthService`/`AppleAuthService`의 `requireNotBanned()`가
막는다. 하지만 이미 발급된 JWT는 `JwtAuthenticationFilter`가 토큰 블랙리스트(`TokenBlacklistService`,
로그아웃 시에만 채워짐)만 확인하고 매 요청마다 `User.bannedAt`을 조회하지 않으므로, 정지된 사용자가
`JWT_EXPIRATION_SECONDS`(기본 3600초) 동안은 계속 유효한 토큰으로 글쓰기/댓글 등을 할 수 있다.

Apple App Review Guideline 1.2는 신고 접수 24시간 내 "콘텐츠 삭제 + 사용자 퇴출(ejecting the user)"을
요구한다. 가이드라인 문구 자체는 기존 세션의 즉시 무효화를 명시하진 않지만, 재심사 시 리뷰어가 정지된
테스트 계정으로 바로 재시도할 가능성이 있고 구현 비용도 낮으므로 즉시 무효화까지 구현한다.

## 목표

정지(`resolveAndBan`) 시점 이후, 그 사용자가 보유한 모든 기존 JWT(발급 시점·기기 무관)가 다음 요청부터
즉시 거부되어야 한다.

## 접근

`JwtAuthenticationFilter`는 이미 매 요청마다 `TokenBlacklistService.isBlacklisted(token)`으로 Redis를
조회하는 패턴을 갖고 있다. 이를 사용자 단위로 확장한다.

- `TokenBlacklistService`에 `banUser(UUID userId)` / `isUserBanned(UUID userId)` 추가.
  `auth:banned-user:<userId>` 키를 Redis에 쓰고, TTL은 `JwtProvider`의 `expirationSeconds`(신규 getter
  `getExpirationSeconds()` 추가)에 1시간의 안전 마진을 더한 값으로 설정한다. 정지 시점 이후 그 시간이
  지나면 정지 이전에 발급된 토큰은 어차피 전부 자연 만료되므로 키도 함께 자연 소멸시킨다(로그아웃
  블랙리스트 키와 동일한 패턴). 안전 마진을 두는 이유: TTL을 정지 시점의 설정값 그대로 쓰면, 운영자가
  이후 `JWT_EXPIRATION_SECONDS`를 더 짧게 변경했을 때 그 변경 이전에 발급된(더 긴 수명의) 토큰이 마커보다
  먼저 만료되지 않을 수 있어 이 변경이 막으려는 문제가 그대로 재현된다.
- `AdminReportActionService.resolveAndBan()`에서 `user.ban(); userRepository.save(user);` 직후
  `tokenBlacklistService.banUser(user.getId())`를 호출한다(같은 `@Transactional` 메서드 안에서 처리하되,
  Redis 쓰기는 트랜잭션 롤백과 무관하게 즉시 반영됨을 인지하고 진행 — DB 트랜잭션이 롤백되는 경우는
  `resolve()`가 예외를 던지는 경우뿐이며 그 경우 정지 자체가 반영되지 않으므로 순서상 `user.ban()` 저장이
  성공한 뒤에만 도달한다).
- `JwtAuthenticationFilter.doFilterInternal()`에서 `tokenBlacklistService.isBlacklisted(token) ||
  tokenBlacklistService.isUserBanned(userId)`로 확인해 인증을 거부한다.

### 검토했으나 채택하지 않은 대안

1. **매 요청마다 `User.bannedAt`을 DB에서 조회.** 가장 직관적이지만 순수 토큰 파싱이던 인증 경로에 DB
   조회를 새로 추가한다. Redis 확장안으로 동일한 결과를 DB 부하 없이 얻을 수 있어 채택하지 않았다.
2. **정지 시점에 사용자의 활성 토큰들을 특정해 무효화.** 현재 세션/기기별 토큰을 추적하는 메커니즘이
   없어 별도의 세션 저장소를 새로 만들어야 한다. 유저ID 단위 마커로 기기 무관하게 동일한 효과를 낼 수
   있어 채택하지 않았다.
3. **`bannedAt` 조회에 짧은 TTL 캐시를 둠.** TTL 동안 정지 사실이 반영되지 않는 지연(staleness)이 남는다.
   Redis 마커는 정지 시점에 정확히 기록되므로 이 지연이 없다.

## 영향 범위

- `backend/src/main/java/com/runvas/auth/service/JwtProvider.java`: `getExpirationSeconds()` getter 추가.
- `backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java`: `banUser`/`isUserBanned` 추가.
- `backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java`: 정지 여부 확인 추가.
- `backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`: `resolveAndBan()`에서
  `TokenBlacklistService` 호출 추가(생성자 주입 필드 추가).
- `docs/admin-dashboard.md`: "정지된 계정은 이후 로그인 시 403" 문구에 "이미 발급된 토큰도 다음 요청부터
  즉시 무효화된다"를 추가.

Redis 장애 시 인증 경로 자체가 실패하므로(기존 `isBlacklisted` 확인도 동일하게 Redis에 의존한다) 이
변경이 가용성을 새로 낮추지는 않는다. 다만 `resolveAndBan()` 안에서 `tokenBlacklistService.banUser(...)`
호출이 Redis 예외로 실패하면 `@Transactional` 롤백으로 콘텐츠 삭제와 계정 정지도 함께 롤백된다 — 정지가
반쯤만 적용되어 토큰이 계속 유효한 상태로 남는 것보다 안전한 선택이므로 의도된 동작이다.

API 계약(`docs/api-contract.md`)이나 응답 필드는 변하지 않는다 — 정지된 사용자의 기존 요청이 401로
거부되는 동작(이미 문서화된 "유효하지 않은 토큰" 401 케이스)의 트리거 조건이 하나 늘어나는 것뿐이다.

## 테스트

- `TokenBlacklistServiceTest`: `banUser`가 `auth:banned-user:<id>` 키를 TTL과 함께 쓰는지, `isUserBanned`가
  키 존재 여부를 반영하는지(기존 블랙리스트 테스트와 동일한 패턴).
- `AdminReportActionServiceTest`: `resolveAndBan` 성공 시 `tokenBlacklistService.banUser(authorId)`가
  호출되는지 검증(mock 추가).
- `JwtAuthenticationFilter`에 대한 단위/통합 테스트가 아직 없으므로, 정지된 사용자 마커가 있을 때
  `SecurityContext`가 비워지는지 확인하는 단위 테스트를 새로 추가한다(기존 필터에 테스트가 없어 이번에
  최초로 만든다 — 블랙리스트 케이스도 함께 커버).
