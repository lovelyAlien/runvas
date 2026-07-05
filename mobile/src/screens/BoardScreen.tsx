import React from 'react';
import { View, Text, TouchableOpacity, Alert, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';

// 게시판 목록/작성 폼은 Non-Goal — 백엔드 Post API(docs/api-contract.md)가 아직 없다.
// 이 화면은 비로그인도 읽을 수 있는 빈 상태와, 글쓰기 게이팅만 먼저 구현한다.
export default function BoardScreen() {
  const { requireAuth } = useAuthGate();

  const handlePressWrite = () => {
    if (!requireAuth()) return;
    Alert.alert('안내', '게시글 작성은 다음 업데이트에서 제공됩니다.');
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'left', 'right']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>게시판</Text>
      </View>

      <View style={styles.empty}>
        <Text style={styles.emptyText}>아직 게시글이 없습니다.</Text>
      </View>

      <TouchableOpacity style={styles.writeFab} onPress={handlePressWrite} activeOpacity={0.8}>
        <Ionicons name="create-outline" size={20} color={Colors.white} />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  header: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  writeFab: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
});
