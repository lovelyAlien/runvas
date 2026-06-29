# 보행자 경로 API 백엔드 경유 + 캐싱

## 배경

T-Map 보행자 경로 탐색 API는 무료 한도가 1,000회/일이고, 기존에는 모바일이 `EXPO_PUBLIC_TMAP_APP_KEY`로
직접 호출했다 (`src/utils/tmapRouting.ts`). 이 방식은 키가 클라이언트 번들에 노출되고, 캐싱이 없어
같은 경로를 반복 요청해도 매번 유료 API를 호출하는 문제가 있었다.

## 변경 내용

- `src/utils/tmapRouting.ts` 삭제, `src/services/routingApi.ts` 신설 — `POST /api/routes/pedestrian`을
  호출하는 방식으로 전환 (`courseApi.ts`의 fetch + `parseApiErrorMessage` 패턴 재사용).
- `MapScreen.tsx`의 `handleMapPress`: T-Map 직접 호출 대신 백엔드 호출, 백엔드 호출 자체가 실패하면
  (네트워크 등) 직선 폴백 — T-Map 자체 실패는 백엔드가 이미 직선으로 폴백해서 응답함.
- `.env`/`.env.example`에서 `EXPO_PUBLIC_TMAP_APP_KEY` 제거. 키는 백엔드 `application-local.yml`
  (gitignore 대상)의 `runvas.tmap.app-key`로 이동.
- 백엔드: `routing` 패키지(`TmapPedestrianClient`, `RoutingService`, `RoutingController`) 신설,
  전용 Redis 컨테이너(`runvas-redis`, 포트 6380)에 좌표 쌍 단위로 30일 캐싱.

## 검증

- `npx tsc --noEmit` 통과.
- Expo 번들(`/index.bundle?platform=ios&dev=true`) 200 확인.
- curl로 `POST /api/routes/pedestrian` 동일 좌표 2회 호출 → 2차 호출이 0.4s→0.02s대로 단축(캐시 히트),
  `docker exec runvas-redis redis-cli KEYS` 로 캐시 키 적재 확인.
- 백엔드만 재시작(Redis는 유지) 후 같은 좌표 재호출 → 여전히 빠른 응답(캐시가 재시작에도 살아있음,
  Redis AOF 영속화 덕분).
