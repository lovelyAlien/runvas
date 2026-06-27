import React from 'react';
import { Modal, View, Text, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';

// App.tsx 루트에서 단 한 번만 렌더링한다 — 화면마다 각자 모달을 띄우지 않는다.
export default function LoginPromptModal() {
  const { isLoginModalVisible, closeLoginModal, mockLogin, isLoggingIn, loginError } = useAuth();

  return (
    <Modal visible={isLoginModalVisible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>로그인이 필요해요</Text>
          <Text style={styles.subtitle}>
            저장, 내보내기, 게시판 글쓰기 등은 로그인 후 사용할 수 있습니다.
          </Text>

          {loginError && <Text style={styles.errorText}>{loginError}</Text>}

          {/* 백엔드 카카오 로그인이 아직 없어 DevAuthController(/auth/dev-login)로 실제 JWT를
              발급받는다. 고정 닉네임 버튼은 두 번째 호출부터 isNewUser=false가 되고, 매번 신규
              버튼은 항상 새 사용자를 만들어 1회성 게시판 이동을 반복 테스트할 수 있다.
              실제 SDK 연동 시 "카카오 로그인" 버튼 하나로 교체한다. */}
          <TouchableOpacity
            style={styles.kakaoButton}
            onPress={() => mockLogin(false)}
            activeOpacity={0.8}
            disabled={isLoggingIn}
          >
            {isLoggingIn ? (
              <ActivityIndicator size="small" color={Colors.gray900} />
            ) : (
              <Text style={styles.kakaoButtonLabel}>테스트 로그인 (고정 계정)</Text>
            )}
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.kakaoButton, styles.kakaoButtonSecondary]}
            onPress={() => mockLogin(true)}
            activeOpacity={0.8}
            disabled={isLoggingIn}
          >
            {isLoggingIn ? (
              <ActivityIndicator size="small" color={Colors.gray900} />
            ) : (
              <Text style={styles.kakaoButtonLabel}>테스트 로그인 (매번 신규 가입)</Text>
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
  kakaoButtonSecondary: {
    backgroundColor: Colors.gray100,
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
