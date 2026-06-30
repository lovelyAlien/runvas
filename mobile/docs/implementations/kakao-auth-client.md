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

## 환경별 Redirect URI

- **EAS 빌드 (개발/프로덕션)**: `runvas://` → 카카오 콘솔에 한 번만 등록
- **Expo Go**: redirect URI가 가변 IP를 포함하여 카카오 콘솔 등록이 불가능함. **로컬 개발 시 EAS 개발 빌드 사용을 권장한다.**

카카오 콘솔 등록 방법:
1. https://developers.kakao.com 접속
2. 애플리케이션 → 카카오 로그인 → Redirect URI
3. `runvas://` 등록

## 팀 개발 규칙
npx expo whoami 로 계정명 확인 후
발급된 redirect URI를 카카오 콘솔 Redirect URI에 등록한다.
