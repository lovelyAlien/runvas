# 카카오 로그인 클라이언트

구현일: 2026-06-30
설계 스펙: docs/superpowers/plans/2026-06-30-mobile-kakao-auth-client.md

## 사용 패키지
- expo-auth-session ~7.0.11: useAuthRequest, makeRedirectUri (카카오 OAuth 인가 코드 획득)
- expo-secure-store ~15.0.8: accessToken / user 영구 저장

## 핵심 결정
- PKCE 미사용 (usePKCE: false): 백엔드 client secret으로 토큰 교환, MVP 수준에서 충분
- makeRedirectUri(): expo-auth-session v7에서 useProxy 옵션 제거됨. 환경에 따라 자동으로 적절한 redirect URI 생성
- SecureStore 키: runvas_access_token, runvas_user

## 팀 개발 규칙
npx expo whoami 로 계정명 확인 후
발급된 redirect URI를 카카오 콘솔 Redirect URI에 등록한다.
