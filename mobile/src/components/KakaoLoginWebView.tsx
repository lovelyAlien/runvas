import React, { useCallback, useRef } from 'react';
import {
  Modal,
  View,
  ActivityIndicator,
  TouchableOpacity,
  Text,
  StyleSheet,
  SafeAreaView,
} from 'react-native';
import { WebView } from 'react-native-webview';
import type { WebViewNavigation } from 'react-native-webview';
import { useAuth } from '../contexts/AuthContext';
import { KAKAO_REDIRECT_URI, KAKAO_REST_API_KEY } from '../config/auth';
import { Colors } from '../constants/theme';

// Expo Go에서 카카오 OAuth를 쓸 때 redirect_uri는 고정 URI를 사용해야 합니다.
// expo-auth-session이 생성하는 exp:// URI는 IP가 가변이라 카카오 콘솔에 등록할 수 없습니다.
// 카카오 개발자 콘솔 > 카카오 로그인 > Redirect URI 에 이 값을 그대로 등록하세요.

function buildKakaoAuthUrl(): string {
  const params = new URLSearchParams({
    client_id: KAKAO_REST_API_KEY,
    redirect_uri: KAKAO_REDIRECT_URI,
    response_type: 'code',
    scope: 'profile_nickname,account_email',
  });
  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`;
}

export default function KakaoLoginWebView() {
  const { isKakaoWebViewVisible, submitKakaoCode, cancelKakaoLogin } = useAuth();
  const handledRef = useRef(false);

  const handleRedirectUrl = useCallback(
    (url: string) => {
      if (handledRef.current) return;
      if (!url.startsWith(KAKAO_REDIRECT_URI)) return;

      handledRef.current = true;
      const searchParams = new URL(url).searchParams;
      const code = searchParams.get('code');
      const error = searchParams.get('error');

      if (code) {
        submitKakaoCode(code);
      } else {
        cancelKakaoLogin(error ? `카카오 인증 오류: ${error}` : '카카오 인증에 실패했습니다.');
      }
    },
    [submitKakaoCode, cancelKakaoLogin],
  );

  // iOS: onShouldStartLoadWithRequest가 redirect를 막고 코드를 추출
  // Android: onNavigationStateChange를 백업으로 사용 (handledRef로 중복 처리 방지)
  const onShouldStartLoadWithRequest = useCallback(
    (request: { url: string }): boolean => {
      if (request.url.startsWith(KAKAO_REDIRECT_URI)) {
        handleRedirectUrl(request.url);
        return false;
      }
      return true;
    },
    [handleRedirectUrl],
  );

  const onNavigationStateChange = useCallback(
    (navState: WebViewNavigation) => {
      handleRedirectUrl(navState.url);
    },
    [handleRedirectUrl],
  );

  return (
    <Modal
      visible={isKakaoWebViewVisible}
      animationType="slide"
      onShow={() => {
        handledRef.current = false;
      }}
    >
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => cancelKakaoLogin()} style={styles.closeButton}>
            <Text style={styles.closeText}>닫기</Text>
          </TouchableOpacity>
          <Text style={styles.title}>카카오 로그인</Text>
          <View style={{ width: 48 }} />
        </View>
        {KAKAO_REST_API_KEY ? (
          <WebView
            source={{ uri: buildKakaoAuthUrl() }}
            onShouldStartLoadWithRequest={onShouldStartLoadWithRequest}
            onNavigationStateChange={onNavigationStateChange}
            startInLoadingState
            renderLoading={() => (
              <View style={styles.loading}>
                <ActivityIndicator size="large" color={Colors.gray900} />
              </View>
            )}
          />
        ) : (
          <View style={styles.loading}>
            <Text style={styles.errorText}>EXPO_PUBLIC_KAKAO_APP_KEY가 설정되지 않았습니다.</Text>
          </View>
        )}
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: Colors.gray300,
  },
  closeButton: { padding: 4 },
  closeText: { fontSize: 15, color: Colors.gray500 },
  title: { fontSize: 15, fontWeight: '600', color: Colors.gray900 },
  loading: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  errorText: { fontSize: 14, color: Colors.danger, textAlign: 'center', paddingHorizontal: 24 },
});
