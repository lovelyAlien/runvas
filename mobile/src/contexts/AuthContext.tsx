import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { User } from '../types';
import { devLogin } from '../services/authApi';

// 백엔드 카카오 로그인(POST /auth/kakao)이 아직 없어 DevAuthController(/auth/dev-login)로
// 실제 JWT를 받아 흐름을 구현한다. 테스트 계정은 고정 닉네임 하나로 통일했다 — 매번 다른
// 사용자로 로그인되면 저장한 코스도 계정마다 흩어져 "저장 → 다시 보기" 테스트가 안 됐다.
// 카카오 SDK 연동 시 mockLogin 본문을 postAuthKakao() 호출로 교체하고, accessToken은
// expo-secure-store에 저장한다 (지금은 메모리에만 보관).
interface AuthContextValue {
  user: User | null;
  accessToken: string | null;
  isLoginModalVisible: boolean;
  isLoggingIn: boolean;
  loginError: string | null;
  mockLogin: () => Promise<void>;
  logout: () => void;
  requireAuth: () => boolean;
  closeLoginModal: () => void;
  consumeNewUserRedirect: () => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const FIXED_DEV_NICKNAME = 'demo_user';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoginModalVisible, setIsLoginModalVisible] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [pendingNewUserRedirect, setPendingNewUserRedirect] = useState(false);

  const mockLogin = useCallback(async () => {
    setIsLoggingIn(true);
    setLoginError(null);
    try {
      const response = await devLogin(FIXED_DEV_NICKNAME);
      setUser(response.user);
      setAccessToken(response.accessToken);
      setPendingNewUserRedirect(response.isNewUser);
      setIsLoginModalVisible(false);
    } catch (e: unknown) {
      setLoginError(e instanceof Error ? e.message : '로그인에 실패했습니다.');
    } finally {
      setIsLoggingIn(false);
    }
  }, []);

  const logout = useCallback(() => {
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
      isLoginModalVisible,
      isLoggingIn,
      loginError,
      mockLogin,
      logout,
      requireAuth,
      closeLoginModal,
      consumeNewUserRedirect,
    }),
    [
      user,
      accessToken,
      isLoginModalVisible,
      isLoggingIn,
      loginError,
      mockLogin,
      logout,
      requireAuth,
      closeLoginModal,
      consumeNewUserRedirect,
    ]
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
