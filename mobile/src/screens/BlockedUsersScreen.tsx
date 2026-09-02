import React, { useCallback, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, Alert, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { fetchBlockedUsers, unblockUser } from '../services/blockApi';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { BlockedUser } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'BlockedUsers'>;

export default function BlockedUsersScreen({ navigation }: Props) {
  const { accessToken } = useAuth();
  const [blocks, setBlocks] = useState<BlockedUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const loadBlocks = useCallback(async () => {
    if (!accessToken) return;
    setIsLoading(true);
    try {
      const result = await fetchBlockedUsers(accessToken);
      setBlocks(result);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, [accessToken]);

  useFocusEffect(
    useCallback(() => {
      loadBlocks();
    }, [loadBlocks])
  );

  const handleUnblock = (userId: string, nickname: string) => {
    if (!accessToken) return;
    Alert.alert('차단 해제', `${nickname}님의 차단을 해제하시겠어요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '해제',
        onPress: async () => {
          try {
            await unblockUser(userId, accessToken);
            setBlocks((prev) => prev.filter((b) => b.blockedUser.id !== userId));
          } catch (e: unknown) {
            Alert.alert('해제 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          }
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>차단한 사용자</Text>
        <View style={styles.headerSpacer} />
      </View>

      {isLoading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={styles.loading} />
      ) : (
        <FlatList
          data={blocks}
          keyExtractor={(item) => item.blockedUser.id}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <Text style={styles.nickname}>{item.blockedUser.nickname}</Text>
              <TouchableOpacity
                onPress={() => handleUnblock(item.blockedUser.id, item.blockedUser.nickname)}
                activeOpacity={0.7}
              >
                <Text style={styles.unblockLabel}>차단 해제</Text>
              </TouchableOpacity>
            </View>
          )}
          ListEmptyComponent={<Text style={styles.emptyText}>차단한 사용자가 없습니다.</Text>}
        />
      )}
    </SafeAreaView>
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
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  headerSpacer: {
    width: 24,
  },
  loading: {
    marginTop: 40,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  nickname: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.gray900,
  },
  unblockLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.primary,
  },
  emptyText: {
    paddingHorizontal: 20,
    paddingVertical: 24,
    textAlign: 'center',
    fontSize: 13,
    color: Colors.gray400,
  },
});
