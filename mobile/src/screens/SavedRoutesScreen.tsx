import React, { useCallback, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Alert, TextInput, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { getMyCourses, deleteCourse } from '../services/courseApi';
import { getBookmarkedCourses } from '../services/bookmarkApi';
import { evictCourse } from '../services/courseCache';
import { formatDistance, formatDuration } from '../utils/format';
import { useAuthGate } from '../hooks/useAuthGate';
import { useAuth } from '../contexts/AuthContext';
import { DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { Colors } from '../constants/theme';
import { CourseSummary, BookmarkedCourseSummary } from '../types';
import { RootTabParamList, RootStackParamList } from '../navigation/types';
import CourseRouteSvg from '../components/CourseRouteSvg';
import CoursePreviewModal from '../components/CoursePreviewModal';

type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'SavedRoutes'>,
  NativeStackScreenProps<RootStackParamList>
>;

type Tab = 'mine' | 'bookmarked';

export default function SavedRoutesScreen({ navigation }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>('mine');
  const [routes, setRoutes] = useState<CourseSummary[]>([]);
  const [bookmarkedRoutes, setBookmarkedRoutes] = useState<BookmarkedCourseSummary[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [previewCourseId, setPreviewCourseId] = useState<string | null>(null);
  const handleClosePreview = useCallback(() => setPreviewCourseId(null), []);
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
      getBookmarkedCourses(accessToken).then((result) => setBookmarkedRoutes(result.courses));
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
          evictCourse(route.id);
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

      <View style={styles.tabBar}>
        <TouchableOpacity
          style={[styles.tabItem, activeTab === 'mine' && styles.tabItemActive]}
          onPress={() => setActiveTab('mine')}
          activeOpacity={0.7}
        >
          <Text style={[styles.tabLabel, activeTab === 'mine' && styles.tabLabelActive]}>내 코스</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabItem, activeTab === 'bookmarked' && styles.tabItemActive]}
          onPress={() => setActiveTab('bookmarked')}
          activeOpacity={0.7}
        >
          <Text style={[styles.tabLabel, activeTab === 'bookmarked' && styles.tabLabelActive]}>북마크</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.searchBar}>
        <Ionicons name="search-outline" size={16} color={Colors.gray400} />
        <TextInput
          style={styles.searchInput}
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholder="코스 이름 검색"
          placeholderTextColor={Colors.gray400}
          returnKeyType="search"
          clearButtonMode="while-editing"
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity onPress={() => setSearchQuery('')} activeOpacity={0.7}>
            <Ionicons name="close-circle" size={16} color={Colors.gray400} />
          </TouchableOpacity>
        )}
      </View>

      {activeTab === 'mine' ? (
        <FlatList
          data={routes.filter((r) =>
            searchQuery.trim()
              ? r.title.toLowerCase().includes(searchQuery.trim().toLowerCase())
              : true
          )}
          keyExtractor={(item) => item.id}
          initialNumToRender={4}
          maxToRenderPerBatch={2}
          windowSize={5}
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
              <CourseRouteSvg
                courseId={item.id}
                onPress={() => setPreviewCourseId(item.id)}
              />
              <TouchableOpacity
                onPress={() => navigation.navigate('CourseEdit', { courseId: item.id })}
                activeOpacity={0.7}
                style={styles.editButton}
              >
                <Ionicons name="pencil-outline" size={20} color={Colors.gray500} />
              </TouchableOpacity>
              <TouchableOpacity onPress={() => handleDelete(item)} activeOpacity={0.7} style={styles.deleteButton}>
                <Ionicons name="trash-outline" size={20} color={Colors.danger} />
              </TouchableOpacity>
            </TouchableOpacity>
          )}
        />
      ) : (
        <FlatList
          data={bookmarkedRoutes.filter((r) =>
            searchQuery.trim()
              ? r.title.toLowerCase().includes(searchQuery.trim().toLowerCase())
              : true
          )}
          keyExtractor={(item) => item.id}
          initialNumToRender={4}
          maxToRenderPerBatch={2}
          windowSize={5}
          contentContainerStyle={bookmarkedRoutes.length === 0 && styles.emptyContainer}
          ListEmptyComponent={
            <Text style={styles.emptyText}>북마크한 코스가 없습니다.</Text>
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
                  {new Date(item.bookmarkedAt).toLocaleDateString()} 저장
                </Text>
                {item.startAddress && (
                  <Text style={styles.rowAddress} numberOfLines={1}>
                    <Ionicons name="location-outline" size={11} color={Colors.gray400} />{' '}
                    {item.startAddress}
                  </Text>
                )}
              </View>
              <CourseRouteSvg
                courseId={item.id}
                onPress={() => setPreviewCourseId(item.id)}
              />
            </TouchableOpacity>
          )}
        />
      )}
      <CoursePreviewModal courseId={previewCourseId} onClose={handleClosePreview} />
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
    marginRight: 8,
  },
  editButton: {
    marginLeft: 12,
  },
  deleteButton: {
    marginLeft: 12,
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
  tabBar: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabItemActive: {
    borderBottomColor: Colors.primary,
  },
  tabLabel: {
    fontSize: 14,
    fontWeight: '500',
    color: Colors.gray500,
  },
  tabLabelActive: {
    color: Colors.primary,
    fontWeight: '700',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginHorizontal: 16,
    marginVertical: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: Colors.gray50,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: Colors.gray100,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
    color: Colors.gray900,
    paddingVertical: 0,
  },
});
