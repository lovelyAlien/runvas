# 백엔드 에이전트 작업 지침

백엔드 구현은 `../docs/`에 있는 공통 계약을 기준으로 합니다.

## 백엔드 구현 원칙

- API 요청과 응답은 `../docs/api-contract.md`를 기준으로 구현합니다.
- 저장 모델과 응답 모델의 노출 범위는 `../docs/data-model.md`를 따릅니다.
- 좌표, 거리, bounds, 경로 순서 검증은 `../docs/geo-conventions.md`와 `../docs/api-contract.md`의 제한값을 따릅니다.
- GPX 응답은 `../docs/gpx-export.md`를 기준으로 구현합니다.
- 작성자 권한, 공개 범위, 좋아요 중복 처리, 삭제 멱등성은 문서에 정의된 규칙을 우선합니다.
- API 내부 구조, DB 테이블명, ORM 엔티티 구조는 백엔드에서 정하되 외부 응답 계약은 문서와 맞춥니다.

## API와 에러

- 요청 또는 응답 필드를 추가하거나 이름을 바꾸려면 `../docs/api-contract.md`를 먼저 수정합니다.
- 에러 응답은 `../docs/api-contract.md`의 공통 에러 응답 형식을 사용합니다.
- 인증이 필요한 API에는 `Authorization: Bearer <accessToken>`을 요구합니다.
- 목록 API는 문서에 정의된 커서 기반 페이지네이션 규칙을 따릅니다.

## 현재 확정된 인증 방향

- 카카오 로그인은 `../docs/api-contract.md`를 기준으로 구현합니다.
- `POST /api/auth/kakao`에서 모바일 앱이 보낸 `authorizationCode`와 `redirectUri`를 받습니다.
- 인가 코드는 백엔드에서 카카오 서버와 교환합니다.
- 카카오 사용자 ID는 내부적으로 `providerUserId`로 저장하고, API 응답에는 절대 포함하지 않습니다.
- Runvas JWT를 `accessToken`으로 반환합니다.
- 카카오 액세스 토큰은 백엔드 내부 검증용으로만 사용하며, Runvas API 인증 토큰으로 취급하지 않습니다.

## 변경 후 검증 (매번)

1. `./gradlew test`로 관련 단위/통합 테스트(JUnit 5, MockMvc, Testcontainers)를 통과시킵니다.
2. 요청/응답 필드나 상태 코드가 바뀌었다면 `../docs/api-contract.md`의 예시와 실제 응답이
   일치하는지 확인합니다.
3. `./gradlew bootRun`으로 로컬 기동 후 변경한 엔드포인트를 직접 호출해 확인합니다 — 테스트만으로는
   잡히지 않는 설정/마이그레이션 오류가 있을 수 있습니다.
