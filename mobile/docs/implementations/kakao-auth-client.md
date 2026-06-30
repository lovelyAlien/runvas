# 카카오 로그인 클라이언트

구현일: 2026-06-30

## 현재 구현 (Expo Go — WebView 방식)

### 사용 패키지
- react-native-webview 13.15.0: 카카오 OAuth 인가 코드 획득
- expo-secure-store ~15.0.8: accessToken / user 영구 저장

### 핵심 결정
- **WebView 방식 채택**: `expo-auth-session`이 Expo Go에서 생성하는 `exp://` redirect URI는 가변 IP를 포함하므로 카카오 콘솔에 등록이 불가능합니다. WebView로 카카오 OAuth 페이지를 직접 열고 redirect를 가로채는 방식으로 우회합니다.
- **고정 redirect URI**: `http://localhost/oauth/kakao/callback` — 카카오 콘솔에 한 번만 등록하면 어떤 기기·IP에서도 동일하게 동작합니다.
- **redirect 가로채기**: `onShouldStartLoadWithRequest`(iOS 주) + `onNavigationStateChange`(Android 백업)으로 redirect URI를 감지하고 `code`를 추출합니다.
- **PKCE 미사용**: 백엔드가 client secret으로 카카오 토큰을 교환하므로 MVP 수준에서 충분합니다.
- **SecureStore 키**: `runvas_access_token`, `runvas_user`

### 카카오 콘솔 등록 방법
1. https://developers.kakao.com 접속
2. 애플리케이션 선택 → 카카오 로그인 → Redirect URI
3. `http://localhost/oauth/kakao/callback` 등록

### 로그인 흐름
```
kakaoLogin() 호출
  → isKakaoWebViewVisible = true
  → KakaoLoginWebView Modal 표시
  → 사용자가 카카오 로그인 완료
  → Kakao가 http://localhost/oauth/kakao/callback?code=XXX 로 redirect
  → WebView가 redirect를 가로채 code 추출
  → submitKakaoCode(code) 호출
  → POST /api/auth/kakao { provider, authorizationCode, redirectUri }
  → 백엔드가 Runvas JWT + 사용자 정보 반환
  → SecureStore에 저장 → 로그인 완료
```

---

## 향후 계획 (네이티브 SDK — EAS 개발 빌드)

앱스토어/플레이스토어 출시 또는 카카오톡 앱 연동 '간편 로그인'이 필요해지면:

- EAS Build로 커스텀 개발 빌드(Development Build) 생성
- `@react-native-seoul/kakao-login` 라이브러리로 네이티브 SDK 연동
- redirect URI → `runvas://` scheme으로 변경
- 카카오 콘솔에 `runvas://` 등록
