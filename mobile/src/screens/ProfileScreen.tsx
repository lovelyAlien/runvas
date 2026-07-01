import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';

// tabPress 가드가 명령형 진입(딥링크 등)까지는 막지 못하므로, 화면 진입 시에도 한 번 더
// requireAuth()를 호출하는 방어 가드를 둔다 (Critic 리뷰 반영).
export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const { requireAuth } = useAuthGate();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    requireAuth();
  }, [requireAuth]);

  const handleLogout = useCallback(() => {
    Alert.alert('로그아웃', '로그아웃 하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '로그아웃',
        style: 'destructive',
        onPress: async () => {
          setIsLoggingOut(true);
          try {
            await logout();
          } catch (e: unknown) {
            Alert.alert('오류', e instanceof Error ? e.message : '로그아웃에 실패했습니다.');
          } finally {
            setIsLoggingOut(false);
          }
        },
      },
    ]);
  }, [logout]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        {user ? (
          <>
            <Text style={styles.nickname}>{user.nickname}</Text>
            <TouchableOpacity
              style={[styles.logoutButton, isLoggingOut && styles.logoutButtonDisabled]}
              activeOpacity={0.8}
              disabled={isLoggingOut}
              onPress={handleLogout}
            >
              {isLoggingOut ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.logoutButtonText}>로그아웃</Text>
              )}
            </TouchableOpacity>
          </>
        ) : (
          <Text style={styles.emptyText}>로그인이 필요합니다.</Text>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nickname: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  logoutButton: {
    marginTop: 16,
    backgroundColor: Colors.danger,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 10,
  },
  logoutButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  logoutButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
});
