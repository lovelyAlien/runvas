# 로그아웃

구현일: 2026-07-01

## 요약

`POST /api/auth/logout`으로 백엔드에 로그아웃을 요청하고, 성공한 경우에만 `SecureStore`의
`runvas_access_token`/`runvas_user`를 지운다. 실패 시(네트워크 오류 등) 로컬 상태를 그대로 유지해
사용자가 로그인 상태를 잃지 않도록 한다.

## 핵심 결정

- **백엔드 성공이 로그아웃의 전제 조건**: `AuthContext.logout()`은 `postAuthLogout` 실패 시 예외를
  그대로 던지고 `SecureStore` 삭제를 실행하지 않는다. 백엔드가 Redis 블랙리스트로 토큰을 즉시
  무효화하므로(`docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`), 로컬만
  지우고 서버 호출이 실패하면 토큰이 여전히 유효한 채로 기기에서만 로그아웃된 것처럼 보이는 상태를
  방지한다.
- **로그아웃 버튼 위치**: `ProfileScreen` — 사용자 정보를 보여주는 화면이라 자연스러운 위치.
- **확인 다이얼로그**: 기존 삭제 확인 패턴(`SavedRoutesScreen.tsx`)과 동일하게
  `Alert.alert(title, message, [취소, {style: 'destructive'}])` 형태를 사용해 실수 로그아웃을 방지.

## 관련 문서

- `docs/api-contract.md` §POST /auth/logout
- `docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`
