import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import * as SecureStore from 'expo-secure-store';
import { User } from '../types';
import { postAuthKakao } from '../services/authApi';
import { KAKAO_REDIRECT_URI } from '../components/KakaoLoginWebView';

const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_APP_KEY ?? '';
const TOKEN_KEY = 'runvas_access_token';
const USER_KEY = 'runvas_user';

interface AuthContextValue {
  user: User | null;
  accessToken: string | null;
  isInitializing: boolean;
  isLoginModalVisible: boolean;
  isLoggingIn: boolean;
  loginError: string | null;
  isKakaoWebViewVisible: boolean;
  kakaoLogin: () => void;
  logout: () => void;
  requireAuth: () => boolean;
  closeLoginModal: () => void;
  consumeNewUserRedirect: () => boolean;
  submitKakaoCode: (code: string) => Promise<void>;
  cancelKakaoLogin: (errorMsg?: string) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const [isLoginModalVisible, setIsLoginModalVisible] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isKakaoWebViewVisible, setIsKakaoWebViewVisible] = useState(false);
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

  const kakaoLogin = useCallback(() => {
    if (!KAKAO_REST_API_KEY) {
      setLoginError('EXPO_PUBLIC_KAKAO_APP_KEY가 설정되지 않았습니다.');
      return;
    }
    setLoginError(null);
    setIsLoggingIn(true);
    setIsKakaoWebViewVisible(true);
  }, []);

  const submitKakaoCode = useCallback(async (code: string) => {
    setIsKakaoWebViewVisible(false);
    try {
      const result = await postAuthKakao(code, KAKAO_REDIRECT_URI);
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
  }, []);

  const cancelKakaoLogin = useCallback((errorMsg?: string) => {
    setIsKakaoWebViewVisible(false);
    setIsLoggingIn(false);
    if (errorMsg) setLoginError(errorMsg);
  }, []);

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
      isKakaoWebViewVisible,
      kakaoLogin,
      logout,
      requireAuth,
      closeLoginModal,
      consumeNewUserRedirect,
      submitKakaoCode,
      cancelKakaoLogin,
    }),
    [
      user,
      accessToken,
      isInitializing,
      isLoginModalVisible,
      isLoggingIn,
      loginError,
      isKakaoWebViewVisible,
      kakaoLogin,
      logout,
      requireAuth,
      closeLoginModal,
      consumeNewUserRedirect,
      submitKakaoCode,
      cancelKakaoLogin,
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
