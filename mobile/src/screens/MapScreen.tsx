import React, { useRef, useCallback, useState, useEffect } from 'react';
import {
  Animated,
  View,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  Modal,
  Text,
  TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CompositeScreenProps } from '@react-navigation/native';

import Header from '../components/Header';
import KakaoMapView, { KakaoMapViewRef, PublicCourseMarker } from '../components/KakaoMapView';
import CourseSearchSheet, {
  CourseSearchSheetRef,
  SHEET_HANDLE_HEIGHT,
} from '../components/CourseSearchSheet';
import RouteStatsBar from '../components/RouteStatsBar';
import PaceSelector from '../components/PaceSelector';
import { useRoute, DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { useLocation } from '../hooks/useLocation';
import { useAuth } from '../contexts/AuthContext';
import { fetchPedestrianRoute } from '../services/routingApi';
import { exportGpx } from '../utils/exportGpx';
import {
  postCourse,
  buildCreateCourseRequest,
  getCourse,
  getPublicCourses,
  searchPublicCourses,
  searchPublicCoursesByTag,
} from '../services/courseApi';
import CourseSearchBar from '../components/CourseSearchBar';
import { patchMe } from '../services/authApi';
import Toast from 'react-native-toast-message';
import { Colors } from '../constants/theme';
import { Coordinate, Course, CourseSummary, CourseVisibility, GeoBounds } from '../types';
import { formatPace } from '../utils/format';
import { RootTabParamList, RootStackParamList } from '../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Map'>,
  NativeStackScreenProps<RootStackParamList>
>;

const FLOATING_BUTTONS_DEFAULT_BOTTOM = 16;
const FLOATING_BUTTONS_SHEET_GAP = 12; // 시트 상단과 우측 FAB 스택 사이 여백

export default function MapScreen({ navigation }: Props) {
  const mapRef = useRef<KakaoMapViewRef>(null);
  const courseSheetRef = useRef<CourseSearchSheetRef>(null);
  const { accessToken, requireAuth, user, updateUser } = useAuth();
  const { getCurrentLocation } = useLocation();

  const selectedPace = user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM;

  const {
    waypoints,
    routeCoords,
    stats,
    addFirstPoint,
    addSegment,
    undoLast,
    clearRoute,
    toRoutePoints,
    toWaypointPoints,
    getBounds,
  } = useRoute(selectedPace);

  const [isExporting, setIsExporting] = useState(false);
  const [isRouting, setIsRouting] = useState(false);
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [routeTitle, setRouteTitle] = useState('');
  const [routeVisibility, setRouteVisibility] = useState<CourseVisibility>('PRIVATE');
  const [isPaceSelectorOpen, setIsPaceSelectorOpen] = useState(false);
  const [isSavingPace, setIsSavingPace] = useState(false);
  const [isBrowseMode, setIsBrowseMode] = useState(false);
  const [isFetchingCourses, setIsFetchingCourses] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isPedestrianRouteEnabled, setIsPedestrianRouteEnabled] = useState(true);
  const [isCourseSheetOpen, setIsCourseSheetOpen] = useState(false);
  const [nearbyCourses, setNearbyCourses] = useState<CourseSummary[]>([]);
  const [isLoadingCourses, setIsLoadingCourses] = useState(false);
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [selectedCourseDetail, setSelectedCourseDetail] = useState<Course | null>(null);
  const [isCourseSheetCollapsed, setIsCourseSheetCollapsed] = useState(false);
  const searchButtonBottom = useRef(new Animated.Value(FLOATING_BUTTONS_DEFAULT_BOTTOM)).current;
  const sheetContentHeightRef = useRef(0);

  const boundsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 탐색 버튼은 시트가 열려 있는 동안 사라지지 않고, 시트의 실시간 위치(드래그 중에도)를 그대로
  // 따라다닌다. translateY는 native driver로도 움직이므로, addListener로 JS 쪽 값을 동기화한다.
  useEffect(() => {
    if (!isCourseSheetOpen) {
      searchButtonBottom.setValue(FLOATING_BUTTONS_DEFAULT_BOTTOM);
      return;
    }
    const sheet = courseSheetRef.current;
    if (!sheet) return;
    const listenerId = sheet.translateY.addListener(({ value }) => {
      const target =
        SHEET_HANDLE_HEIGHT + sheetContentHeightRef.current + FLOATING_BUTTONS_SHEET_GAP - value;
      searchButtonBottom.setValue(target);
    });
    return () => sheet.translateY.removeListener(listenerId);
  }, [isCourseSheetOpen, searchButtonBottom]);

  const handleOpenCourseSearch = async () => {
    if (!mapRef.current) return;
    setIsLoadingCourses(true);
    setIsCourseSheetOpen(true);
    setSelectedCourseId(null);
    setSelectedCourseDetail(null);
    setIsCourseSheetCollapsed(false);
    sheetContentHeightRef.current = 0;
    mapRef.current.clearCoursePreview();
    courseSheetRef.current?.expand();
    try {
      const bounds = await mapRef.current.getBounds();
      const { courses } = await getPublicCourses(
        {
          swLat: bounds.southWest.latitude,
          swLng: bounds.southWest.longitude,
          neLat: bounds.northEast.latitude,
          neLng: bounds.northEast.longitude,
        },
        accessToken ?? undefined
      );
      setNearbyCourses(courses);
    } catch (e: unknown) {
      Alert.alert('조회 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      setIsCourseSheetOpen(false);
    } finally {
      setIsLoadingCourses(false);
    }
  };

  const handleCloseCourseSearch = () => {
    setIsCourseSheetOpen(false);
    setSelectedCourseId(null);
    setSelectedCourseDetail(null);
    setIsCourseSheetCollapsed(false);
    mapRef.current?.clearCoursePreview();
  };

  // 목록에서 코스를 선택: 현재 지도 범위를 유지한 채 경로만 미리보기로 그린다 (카메라 이동 없음).
  const handleSelectCourse = async (courseId: string) => {
    setSelectedCourseId(courseId);
    try {
      const course = await getCourse(courseId, accessToken ?? undefined);
      setSelectedCourseDetail(course);
      mapRef.current?.previewCourse(course.path);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      setSelectedCourseId(null);
      setSelectedCourseDetail(null);
    }
  };

  // "상세 보기" 버튼: 선택된 코스의 좌표로 카메라를 이동하고 경로 순서(웨이포인트 번호)를 표시한다.
  const handleViewCourseDetail = (courseId: string) => {
    if (!selectedCourseDetail || selectedCourseDetail.id !== courseId) return;
    mapRef.current?.fitBounds(selectedCourseDetail.bounds);
    mapRef.current?.showCourseWaypoints(selectedCourseDetail.waypoints);
  };

  const handlePressWritePost = () => {
    if (!requireAuth() || !selectedCourseDetail) return;
    navigation.navigate('PostCreate', {
      attachedCourseId: selectedCourseDetail.id,
      attachedCourseTitle: selectedCourseDetail.title,
    });
  };

  const handlePressCourseBoard = () => {
    if (!selectedCourseDetail) return;
    navigation.navigate('CourseBoard', {
      courseId: selectedCourseDetail.id,
      courseTitle: selectedCourseDetail.title,
    });
  };

  const fetchPublicCourses = useCallback(
    async (bounds: GeoBounds) => {
      setIsFetchingCourses(true);
      try {
        const { courses } = await getPublicCourses(
          {
            swLat: bounds.southWest.latitude,
            swLng: bounds.southWest.longitude,
            neLat: bounds.northEast.latitude,
            neLng: bounds.northEast.longitude,
            limit: 50,
          },
          accessToken ?? undefined
        );
        const markers: PublicCourseMarker[] = courses.map((c) => ({
          id: c.id,
          title: c.title,
          centerLat: (c.bounds.southWest.latitude + c.bounds.northEast.latitude) / 2,
          centerLng: (c.bounds.southWest.longitude + c.bounds.northEast.longitude) / 2,
        }));
        mapRef.current?.showPublicCourses(markers);
      } catch (error: unknown) {
        console.error('공개 코스 조회 실패:', error);
      } finally {
        setIsFetchingCourses(false);
      }
    },
    [accessToken]
  );

  const handleBoundsChange = useCallback(
    (bounds: GeoBounds) => {
      if (!isBrowseMode) return;
      if (boundsTimerRef.current) clearTimeout(boundsTimerRef.current);
      boundsTimerRef.current = setTimeout(() => fetchPublicCourses(bounds), 1000);
    },
    [isBrowseMode, fetchPublicCourses]
  );

  const handleCourseMarkerPress = useCallback(
    (courseId: string) => {
      navigation.navigate('CourseDetail', { courseId });
    },
    [navigation]
  );

  const toggleBrowseMode = useCallback(() => {
    setIsBrowseMode((prev) => !prev);
  }, []);

  const togglePedestrianRoute = useCallback(() => {
    setIsPedestrianRouteEnabled((prev) => {
      const next = !prev;
      Toast.show({
        type: 'info',
        text1: next ? '보행로 경로를 사용합니다' : '직선으로 연결합니다',
        visibilityTime: 2500,
        position: 'bottom',
      });
      return next;
    });
  }, []);

  const handleSearchCourse = useCallback(
    (q: string, signal: AbortSignal) => searchPublicCourses(q, accessToken ?? undefined, signal),
    [accessToken]
  );

  const handleSearchCourseByTag = useCallback(
    (tag: string, signal: AbortSignal) => searchPublicCoursesByTag(tag, accessToken ?? undefined, signal),
    [accessToken]
  );

  const handleSelectSearchResult = useCallback(
    (courseId: string) => {
      setIsSearchOpen(false);
      navigation.navigate('CourseDetail', { courseId });
    },
    [navigation]
  );

  // isBrowseMode 변화에 따른 지도 상태 동기화 + 언마운트 시 타이머 정리
  useEffect(() => {
    mapRef.current?.setBrowseMode(isBrowseMode);
    if (!isBrowseMode) {
      mapRef.current?.clearPublicCourses();
    }
    return () => {
      if (boundsTimerRef.current) clearTimeout(boundsTimerRef.current);
    };
  }, [isBrowseMode]);

  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isBrowseMode) return;
      if (isCourseSheetOpen) return; // 코스 탐색 중에는 지도 탭이 내 경로에 웨이포인트를 추가하지 않게 한다
      if (isRouting) return;

      if (waypoints.length === 0) {
        addFirstPoint(coord);
        mapRef.current?.addWaypoint(coord, 1);
        return;
      }

      const prevWaypoint = waypoints[waypoints.length - 1];

      setIsRouting(true);
      try {
        const segmentCoords = await fetchPedestrianRoute(prevWaypoint, coord, accessToken);
        addSegment(coord, segmentCoords);
        mapRef.current?.addWaypoint(coord, waypoints.length + 1);
        mapRef.current?.addRouteSegment(segmentCoords);
      } catch (error: unknown) {
        console.error('보행로 API 호출 실패:', error);
        const straightSegment = [prevWaypoint, coord];
        addSegment(coord, straightSegment);
        mapRef.current?.addWaypoint(coord, waypoints.length + 1);
        mapRef.current?.addRouteSegment(straightSegment);
      } finally {
        setIsRouting(false);
      }
    },
    [waypoints, isRouting, isBrowseMode, isCourseSheetOpen, isPedestrianRouteEnabled, accessToken, addFirstPoint, addSegment]
  );

  const handleLocate = async () => {
    const coord = await getCurrentLocation();
    if (!coord) {
      Alert.alert('위치 오류', '위치 권한을 허용해주세요.');
      return;
    }
    mapRef.current?.moveToLocation(coord);
  };

  const handleUndo = () => {
    if (waypoints.length === 0) return;
    undoLast();
    mapRef.current?.undoLast();
  };

  const handleClear = () => {
    if (waypoints.length === 0) return;
    Alert.alert('경로 초기화', '모든 경로를 삭제할까요?', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          clearRoute();
          mapRef.current?.clearMap();
        },
      },
    ]);
  };

  const handleExportGpx = async () => {
    if (!requireAuth()) return;
    if (routeCoords.length < 2) {
      Alert.alert('경로 없음', '최소 2개 이상의 포인트가 필요합니다.');
      return;
    }
    setIsExporting(true);
    try {
      await exportGpx(toRoutePoints());
    } catch (e: unknown) {
      Alert.alert('내보내기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsExporting(false);
    }
  };

  const handlePaceConfirm = async (paceSecPerKm: number) => {
    if (!accessToken || !user) { requireAuth(); return; }
    setIsSavingPace(true);
    try {
      const result = await patchMe({ runningPaceSecPerKm: paceSecPerKm }, accessToken);
      await updateUser(result.user);
      setIsPaceSelectorOpen(false);
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSavingPace(false);
    }
  };

  const handleOpenSaveModal = () => {
    if (!requireAuth()) return;
    const bounds = getBounds();
    if (routeCoords.length < 2 || !bounds) {
      Alert.alert('경로 없음', '최소 2개 이상의 포인트가 필요합니다.');
      return;
    }
    setRouteTitle('');
    setRouteVisibility('PRIVATE');
    setIsSaveModalOpen(true);
  };

  const handleConfirmSave = async () => {
    const bounds = getBounds();
    if (!bounds || !accessToken) return;
    const title = routeTitle.trim() || '제목 없는 코스';
    const path = toRoutePoints();
    const waypointPoints = toWaypointPoints();

    try {
      await postCourse(
        buildCreateCourseRequest({
          title,
          path,
          waypoints: waypointPoints,
          distanceMeters: stats.distanceMeters,
          estimatedDurationSeconds: stats.estimatedDurationSeconds,
          bounds,
          visibility: routeVisibility,
        }),
        accessToken
      );
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      return;
    }

    setIsSaveModalOpen(false);
    navigation.navigate('SavedRoutes');
  };

  const canSave = routeCoords.length >= 2;
  const isLoading = isRouting || isFetchingCourses;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'left', 'right']}>
      <Header
        pointCount={waypoints.length}
        isRouting={isRouting}
        onPressSavedRoutes={() => {
          if (requireAuth()) navigation.navigate('SavedRoutes');
        }}
      />

      <View style={styles.mapContainer}>
        <KakaoMapView
          ref={mapRef}
          onMapPress={handleMapPress}
          onBoundsChange={handleBoundsChange}
          onCourseMarkerPress={handleCourseMarkerPress}
        />

        {isLoading && (
          <View style={styles.routingOverlay}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        )}

        {isSearchOpen && (
          <CourseSearchBar
            onClose={() => setIsSearchOpen(false)}
            onSelectCourse={handleSelectSearchResult}
            onSearch={handleSearchCourse}
            onSearchByTag={handleSearchCourseByTag}
          />
        )}

        {/* 좌측 하단: "주변 코스 찾기"는 지도 bounds로 목록을 가져오는 주요 동작이라 텍스트
            라벨이 있는 pill 버튼으로, "검색"은 이름/태그 검색창을 여는 보조 동작이라 아이콘
            전용 FAB로 남겨 서로 다른 동작임을 형태로 구분한다. 시트가 열려 있는 동안도 사라지지
            않고 시트 상단 위치를 따라간다. */}
        <Animated.View style={[styles.bottomLeftButtons, { bottom: searchButtonBottom }]}>
          <TouchableOpacity
            style={[styles.nearbyCoursesButton, isLoadingCourses && styles.nearbyCoursesButtonDisabled]}
            onPress={handleOpenCourseSearch}
            disabled={isLoadingCourses}
            activeOpacity={0.8}
          >
            <Ionicons
              name="navigate"
              size={16}
              color={isLoadingCourses ? Colors.gray400 : Colors.white}
            />
            <Text
              style={[
                styles.nearbyCoursesButtonLabel,
                isLoadingCourses && styles.nearbyCoursesButtonLabelDisabled,
              ]}
            >
              주변 코스 찾기
            </Text>
          </TouchableOpacity>
          <FAB
            icon="search"
            onPress={() => setIsSearchOpen(true)}
            color={Colors.gray500}
          />
        </Animated.View>

        {/* 우측: 코스 탐색 시트가 닫혀 있을 때는 기존 '내 경로' 도구 모음, 열려 있을 때는
            위치 + 후기 작성/목록 버튼으로 전환. 시트가 열린 동안은 '내 경로' 도구가 맥락에
            안 맞으므로 숨긴다. */}
        {!isCourseSheetOpen && (
          <View style={styles.floatingButtons}>
            <FAB icon="locate" onPress={handleLocate} />
            {!isBrowseMode && (
              <>
                <FAB
                  icon={isPedestrianRouteEnabled ? 'walk' : 'walk-outline'}
                  onPress={togglePedestrianRoute}
                  color={isPedestrianRouteEnabled ? Colors.primary : Colors.gray400}
                />
                <FAB
                  icon="arrow-undo"
                  onPress={handleUndo}
                  disabled={waypoints.length === 0 || isRouting}
                />
                <FAB icon="save-outline" onPress={handleOpenSaveModal} disabled={!canSave} />
                <FAB
                  icon="trash-outline"
                  onPress={handleClear}
                  disabled={waypoints.length === 0 || isRouting}
                  color={Colors.danger}
                />
              </>
            )}
          </View>
        )}

        {isCourseSheetOpen && (
          <Animated.View style={[styles.floatingButtons, { bottom: searchButtonBottom }]}>
            <FAB icon="locate" onPress={handleLocate} />
            <FAB
              icon="create-outline"
              onPress={handlePressWritePost}
              disabled={!selectedCourseDetail}
            />
            <FAB
              icon="list-outline"
              onPress={handlePressCourseBoard}
              disabled={!selectedCourseDetail}
            />
          </Animated.View>
        )}

        <CourseSearchSheet
          ref={courseSheetRef}
          visible={isCourseSheetOpen}
          courses={nearbyCourses}
          isLoading={isLoadingCourses}
          selectedCourseId={selectedCourseId}
          onSelectCourse={handleSelectCourse}
          onViewDetail={handleViewCourseDetail}
          onClose={handleCloseCourseSearch}
          onCollapsedChange={setIsCourseSheetCollapsed}
          onContentHeightChange={(height) => {
            sheetContentHeightRef.current = height;
          }}
        />
      </View>

      <RouteStatsBar
        stats={stats}
        onExport={handleExportGpx}
        isExporting={isExporting}
        selectedPaceLabel={formatPace(selectedPace)}
        onPacePress={() => { if (!requireAuth()) return; setIsPaceSelectorOpen(true); }}
      />

      <PaceSelector
        visible={isPaceSelectorOpen}
        currentPace={selectedPace}
        onConfirm={handlePaceConfirm}
        onClose={() => setIsPaceSelectorOpen(false)}
        isSaving={isSavingPace}
      />

      <Modal visible={isSaveModalOpen} transparent animationType="fade">
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>코스 저장</Text>
            <TextInput
              style={styles.modalInput}
              placeholder="코스 제목을 입력하세요"
              placeholderTextColor={Colors.gray400}
              value={routeTitle}
              onChangeText={setRouteTitle}
              autoFocus
            />
            <Text style={styles.modalLabel}>공개 설정</Text>
            <View style={styles.visibilityRow}>
              <TouchableOpacity
                style={[styles.visibilityOption, routeVisibility === 'PUBLIC' && styles.visibilityActive]}
                onPress={() => setRouteVisibility('PUBLIC')}
                activeOpacity={0.7}
              >
                <Ionicons
                  name="globe-outline"
                  size={16}
                  color={routeVisibility === 'PUBLIC' ? Colors.primary : Colors.gray500}
                />
                <Text style={[styles.visibilityText, routeVisibility === 'PUBLIC' && styles.visibilityActiveText]}>
                  공개
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.visibilityOption, routeVisibility === 'PRIVATE' && styles.visibilityActive]}
                onPress={() => setRouteVisibility('PRIVATE')}
                activeOpacity={0.7}
              >
                <Ionicons
                  name="lock-closed-outline"
                  size={16}
                  color={routeVisibility === 'PRIVATE' ? Colors.primary : Colors.gray500}
                />
                <Text style={[styles.visibilityText, routeVisibility === 'PRIVATE' && styles.visibilityActiveText]}>
                  비공개
                </Text>
              </TouchableOpacity>
            </View>
            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalCancelButton}
                onPress={() => setIsSaveModalOpen(false)}
              >
                <Text style={styles.modalCancelLabel}>취소</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalSaveButton} onPress={handleConfirmSave}>
                <Text style={styles.modalSaveLabel}>저장</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

interface FABProps {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
  disabled?: boolean;
  color?: string;
}

function FAB({ icon, onPress, disabled, color = Colors.primary }: FABProps) {
  return (
    <TouchableOpacity
      style={[styles.fab, disabled && styles.fabDisabled]}
      onPress={onPress}
      disabled={disabled}
      activeOpacity={0.8}
    >
      <Ionicons name={icon} size={20} color={disabled ? Colors.gray300 : color} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.gray50 },
  mapContainer: { flex: 1, position: 'relative' },
  routingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(255,255,255,0.45)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  floatingButtons: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    gap: 10,
    alignItems: 'center',
  },
  bottomLeftButtons: {
    position: 'absolute',
    left: 16,
    gap: 10,
    alignItems: 'flex-start',
  },
  nearbyCoursesButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 44,
    paddingHorizontal: 16,
    borderRadius: 22,
    backgroundColor: Colors.primary,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.18,
    shadowRadius: 4,
    elevation: 4,
  },
  nearbyCoursesButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  nearbyCoursesButtonLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.white,
  },
  nearbyCoursesButtonLabelDisabled: {
    color: Colors.gray400,
  },
  fab: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: Colors.white,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.18,
    shadowRadius: 4,
    elevation: 4,
  },
  fabDisabled: { opacity: 0.4 },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalCard: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 24,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 16,
  },
  modalInput: {
    borderWidth: 1,
    borderColor: Colors.gray100,
    borderRadius: 8,
    padding: 12,
    fontSize: 15,
    color: Colors.gray900,
    marginBottom: 20,
  },
  modalLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.gray900,
    marginBottom: 6,
  },
  visibilityRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  visibilityOption: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: Colors.gray100,
    borderRadius: 8,
  },
  visibilityActive: {
    borderColor: Colors.primary,
  },
  visibilityText: {
    fontSize: 14,
    color: Colors.gray500,
  },
  visibilityActiveText: {
    color: Colors.primary,
    fontWeight: '600',
  },
  modalActions: { flexDirection: 'row', gap: 12 },
  modalCancelButton: {
    flex: 1,
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: Colors.gray100,
    alignItems: 'center',
  },
  modalCancelLabel: { fontSize: 15, color: Colors.gray500 },
  modalSaveButton: {
    flex: 1,
    padding: 12,
    borderRadius: 8,
    backgroundColor: Colors.primary,
    alignItems: 'center',
  },
  modalSaveLabel: { fontSize: 15, fontWeight: '700', color: Colors.white },
});
