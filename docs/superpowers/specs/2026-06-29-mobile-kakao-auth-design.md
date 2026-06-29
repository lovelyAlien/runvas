# 모바일 카카오 로그인 클라이언트 설계

작성일: 2026-06-29
관련 문서: `docs/api-contract.md` §Auth APIs, `mobile/AGENTS.md`

## 배경

현재 모바일 앱은 `AuthContext.mockLogin` → `devLogin` (백엔드 임시 엔드포인트)으로 인증을 처리한다.
`AuthContext.tsx`에 "카카오 SDK 연동 시 교체하라"는 주석이 남아 있으며, 이 설계는 그 교체 작업의 범위를 정의한다.

백엔드 `POST /api/auth/kakao`는 이미 구현 완료 상태다.

## 목표

- 카카오 OAuth로 실제 `authorizationCode`를 획득해 백엔드에 전달
- 백엔드가 발급한 `accessToken`을 `expo-secure-store`에 영구 저장
- 앱 재시작 시 저장된 토큰으로 세션 복원
- `mockLogin` / `devLogin` 코드 완전 제거

## 환경 조건

- Expo SDK 54, Expo Go 기반 개발
- 네이티브 Kakao SDK(`@react-native-kakao/core`) 사용 불가
- **PKCE 미사용** (`usePKCE: false`): 백엔드가 client secret으로 토큰을 교환하므로 MVP 수준에서 충분

## 추가 패키지

| 패키지 | 용도 |
|--------|------|
| `expo-auth-session` | OAuth 흐름 (AuthRequest, promptAsync, makeRedirectUri) |
| `expo-secure-store` | accessToken / user 영구 저장 |
| `expo-crypto` | expo-auth-session peer dependency |

설치: `npx expo install expo-auth-session expo-secure-store expo-crypto`

## 변경 파일

### `app.json`

`scheme` 추가 — 프로덕션 빌드의 딥링크 redirect URI 기반:

```json
{
  "expo": {
    "scheme": "runvas"
  }
}
```

### `.env.example`

`EXPO_PUBLIC_KAKAO_APP_KEY`는 카카오 개발자 콘솔의 **REST API 키**를 사용한다.
(네이티브 앱 키와 다름)

```
EXPO_PUBLIC_KAKAO_APP_KEY=   # 카카오 REST API 키
EXPO_PUBLIC_API_BASE_URL=
```

### `src/services/authApi.ts`

- `devLogin()` 함수 제거
- `postAuthKakao()` 추가:

```ts
export async function postAuthKakao(
  authorizationCode: string,
  redirectUri: string
): Promise<AuthResponse>
```

요청: `POST /api/auth/kakao`
```json
{
  "provider": "KAKAO",
  "authorizationCode": "<code>",
  "redirectUri": "<redirectUri>"
}
```

응답: `AuthResponse` (`accessToken`, `user`, `isNewUser`) — `docs/api-contract.md` §POST /auth/kakao 기준

### `src/contexts/AuthContext.tsx`

**인터페이스 변경:**

```ts
// 제거
mockLogin: () => Promise<void>

// 추가
kakaoLogin: () => Promise<void>
isInitializing: boolean
```

**동작:**

1. **앱 시작 (`useEffect`)**: SecureStore에서 `runvas_access_token`, `runvas_user` 로드 → 성공 시 상태 복원 → `isInitializing: false`
2. **`kakaoLogin()`**:
   - `AuthRequest` 생성:
     - `clientId`: `EXPO_PUBLIC_KAKAO_APP_KEY` (REST API 키)
     - `usePKCE: false`
     - `responseType: ResponseType.Code`
     - `scopes: ['profile_nickname', 'account_email']`
     - `redirectUri`: `makeRedirectUri({ useProxy: true })`
     - Kakao 인증 서버 엔드포인트를 직접 지정 (자동 디스커버리 없음):
       `{ authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize' }`
   - `promptAsync()` 호출 → 브라우저 열림
   - `response.type === 'success'` → `code` 추출
   - `postAuthKakao(code, redirectUri)` 호출
   - SecureStore에 `accessToken` + `user` 저장
   - 상태 갱신, 모달 닫기
3. **`logout()`**: SecureStore 항목 삭제, 상태 초기화

**SecureStore 키:**

| 키 | 값 |
|----|-----|
| `runvas_access_token` | JWT 문자열 |
| `runvas_user` | User 객체 (JSON) |

### `src/components/LoginPromptModal.tsx`

- `mockLogin` → `kakaoLogin` 참조 교체
- 버튼 레이블: "테스트 로그인" → "카카오 로그인"
- 버튼 UI: 기존 `#FEE500` 배경, `Colors.gray900` 텍스트 유지 — 로고 이미지 추가 없음 (YAGNI)

## 인증 흐름

```
앱 시작
  → isInitializing: true
  → SecureStore 로드 시도
  → 토큰 있으면 상태 복원 / 없으면 비로그인 상태
  → isInitializing: false

사용자가 "카카오 로그인" 탭
  → AuthRequest 생성
  → promptAsync() → 시스템 브라우저
  → 카카오 로그인 완료
  → Expo 프록시(개발) 또는 runvas:// 스킴(프로덕션) 통해 앱 복귀
  → authorizationCode 추출
  → POST /api/auth/kakao
  → accessToken + user 수신
  → SecureStore 저장
  → 상태 갱신 → 모달 닫힘

로그아웃
  → SecureStore 삭제
  → 상태 초기화
```

## redirectUri 전략

| 환경 | URI | 카카오 콘솔 등록 필요 |
|------|-----|----------------------|
| Expo Go (개발) | `https://auth.expo.io/@{owner}/{slug}` (`makeRedirectUri({ useProxy: true })` 자동 생성) | 필요 |
| 프로덕션 빌드 | `runvas://` (`makeRedirectUri({ scheme: 'runvas' })`) | 필요 |

두 URI 모두 카카오 개발자 콘솔 → 앱 → Redirect URI 목록에 등록해야 한다.

## 에러 처리

| 상황 | 처리 |
|------|------|
| 사용자가 브라우저 닫기 (`response.type === 'dismiss'`) | 조용히 무시, 모달 유지 |
| 카카오 인증 오류 (`response.type === 'error'`) | `loginError` 상태에 메시지 표시 |
| 백엔드 401 | "카카오 로그인에 실패했습니다" 표시 |
| 네트워크 오류 | "네트워크 오류가 발생했습니다" 표시 |

## MVP 제외 범위

- Refresh token (api-contract.md: "MVP에서는 refresh token을 응답하지 않음")
- accessToken 만료 시 자동 갱신 — 401 발생 시 사용자가 재로그인
- PKCE — 프로덕션 강화 시 `codeVerifier`를 백엔드에 함께 전달하는 방식으로 전환

## 검증 기준

- 카카오 로그인 성공 → `GET /api/me` 호출 성공 (200)
- 앱 재시작 후 SecureStore 토큰으로 세션 복원 확인
- 로그아웃 후 토큰 삭제 확인
- `npx tsc --noEmit` 통과
- `npx expo start` 후 번들 HTTP 200 확인
