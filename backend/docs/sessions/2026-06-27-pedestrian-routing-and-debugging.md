# 2026-06-27 작업 기록 — 보행자 경로 API 백엔드 전환 + 502/캐시 디버깅

## 배경

T-Map 보행자 경로 탐색 API가 무료 한도 1,000회/일이라, 클라이언트가 직접 호출하던 방식을
백엔드 경유 + 캐싱으로 바꿔야 했다. 그 과정에서 502 에러, `UNKOWN_ERROR`, 캐시 미스 등
여러 문제를 같이 디버깅했다.

## 1. 코스 저장 502/UNKNOWN_ERROR 디버깅

- **원인 1**: Spring Security 필터 단계에서 막히는 401/403이 `GlobalExceptionHandler`를
  거치지 않고 Spring 기본 에러 바디(`{"timestamp":...}`)를 내려줘서 모바일이 `UNKNOWN_ERROR`로
  잘못 처리. → `RestAuthEntryPoints`(`AuthenticationEntryPoint`/`AccessDeniedHandler`)를 추가해
  `docs/api-contract.md` 형식(`{"error":{"code","message"}}`)으로 통일.
- **원인 2**: 로컬 Mac의 IPv4 8080 포트를 다른 프로젝트의 brew nginx가 선점하고 있어서, 백엔드는
  IPv6로만 떠 있었고 LAN(IPv4)으로 들어오는 휴대폰 요청은 전부 502. → 백엔드 포트를 **8921**로
  변경(다른 프로젝트의 nginx는 건드리지 않음).
- **원인 3**: `courses.path` 컬럼이 `tinytext`(255자 제한)로 생성돼 있어서 긴 경로 저장 시
  `Data truncation` 에러. → `Course.path`에 `columnDefinition = "LONGTEXT"` 추가 + 기존 컬럼
  `ALTER TABLE ... MODIFY path LONGTEXT`로 직접 수정.
- SQL 로깅(`show_sql`, `org.hibernate.SQL: debug`)을 `application-dev.yml`에 추가해 이후
  디버깅을 쉽게 했다.

## 2. 보행자 경로 API 백엔드 전환

- 모바일이 `EXPO_PUBLIC_TMAP_APP_KEY`로 T-Map을 직접 호출하던 `tmapRouting.ts`를 제거하고,
  백엔드 `routing` 패키지(`TmapPedestrianClient`, `RoutingService`, `RoutingController`)를
  신설해 `POST /api/routes/pedestrian`으로 옮겼다. 모바일은 `routingApi.ts`로 호출.
- 전용 Redis 컨테이너(`runvas-redis`, 포트 6380, `--appendonly yes`로 AOF 켬 — 다른 프로젝트의
  MySQL/Redis와 분리하는 기존 원칙을 그대로 따름)에 출발/도착 좌표 쌍 단위로 30일 캐싱.
- T-Map app-key는 코드/git에 평문으로 두지 않고 `application-local.yml`(gitignore 대상) 또는
  `RUNVAS_TMAP_APP_KEY` 환경변수로만 주입.

## 3. 발견하고 같이 고친 버그

- **`GlobalExceptionHandler`가 예외를 로깅 없이 삼킴**: `@ExceptionHandler(Exception.class)`가
  로그 없이 바로 500 응답만 내려서 원인 파악이 불가능했다. `log.error("Unexpected server error", ex)`
  추가.
- **기본 `RedisCacheManager`가 record를 캐싱 못 함**: 기본 직렬화가 JDK 직렬화인데, `RoutePoint`가
  record(Serializable 미구현)라 캐시 쓰기 시 `SerializationException`. `CacheConfig`에서
  `GenericJackson2JsonRedisSerializer`로 교체해 해결.
- **캐시 키 정밀도가 너무 좁음**: 처음에 좌표를 소수 5자리(약 1.1m 격자)로 반올림했는데, 실제
  탭 좌표가 사람 눈에는 "같은 지점"이어도 2~3m씩 차이가 나서 캐시 미스가 잦았다. 4자리(약 11m
  격자)로 넓혀 해결 — 실측 로그(`캐시 미스`/`캐시 히트`)로 확인.
- **`@Cacheable`은 히트 시 메서드 진입을 건너뛰어 로그를 못 찍음**: `RoutingService`를
  어노테이션 대신 `CacheManager`를 직접 쓰는 방식으로 바꿔 히트/미스를 명시적으로 `log.info`.

## 4. 디버깅 중 알아낸 환경 사실

- 백엔드가 IntelliJ로 떠 있으면 콘솔 로그를 외부에서 못 본다 — 디버깅이 필요할 땐
  `./gradlew bootRun > /tmp/runvas-backend.log 2>&1 &`로 직접 띄우는 게 로그 확인에 유리하다.
  (이 세션 당시엔 `--spring.profiles.active=dev,local`이 필요했지만, 아래 "이후 정리" 항목으로
  `application-dev.yml`/`application-local.yml` 분리를 없애서 더 이상 프로필 지정이 필요 없다.)
- `:8081`(Expo 번들러)과 `:8921`(백엔드)은 의도적으로 분리된 별개 서비스 — 혼동 포인트였지만
  버그는 아니었다.
- "BE 호출이 안 되는 것 같다"는 보고의 실제 원인은 비로그인 상태였다 — `MapScreen.tsx`의
  게이트(`!user || !accessToken`)가 의도대로 동작하고 있었을 뿐.

## 최종 검증

- `curl`로 동일 좌표 2회 호출 → 캐시 히트 로그 확인, Redis 키 적재 확인.
- 백엔드만 재시작(Redis 유지) 후에도 캐시 유지 확인(AOF 영속화).
- 실사용 좌표차(~2.5m)로 재현 테스트 → 4자리 격자로 캐시 히트 확인.
- 모바일 `npx tsc --noEmit` 통과, Expo 번들 200 확인.

## 5. 이후 정리 — yml 통합

`application.yml`(공통)/`application-dev.yml`("dev" 프로필)/`application-local.yml`("local"
프로필, 시크릿)로 나뉘어 있던 게, 프로필을 두 개 다 켜야 시크릿까지 로드되는 구조였다. IntelliJ
실행 설정이 "dev"만 켜고 "local"을 빼먹어서 T-Map 키가 비는 사고가 실제로 났다(위 디버깅 참고).

이 프로젝트엔 아직 "운영(prod)" 환경이 없어 dev/local을 프로필로 나눌 필요가 없다고 판단해
`application-dev.yml`의 내용을 `application.yml`에 합치고, `application.yml`에
`spring.config.import: optional:classpath:application-local.yml`을 추가해 시크릿 파일이
프로필 지정 없이 항상 로드되게 했다. `application-dev.yml`은 삭제했다. 이제 백엔드는
`./gradlew bootRun`만으로 항상 같은 설정으로 뜬다.
