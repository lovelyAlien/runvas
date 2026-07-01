# 로그아웃

구현일: 2026-07-01

## 요약

`POST /api/auth/logout`으로 백엔드에 로그아웃을 요청하고, 성공하거나 서버가 `401 UNAUTHORIZED`를
반환하면 `SecureStore`의 `runvas_access_token`/`runvas_user`를 지운다. `401`은 이미 서버 기준으로
인증 불가 상태라는 뜻이므로 로컬 세션도 종료한다. 네트워크 오류나 서버 오류처럼 서버 무효화 여부를
알 수 없는 실패는 로컬 상태를 유지해 사용자가 재시도할 수 있게 한다.

앱 시작 시 저장된 JWT의 `exp`가 이미 지난 경우에도 저장된 사용자 정보를 로그인 상태로 복원하지 않고
로컬 세션을 정리한다.

## 핵심 결정

- **만료 토큰은 세션으로 복원하지 않음**: `AuthContext`는 `SecureStore`에서 토큰과 사용자 정보를
  읽은 뒤 JWT `exp`를 확인한다. 토큰이 없거나 만료됐으면 두 값을 삭제하고 비로그인 상태로 시작한다.
- **`401` 로그아웃은 로컬 로그아웃 성공 처리**: 만료 토큰, 이미 무효화된 토큰, 서명 불일치 토큰은
  서버에서 `401`로 거부된다. 이 경우 로컬 사용자 정보를 유지하면 로그아웃할 수 없는 유령 로그인
  상태가 되므로 로컬 세션을 정리한다.
- **네트워크/서버 오류는 재시도 가능하게 유지**: 백엔드가 Redis 블랙리스트로 토큰을 즉시 무효화하므로
  (`docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`), 서버 호출 결과를 알 수
  없는 오류에서는 로컬 상태를 유지한다.
- **로그아웃 버튼 위치**: `ProfileScreen` — 사용자 정보를 보여주는 화면이라 자연스러운 위치.
- **확인 다이얼로그**: 기존 삭제 확인 패턴(`SavedRoutesScreen.tsx`)과 동일하게
  `Alert.alert(title, message, [취소, {style: 'destructive'}])` 형태를 사용해 실수 로그아웃을 방지.

## 관련 문서

- `docs/api-contract.md` §POST /auth/logout
- `docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`
