import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';

// tabPress 가드가 명령형 진입(딥링크 등)까지는 막지 못하므로, 화면 진입 시에도 한 번 더
// requireAuth()를 호출하는 방어 가드를 둔다 (Critic 리뷰 반영).
export default function ProfileScreen() {
  const { user } = useAuth();
  const { requireAuth } = useAuthGate();

  useEffect(() => {
    requireAuth();
  }, [requireAuth]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        {user ? (
          <Text style={styles.nickname}>{user.nickname}</Text>
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
});
