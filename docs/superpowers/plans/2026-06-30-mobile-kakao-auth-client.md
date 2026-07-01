# 모바일 카카오 로그인 클라이언트 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `mockLogin` / `devLogin` (임시 dev 엔드포인트)을 실제 카카오 OAuth 로그인으로 교체하고, 발급된 `accessToken`을 `expo-secure-store`에 영구 저장한다.

**Architecture:** `expo-auth-session`의 `useAuthRequest` hook으로 카카오 인가 코드를 획득하고, 기존 `POST /api/auth/kakao` 백엔드 엔드포인트에 전달한다. 반환된 JWT는 `expo-secure-store`에 저장해 앱 재시작 시 세션을 복원한다.

**Tech Stack:** Expo SDK 54, expo-auth-session, expo-secure-store, expo-crypto, TypeScript

## Global Constraints

- Expo SDK 버전 `~54.0.0` 고정 — 임의로 올리지 않는다
- 패키지 설치는 `npx expo install`만 사용한다 (`npm install` 금지)
- `EXPO_PUBLIC_KAKAO_APP_KEY`는 카카오 REST API 키 (네이티브 앱 키 아님)
- PKCE 미사용 (`usePKCE: false`) — 백엔드가 client secret으로 토큰을 교환하므로 MVP 수준에서 충분
- 카카오 OAuth 인가 서버: `https://kauth.kakao.com/oauth/authorize`
- redirect URI 개발 환경: `makeRedirectUri({ useProxy: true })` (Expo 프록시)
- SecureStore 키: `runvas_access_token`, `runvas_user`
- `providerUserId`는 API 응답에 없으므로 타입에 추가하지 않는다
- 검증 명령: `npx tsc --noEmit` (TypeScript 오류 없음)
- 번들 확인: `npx expo start` → `curl "http://localhost:8081/index.bundle?platform=ios&dev=true"` → HTTP 200

---

### Task 1: 패키지 설치 및 프로젝트 설정

**Files:**
- Modify: `mobile/package.json` (npx expo install이 자동 수정)
- Modify: `mobile/app.json`
- Modify: `mobile/.env.example`

**Interfaces:**
- Produces: `expo-auth-session`, `expo-secure-store`, `expo-crypto` 패키지 사용 가능 상태; `app.json`에 `scheme: "runvas"` 설정

- [ ] **Step 1: 패키지 설치**

`mobile/` 디렉토리에서 실행:

```bash
npx expo install expo-auth-session expo-secure-store expo-crypto
```

완료 후 `package.json`에 세 패키지가 추가되었는지 확인한다.

- [ ] **Step 2: `app.json`에 scheme 추가**

`mobile/app.json`의 `expo` 객체에 `scheme` 필드를 추가한다:

```json
{
  "expo": {
    "name": "Runvas",
    "slug": "runvas-mobile",
    "version": "1.0.0",
    "scheme": "runvas",
    "orientation": "portrait",
    "icon": "./assets/icon.png",
    "userInterfaceStyle": "light",
    "ios": {
      "supportsTablet": true,
      "infoPlist": {
        "NSLocationWhenInUseUsageDescription": "러닝 코스 생성을 위해 현재 위치가 필요합니다."
      }
    },
    "android": {
      "adaptiveIcon": {
        "backgroundColor": "#E6F4FE",
        "foregroundImage": "./assets/android-icon-foreground.png",
        "backgroundImage": "./assets/android-icon-background.png",
        "monochromeImage": "./assets/android-icon-monochrome.png"
      },
      "permissions": ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"]
    },
    "web": {
      "favicon": "./assets/favicon.png"
    },
    "plugins": [
      [
        "expo-location",
        {
          "locationWhenInUsePermission": "러닝 코스 생성을 위해 현재 위치가 필요합니다."
        }
      ]
    ]
  }
}
```

- [ ] **Step 3: `.env.example` 주석 업데이트**

`mobile/.env.example`을 아래 내용으로 교체한다:

```
EXPO_PUBLIC_KAKAO_APP_KEY=   # 카카오 개발자 콘솔 REST API 키 (네이티브 앱 키 아님)
EXPO_PUBLIC_API_BASE_URL=
```

- [ ] **Step 4: TypeScript 컴파일 확인**

```bash
npx tsc --noEmit
```

Expected: 오류 없음

- [ ] **Step 5: 커밋**

```bash
git add mobile/app.json mobile/.env.example mobile/package.json mobile/package-lock.json
git commit -m "chore(mobile): expo-auth-session, expo-secure-store 설치 및 scheme 설정"
```

---

### Task 2: `authApi.ts` 교체

**Files:**
- Modify: `mobile/src/services/authApi.ts`

**Interfaces:**
- Consumes: `AuthResponse` (`{ accessToken: string; user: User; isNewUser: boolean }`) — `src/types/index.ts`에 정의됨
- Produces: `postAuthKakao(authorizationCode: string, redirectUri: string): Promise<AuthResponse>` — Task 3에서 사용

- [ ] **Step 1: `authApi.ts` 전체 교체**

`mobile/src/services/authApi.ts`를 아래 내용으로 교체한다.
`devLogin` 함수를 완전히 제거하고 `postAuthKakao`를 추가한다:

```typescript
import { AuthResponse } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function postAuthKakao(
  authorizationCode: string,
  redirectUri: string,
): Promise<AuthResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/kakao`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider: 'KAKAO',
      authorizationCode,
      redirectUri,
    }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as AuthResponse;
}
```

- [ ] **Step 2: TypeScript 컴파일 확인**

```bash
npx tsc --noEmit
```

Expected: 오류 없음.
`devLogin`을 참조하던 곳에서 오류가 나면 다음 Task에서 수정되므로 `AuthContext.tsx` 관련 오류는 정상이다. 다른 파일의 오류는 즉시 수정한다.

- [ ] **Step 3: 커밋**

```bash
git add mobile/src/services/authApi.ts
git commit -m "feat(mobile): postAuthKakao 추가, devLogin 제거"
```

---

### Task 3: `AuthContext.tsx` 교체

**Files:**
- Modify: `mobile/src/contexts/AuthContext.tsx`

**Interfaces:**
- Consumes: `postAuthKakao(authorizationCode, redirectUri)` — Task 2에서 정의
- Produces:
  - `kakaoLogin(): Promise<void>` — LoginPromptModal에서 사용 (Task 4)
  - `isInitializing: boolean` — App.tsx 스플래시 처리에 사용 가능
  - `logout(): void` — ProfileScreen 등 기존 호출부와 시그니처 유지

- [ ] **Step 1: `AuthContext.tsx` 전체 교체**

`mobile/src/contexts/AuthContext.tsx`를 아래 내용으로 교체한다:

```typescript
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import * as SecureStore from 'expo-secure-store';
import {
  makeRedirectUri,
  ResponseType,
  useAuthRequest,
} from 'expo-auth-session';
import { User } from '../types';
import { postAuthKakao } from '../services/authApi';

const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_APP_KEY ?? '';
const TOKEN_KEY = 'runvas_access_token';
const USER_KEY = 'runvas_user';
const KAKAO_DISCOVERY = {
  authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize',
};
const redirectUri = makeRedirectUri({ useProxy: true });

interface AuthContextValue {
  user: User | null;
  accessToken: string | null;
  isInitializing: boolean;
  isLoginModalVisible: boolean;
  isLoggingIn: boolean;
  loginError: string | null;
  kakaoLogin: () => Promise<void>;
  logout: () => void;
  requireAuth: () => boolean;
  closeLoginModal: () => void;
  consumeNewUserRedirect: () => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const [isLoginModalVisible, setIsLoginModalVisible] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [pendingNewUserRedirect, setPendingNewUserRedirect] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [storedToken, storedUser] = await Promise.all([
          SecureStore.getItemAsync(TOKEN_KEY),
          SecureStore.getItemAsync(USER_KEY),
        ]);
        if (storedToken && storedUser) {
          setAccessToken(storedToken);
          setUser(JSON.parse(storedUser) as User);
        }
      } catch {
        // 복원 실패 시 비로그인 상태 유지
      } finally {
        setIsInitializing(false);
      }
    })();
  }, []);

  const [request, , promptAsync] = useAuthRequest(
    {
      clientId: KAKAO_REST_API_KEY,
      usePKCE: false,
      responseType: ResponseType.Code,
      scopes: ['profile_nickname', 'account_email'],
      redirectUri,
    },
    KAKAO_DISCOVERY,
  );

  const kakaoLogin = useCallback(async () => {
    if (!request) return;
    setIsLoggingIn(true);
    setLoginError(null);
    try {
      const response = await promptAsync();
      if (response.type === 'dismiss' || response.type === 'cancel') return;
      if (response.type === 'error') {
        throw new Error(response.error?.message ?? '카카오 인증에 실패했습니다.');
      }
      if (response.type !== 'success') return;

      const { code } = response.params;
      const result = await postAuthKakao(code, redirectUri);

      await Promise.all([
        SecureStore.setItemAsync(TOKEN_KEY, result.accessToken),
        SecureStore.setItemAsync(USER_KEY, JSON.stringify(result.user)),
      ]);

      setUser(result.user);
      setAccessToken(result.accessToken);
      setPendingNewUserRedirect(result.isNewUser);
      setIsLoginModalVisible(false);
    } catch (e: unknown) {
      setLoginError(e instanceof Error ? e.message : '로그인에 실패했습니다.');
    } finally {
      setIsLoggingIn(false);
    }
  }, [request, promptAsync]);

  const logout = useCallback(() => {
    SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {});
    SecureStore.deleteItemAsync(USER_KEY).catch(() => {});
    setUser(null);
    setAccessToken(null);
    setPendingNewUserRedirect(false);
  }, []);

  const requireAuth = useCallback((): boolean => {
    if (user) return true;
    setIsLoginModalVisible(true);
    return false;
  }, [user]);

  const closeLoginModal = useCallback(() => {
    setIsLoginModalVisible(false);
  }, []);

  const consumeNewUserRedirect = useCallback((): boolean => {
    if (!pendingNewUserRedirect) return false;
    setPendingNewUserRedirect(false);
    return true;
  }, [pendingNewUserRedirect]);

  const value = useMemo(
    () => ({
      user,
      accessToken,
      isInitializing,
      isLoginModalVisible,
      isLoggingIn,
      loginError,
      kakaoLogin,
      logout,
      requireAuth,
      closeLoginModal,
      consumeNewUserRedirect,
    }),
    [
      user,
      accessToken,
      isInitializing,
      isLoginModalVisible,
      isLoggingIn,
      loginError,
      kakaoLogin,
      logout,
      requireAuth,
      closeLoginModal,
      consumeNewUserRedirect,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth는 AuthProvider 내부에서만 사용할 수 있습니다.');
  }
  return context;
}
```

- [ ] **Step 2: TypeScript 컴파일 확인**

```bash
npx tsc --noEmit
```

Expected: 오류 없음.
`mockLogin`을 참조하던 `LoginPromptModal.tsx`에서 오류가 나면 Task 4에서 수정된다. 그 외 파일의 오류는 즉시 수정한다.

- [ ] **Step 3: 커밋**

```bash
git add mobile/src/contexts/AuthContext.tsx
git commit -m "feat(mobile): 카카오 OAuth 로그인 및 SecureStore 토큰 영속성 구현"
```

---

### Task 4: `LoginPromptModal.tsx` 교체 및 통합 검증

**Files:**
- Modify: `mobile/src/components/LoginPromptModal.tsx`

**Interfaces:**
- Consumes: `kakaoLogin(): Promise<void>` — Task 3에서 정의 (기존 `mockLogin` 교체)

- [ ] **Step 1: `LoginPromptModal.tsx` 전체 교체**

`mobile/src/components/LoginPromptModal.tsx`를 아래 내용으로 교체한다:

```typescript
import React from 'react';
import { Modal, View, Text, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';

// App.tsx 루트에서 단 한 번만 렌더링한다 — 화면마다 각자 모달을 띄우지 않는다.
export default function LoginPromptModal() {
  const { isLoginModalVisible, closeLoginModal, kakaoLogin, isLoggingIn, loginError } = useAuth();

  return (
    <Modal visible={isLoginModalVisible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>로그인이 필요해요</Text>
          <Text style={styles.subtitle}>
            저장, 내보내기, 게시판 글쓰기 등은 로그인 후 사용할 수 있습니다.
          </Text>

          {loginError && <Text style={styles.errorText}>{loginError}</Text>}

          <TouchableOpacity
            style={styles.kakaoButton}
            onPress={() => kakaoLogin()}
            activeOpacity={0.8}
            disabled={isLoggingIn}
          >
            {isLoggingIn ? (
              <ActivityIndicator size="small" color={Colors.gray900} />
            ) : (
              <Text style={styles.kakaoButtonLabel}>카카오 로그인</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity onPress={closeLoginModal} activeOpacity={0.7} disabled={isLoggingIn}>
            <Text style={styles.cancelLabel}>닫기</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 14,
    padding: 20,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 13,
    color: Colors.gray500,
    marginBottom: 16,
  },
  errorText: {
    fontSize: 12,
    color: Colors.danger,
    marginBottom: 12,
  },
  kakaoButton: {
    backgroundColor: '#FEE500',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 8,
  },
  kakaoButtonLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.gray900,
  },
  cancelLabel: {
    textAlign: 'center',
    color: Colors.gray500,
    marginTop: 8,
    fontWeight: '600',
  },
});
```

- [ ] **Step 2: TypeScript 컴파일 확인**

```bash
npx tsc --noEmit
```

Expected: 오류 없음

- [ ] **Step 3: 번들 확인**

백그라운드로 Expo 서버를 시작하고 번들 HTTP 200을 확인한다:

```bash
npx expo start --clear &
sleep 15
curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081/index.bundle?platform=ios&dev=true"
```

Expected: `200`

런타임 크래시는 `tsc`만으로 잡히지 않으므로 이 단계가 필수다.

- [ ] **Step 4: 수동 통합 검증 체크리스트**

실기기 또는 시뮬레이터에서 Expo Go로 확인:

```
□ 앱 실행 시 "카카오 로그인" 버튼이 모달에 표시된다
□ 버튼 탭 → 브라우저(Expo 프록시)가 열리고 카카오 로그인 페이지가 나타난다
□ 로그인 완료 → 앱으로 복귀, 로그인 상태가 된다
□ 앱을 완전히 종료 후 재시작 → 로그인 상태가 유지된다 (SecureStore 복원)
□ 로그아웃 후 앱 재시작 → 비로그인 상태로 시작한다
□ 브라우저에서 뒤로가기/닫기 → 모달이 그대로 유지된다 (오류 없이 dismiss)
```

- [ ] **Step 5: `mobile/docs/implementations/` 기록**

`mobile/docs/implementations/kakao-auth-client.md` 파일을 생성해 설계 과정과 사용한 패키지를 기록한다:

```markdown
# 카카오 로그인 클라이언트

구현일: 2026-06-30
설계 스펙: docs/superpowers/specs/2026-06-29-mobile-kakao-auth-design.md

## 사용 패키지
- expo-auth-session: useAuthRequest, makeRedirectUri (Expo 프록시 기반 OAuth)
- expo-secure-store: accessToken / user 영구 저장

## 핵심 결정
- PKCE 미사용 (usePKCE: false): 백엔드 client secret으로 토큰 교환, MVP 수준에서 충분
- redirectUri: makeRedirectUri({ useProxy: true }) — 팀원별 Expo 계정 기반 URI를 카카오 콘솔에 직접 등록

## 팀 개발 규칙
npx expo whoami 로 계정명 확인 후
https://auth.expo.io/@{계정명}/runvas-mobile 을 카카오 콘솔 Redirect URI에 등록한다.
```

- [ ] **Step 6: 커밋**

```bash
git add mobile/src/components/LoginPromptModal.tsx mobile/docs/implementations/kakao-auth-client.md
git commit -m "feat(mobile): 카카오 로그인 버튼 교체 및 통합 검증 완료"
```
