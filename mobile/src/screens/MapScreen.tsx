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
import { CompositeScreenProps } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import Header from '../components/Header';
import KakaoMapView, { KakaoMapViewRef } from '../components/KakaoMapView';
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
  getCourses,
  searchPublicCourses,
  searchPublicCoursesByTag,
} from '../services/courseApi';
import CourseSearchBar from '../components/CourseSearchBar';
import { patchMe } from '../services/authApi';
import Toast from 'react-native-toast-message';
import { Colors } from '../constants/theme';
import { Coordinate, Course, CourseSummary, CourseVisibility } from '../types';
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
  const [isRouting, setIsRouting] = useState(false); // 경로 탐색 중 여부
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [routeTitle, setRouteTitle] = useState('');
  const [routeVisibility, setRouteVisibility] = useState<CourseVisibility>('PRIVATE');
  const [isPaceSelectorOpen, setIsPaceSelectorOpen] = useState(false);
  const [isSavingPace, setIsSavingPace] = useState(false);
  const [isCourseSheetOpen, setIsCourseSheetOpen] = useState(false);
  const [nearbyCourses, setNearbyCourses] = useState<CourseSummary[]>([]);
  const [isLoadingCourses, setIsLoadingCourses] = useState(false);
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [selectedCourseDetail, setSelectedCourseDetail] = useState<Course | null>(null);
  const [isCourseSheetCollapsed, setIsCourseSheetCollapsed] = useState(false);
  const searchButtonBottom = useRef(new Animated.Value(FLOATING_BUTTONS_DEFAULT_BOTTOM)).current;
  const sheetContentHeightRef = useRef(0);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isPedestrianRouteEnabled, setIsPedestrianRouteEnabled] = useState(true);

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

  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isRouting) return; // 이미 경로 탐색 중이면 무시
      if (isCourseSheetOpen) return; // 코스 탐색 중에는 지도 탭이 내 경로에 웨이포인트를 추가하지 않게 한다

      // 첫 번째 포인트: 마커만 추가
      if (waypoints.length === 0) {
        addFirstPoint(coord);
        mapRef.current?.addWaypoint(coord, 1);
        return;
      }

      // 두 번째 이후: 보행로 API는 비로그인 사용자도 호출 가능 (docs/api-contract.md Auth: None)
      const prevWaypoint = waypoints[waypoints.length - 1];

      if (!isPedestrianRouteEnabled) {
        const straightSegment = [prevWaypoint, coord];
        addSegment(coord, straightSegment);
        mapRef.current?.addWaypoint(coord, waypoints.length + 1);
        mapRef.current?.addRouteSegment(straightSegment);
        return;
      }

      setIsRouting(true);
      try {
        const segmentCoords = await fetchPedestrianRoute(prevWaypoint, coord, accessToken);
        addSegment(coord, segmentCoords);
        mapRef.current?.addWaypoint(coord, waypoints.length + 1);
        mapRef.current?.addRouteSegment(segmentCoords);
      } catch (error: unknown) {
        // 백엔드 호출 실패 시 직선으로 대체 (T-Map 자체 실패는 백엔드가 이미 직선으로 폴백함).
        // 실패 원인이 안 보이면 디버깅이 안 되므로 콘솔에는 남긴다.
        console.error('보행로 API 호출 실패:', error);
        const straightSegment = [prevWaypoint, coord];
        addSegment(coord, straightSegment);
        mapRef.current?.addWaypoint(coord, waypoints.length + 1);
        mapRef.current?.addRouteSegment(straightSegment);
      } finally {
        setIsRouting(false);
      }
    },
    [
      waypoints,
      isRouting,
      isCourseSheetOpen,
      isPedestrianRouteEnabled,
      accessToken,
      addFirstPoint,
      addSegment,
    ]
  );

  const handleLocate = async () => {
    const coord = await getCurrentLocation();
    if (!coord) {
      Alert.alert('위치 오류', '위치 권한을 허용해주세요.');
      return;
    }
    mapRef.current?.moveToLocation(coord);
  };

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
      const courses = await getCourses(bounds, accessToken ?? undefined);
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
    } catch (e: any) {
      Alert.alert('내보내기 실패', e.message ?? '알 수 없는 오류가 발생했습니다.');
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
    } catch (e: any) {
      Alert.alert('저장 실패', e.message ?? '알 수 없는 오류가 발생했습니다.');
      return;
    }

    setIsSaveModalOpen(false);
    navigation.navigate('SavedRoutes');
  };

  const canSave = routeCoords.length >= 2;

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
        <KakaoMapView ref={mapRef} onMapPress={handleMapPress} />

        {/* 경로 탐색 중 오버레이 */}
        {isRouting && (
          <View style={styles.routingOverlay}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        )}

        {/* 좌측 하단 코스 조회/이름 검색 버튼 — 시트가 열려 있는 동안 사라지지 않고, 시트가 펼쳐지고
            접히는 움직임을 그대로 따라다닌다. */}
        <Animated.View style={[styles.bottomLeftButtons, { bottom: searchButtonBottom }]}>
          <FAB icon="search" onPress={handleOpenCourseSearch} disabled={isLoadingCourses} />
          <FAB icon="search-outline" onPress={() => setIsSearchOpen(true)} />
        </Animated.View>

        {isSearchOpen && (
          <CourseSearchBar
            onClose={() => setIsSearchOpen(false)}
            onSelectCourse={handleSelectSearchResult}
            onSearch={handleSearchCourse}
            onSearchByTag={handleSearchCourseByTag}
          />
        )}

        {/* 우측 플로팅 버튼 — 코스 탐색 시트가 닫혀 있을 때만 보이는 '내 경로' 도구 모음.
            시트가 열려 있는 동안은 맥락에 안 맞으므로(펼침/접힘 모두) 완전히 숨긴다. */}
        {!isCourseSheetOpen && (
          <View style={[styles.floatingButtons, { bottom: FLOATING_BUTTONS_DEFAULT_BOTTOM }]}>
            <FAB icon="locate" onPress={handleLocate} />
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
          </View>
        )}

        {/* 코스 커뮤니티 액션 버튼 — 코스 탐색 시트가 열려 있는 동안(펼침/접힘 모두) 보이고,
            좌측 탐색 버튼과 같은 방식으로 시트 상단 위치를 따라간다. 현재 위치 버튼은 항상 함께
            보이고, 후기 작성/목록 버튼은 코스를 선택해야 눌린다. */}
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
            <View style={styles.visibilityToggle}>
              <TouchableOpacity
                style={[
                  styles.visibilityOption,
                  routeVisibility === 'PRIVATE' && styles.visibilityOptionSelected,
                ]}
                onPress={() => setRouteVisibility('PRIVATE')}
                activeOpacity={0.8}
              >
                <Text
                  style={[
                    styles.visibilityOptionLabel,
                    routeVisibility === 'PRIVATE' && styles.visibilityOptionLabelSelected,
                  ]}
                >
                  비공개
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.visibilityOption,
                  routeVisibility === 'PUBLIC' && styles.visibilityOptionSelected,
                ]}
                onPress={() => setRouteVisibility('PUBLIC')}
                activeOpacity={0.8}
              >
                <Text
                  style={[
                    styles.visibilityOptionLabel,
                    routeVisibility === 'PUBLIC' && styles.visibilityOptionLabelSelected,
                  ]}
                >
                  공개
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
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  mapContainer: {
    flex: 1,
    overflow: 'hidden', // 코스 시트가 접힘 애니메이션으로 내려갈 때 이 경계 밖으로 새어나가지 않게 자른다
  },
  routingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(255,255,255,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  floatingButtons: {
    position: 'absolute',
    right: 16,
    gap: 10,
  },
  bottomLeftButtons: {
    position: 'absolute',
    left: 16,
    gap: 10,
  },
  fab: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
  fabDisabled: {
    backgroundColor: Colors.gray50,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  modalCard: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 14,
    padding: 20,
  },
  modalTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 12,
  },
  modalInput: {
    borderWidth: 1,
    borderColor: Colors.gray100,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: Colors.gray900,
  },
  visibilityToggle: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 12,
  },
  visibilityOption: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: Colors.gray100,
    alignItems: 'center',
  },
  visibilityOptionSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  visibilityOptionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.gray500,
  },
  visibilityOptionLabelSelected: {
    color: Colors.white,
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 16,
  },
  modalCancelButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  modalCancelLabel: {
    color: Colors.gray500,
    fontWeight: '600',
  },
  modalSaveButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
  },
  modalSaveLabel: {
    color: Colors.white,
    fontWeight: '700',
  },
});
