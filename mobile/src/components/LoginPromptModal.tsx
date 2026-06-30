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
            onPress={kakaoLogin}
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
