# 진입 화면 인증 게이팅 + 하단 탭 뼈대

## 배경

`.omc/specs/deep-interview-entry-screen-design.md`(deep-interview, 모호도 15%)와
`.omc/plans/entry-screen-auth-gating.md`(omc-plan consensus, Critic APPROVED)에서 합의된
설계를 구현했다. 핵심 요구사항: 로그인 안 해도 지도 그리기(직선)·게시판 읽기는 가능하지만,
보행로 라우팅·저장·내보내기·게시판 글쓰기·저장한 코스/마이페이지 탭은 로그인이 필요하다.
하단 탭은 로그인 여부와 무관하게 항상 4개(지도/게시판/저장한 코스/마이페이지)가 보인다.

## 핵심 결정 (ADR 요약)

인증 게이트를 **`useAuth().requireAuth()` 단일 함수**로 통일해 탭 터치(`tabPress` 리스너)와
명령형 네비게이션 호출 양쪽에서 재사용했다. 모달 상태(`isLoginModalVisible`)는 `AuthContext`가
단독 소유하고, `LoginPromptModal`은 `App.tsx` 루트에서 한 번만 렌더링한다 — 화면마다 모달
인스턴스를 따로 들면 어떤 모달이 뜨는지 불명확해지는 문제(omc-plan Critic 리뷰에서 발견)를
피하기 위함이다.

로그인 성공 후 "신규 가입자는 게시판으로 1회 자동 이동" 부수효과는 `NewUserRedirectWatcher`라는
컴포넌트 한 곳에서만 처리한다 (`App.tsx`). 여러 곳에서 `consumeNewUserRedirect()`를 호출하면
플래그를 중복 소비하거나 놓칠 위험이 있어 단일 소비자로 고정했다.

## 변경/추가 파일

| 파일 | 내용 |
| --- | --- |
| `src/types/index.ts` | `User` 타입 추가 (`docs/data-model.md` 기준, `providerUserId` 제외) |
| `src/contexts/AuthContext.tsx` | (신규) `user`, 모달 상태, `mockLogin`/`requireAuth`/`consumeNewUserRedirect` |
| `src/hooks/useAuthGate.ts` | (신규) `requireAuth`를 재노출하는 얇은 래퍼 |
| `src/components/LoginPromptModal.tsx` | (신규) App 루트에서 1회만 렌더링하는 로그인 모달 |
| `src/navigation/types.ts` | `RootStackParamList` → `RootTabParamList` (Map/Board/SavedRoutes/Profile) |
| `src/screens/BoardScreen.tsx` | (신규) 게시글 목록 placeholder + 글쓰기 FAB(게이팅만 구현) |
| `src/screens/ProfileScreen.tsx` | (신규) 닉네임 표시 placeholder + 진입 시 `requireAuth()` 방어 가드 |
| `App.tsx` | `Stack.Navigator` → `Tab.Navigator`, `AuthProvider`, `navigationRef`, `NewUserRedirectWatcher` |
| `src/screens/MapScreen.tsx` | `handleMapPress` 인증 분기(직선 vs T-MAP), 저장/내보내기/북마크 콜백에 `requireAuth()` 게이트 |
| `src/screens/SavedRoutesScreen.tsx` | `useFocusEffect`로 진입 시 `requireAuth()` 방어 가드, 뒤로가기 헤더 제거(탭이라 의미 없음) |

## 백엔드 연동 전까지 임시인 부분

- `BoardScreen`은 글 목록 자체가 없어 좋아요 게이팅은 N/A. 글쓰기 FAB만 게이팅을 구현했다.
- `accessToken`은 아직 메모리(`AuthContext` state)에만 보관한다. 카카오 SDK 연동 시
  `expo-secure-store`로 옮겨야 한다 (앱 재시작 시 로그아웃되는 건 지금은 의도된 동작).

## 추가: 실제 백엔드 연동 (2026-06-27)

`runvas/backend`(Spring Boot + MySQL)가 준비되어, mock 전용이던 로그인/저장 흐름을 실제
HTTP 호출로 교체했다.

- **`src/services/authApi.ts`** (신규): `POST /auth/dev-login` 호출. 이건 `docs/api-contract.md`
  계약이 아니라 백엔드 `DevAuthController`가 제공하는 개발용 엔드포인트 — 카카오 앱 키/SDK
  연동 전까지 실제로 서명된 JWT를 받기 위한 다리 역할만 한다.
- **`AuthContext.mockLogin`**: 더 이상 클라이언트에서 더미 `User`를 만들지 않고
  `authApi.devLogin()`을 호출해 실제 `accessToken`/`User`/`isNewUser`를 받는다. 두 버튼의 의미가
  바뀌었다 — "테스트 로그인 (고정 계정)"은 고정 닉네임(`demo_user`)을 보내 두 번째 호출부터는
  실제로 `isNewUser=false`가 되고, "테스트 로그인 (매번 신규 가입)"은 닉네임을 비워 보내
  백엔드가 매번 새 사용자를 만들게 해 게시판 1회 이동을 반복 테스트할 수 있게 한다.
- **`src/services/courseApi.ts`**: `postCourse(body, accessToken)`로 시그니처 변경,
  `Authorization: Bearer` 헤더 추가.
- **`src/screens/MapScreen.tsx`의 `handleConfirmSave`**: 로컬 저장(`saveLocalRoute`)은 항상
  먼저 성공시키고, 그 다음에 `postCourse()`로 백엔드 업로드를 시도한다. 백엔드 호출이 실패해도
  로컬 저장 결과는 유지된다(오프라인 우선 원칙 유지) — 실패 시 Alert로만 알린다.
- **`.env`**: `EXPO_PUBLIC_API_BASE_URL`을 개발 머신 LAN IP(`http://<IP>:8080`)로 설정했다.
  iOS 시뮬레이터는 `localhost`도 되지만, 실기기(Expo Go)는 같은 와이파이의 LAN IP가 필요하다.
  IP가 바뀌면 `.env`를 다시 맞춰야 한다 (DHCP라 영구적이지 않음).

## 검증

- `npx tsc --noEmit` 통과 (mobile)
- `npx expo start` 번들 HTTP 200 확인
- **엔드투엔드 통합 테스트 완료**: 백엔드(MySQL)를 띄우고 `curl`로
  `POST /auth/dev-login` → 받은 JWT로 `POST /courses` 호출까지 성공 확인 (201 응답, 코스 객체
  정상 반환). 단, 모바일 UI에서 직접 탭하며 확인하는 실기기 테스트는 아직 안 했다 — 다음
  작업에서 필요.
