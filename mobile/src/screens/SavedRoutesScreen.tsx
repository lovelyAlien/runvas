import React, { useCallback, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Alert, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { getMyCourses, deleteCourse } from '../services/courseApi';
import { formatDistance, formatDuration } from '../utils/format';
import { useAuthGate } from '../hooks/useAuthGate';
import { useAuth } from '../contexts/AuthContext';
import { DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { Colors } from '../constants/theme';
import { CourseSummary } from '../types';
import { RootTabParamList, RootStackParamList } from '../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'SavedRoutes'>,
  NativeStackScreenProps<RootStackParamList>
>;

export default function SavedRoutesScreen({ navigation }: Props) {
  const [routes, setRoutes] = useState<CourseSummary[]>([]);
  const { requireAuth } = useAuthGate();
  const { accessToken, user } = useAuth();

  const userPace = user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM;

  function estimatedDuration(distanceMeters: number): number {
    return Math.round((distanceMeters / 1000) * userPace);
  }

  // tabPress 가드가 막지 못하는 명령형 진입(딥링크 등)에 대한 방어 가드.
  useFocusEffect(
    useCallback(() => {
      requireAuth();
    }, [requireAuth])
  );

  useFocusEffect(
    useCallback(() => {
      if (!accessToken) return;
      getMyCourses(accessToken).then(setRoutes);
    }, [accessToken])
  );

  const handleDelete = (route: CourseSummary) => {
    Alert.alert('코스 삭제', `"${route.title}"를 삭제할까요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          if (!accessToken) return;
          await deleteCourse(route.id, accessToken);
          setRoutes((prev) => prev.filter((r) => r.id !== route.id));
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>저장한 코스</Text>
      </View>

      <FlatList
        data={routes}
        keyExtractor={(item) => item.id}
        contentContainerStyle={routes.length === 0 && styles.emptyContainer}
        ListEmptyComponent={
          <Text style={styles.emptyText}>아직 저장한 코스가 없습니다.</Text>
        }
        renderItem={({ item }) => (
          <TouchableOpacity
            style={styles.row}
            activeOpacity={0.7}
            onPress={() => navigation.navigate('CourseDetail', { courseId: item.id })}
          >
            <View style={styles.rowInfo}>
              <Text style={styles.rowTitle}>{item.title}</Text>
              <Text style={styles.rowMeta}>
                {formatDistance(item.distanceMeters)} ·{' '}
                {formatDuration(estimatedDuration(item.distanceMeters))} ·{' '}
                {new Date(item.createdAt).toLocaleDateString()}
              </Text>
              {item.startAddress && (
                <Text style={styles.rowAddress} numberOfLines={1}>
                  <Ionicons name="location-outline" size={11} color={Colors.gray400} />{' '}
                  {item.startAddress}
                </Text>
              )}
            </View>
            <TouchableOpacity onPress={() => handleDelete(item)} activeOpacity={0.7}>
              <Ionicons name="trash-outline" size={20} color={Colors.danger} />
            </TouchableOpacity>
          </TouchableOpacity>
        )}
      />
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
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  headerSpacer: {
    width: 24,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  rowInfo: {
    flex: 1,
    marginRight: 12,
  },
  rowTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.gray900,
  },
  rowMeta: {
    fontSize: 12,
    color: Colors.gray500,
    marginTop: 4,
  },
  rowAddress: {
    fontSize: 11,
    color: Colors.gray400,
    marginTop: 2,
  },
});
