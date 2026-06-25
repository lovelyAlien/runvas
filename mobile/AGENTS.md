# 모바일 에이전트 작업 지침

모바일 구현은 `../docs/`에 있는 공통 계약을 기준으로 합니다.

## 모바일 구현 원칙

- 화면과 사용자 흐름은 `../docs/product-scope.md`의 MVP 범위를 기준으로 구현합니다.
- API 요청과 응답 타입은 `../docs/api-contract.md`와 `../docs/data-model.md`를 기준으로 정의합니다.
- 지도 좌표, 거리, bounds, 경로 순서와 관련된 화면 또는 저장 요청은 `../docs/geo-conventions.md`를 따릅니다.
- GPX 다운로드 UX와 API 호출은 `../docs/gpx-export.md`를 기준으로 구현합니다.
- 서버에서 계산하거나 검증하는 값과 모바일에서 생성하는 값의 책임을 문서와 다르게 바꾸지 않습니다.
- API 필드를 바꿔야 한다면 모바일 코드를 바꾸기 전에 `../docs/`를 먼저 수정합니다.

## API 연동

- `../docs/api-contract.md`를 확인하지 않고 모바일만의 API 모델 가정을 추가하지 않습니다.
- 모바일 응답 타입은 `../docs/`의 `User`, `PublicProfile`, 에러 응답 정의와 맞춥니다.
- 인증이 필요한 API 요청에는 `Authorization: Bearer <accessToken>`을 붙입니다.
- 에러 처리는 `../docs/api-contract.md`의 공통 에러 응답 형식을 기준으로 구현합니다.

## 현재 확정된 인증 방향

- 카카오 SDK로 로그인을 시작하고 `authorizationCode`를 받습니다.
- `authorizationCode`와 `redirectUri`를 `POST /api/auth/kakao`로 백엔드에 보냅니다.
- 백엔드가 반환한 Runvas `accessToken`을 저장하고 Runvas API 호출에 사용합니다.
- 카카오 액세스 토큰은 Runvas API 인증 토큰으로 사용하지 않습니다.
