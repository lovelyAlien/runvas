import React, { useRef, useCallback, useState } from 'react';
import {
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

import Header from '../components/Header';
import KakaoMapView, { KakaoMapViewRef } from '../components/KakaoMapView';
import CourseSearchSheet from '../components/CourseSearchSheet';
import RouteStatsBar from '../components/RouteStatsBar';
import PaceSelector from '../components/PaceSelector';
import { useRoute, DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { useLocation } from '../hooks/useLocation';
import { useAuth } from '../contexts/AuthContext';
import { fetchPedestrianRoute } from '../services/routingApi';
import { exportGpx } from '../utils/exportGpx';
import { postCourse, buildCreateCourseRequest, getCourse, getCourses } from '../services/courseApi';
import { patchMe } from '../services/authApi';
import { Colors } from '../constants/theme';
import { Coordinate, CourseSummary, CourseVisibility } from '../types';
import { formatPace } from '../utils/format';
import { RootTabParamList } from '../navigation/types';

type Props = BottomTabScreenProps<RootTabParamList, 'Map'>;

export default function MapScreen({ navigation }: Props) {
  const mapRef = useRef<KakaoMapViewRef>(null);
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

  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isRouting) return; // 이미 경로 탐색 중이면 무시

      // 첫 번째 포인트: 마커만 추가
      if (waypoints.length === 0) {
        addFirstPoint(coord);
        mapRef.current?.addWaypoint(coord, 1);
        return;
      }

      // 두 번째 이후: 보행로 API는 비로그인 사용자도 호출 가능 (docs/api-contract.md Auth: None)
      const prevWaypoint = waypoints[waypoints.length - 1];

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
    [waypoints, isRouting, accessToken, addFirstPoint, addSegment]
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

  const handleSelectCourse = async (courseId: string) => {
    setIsCourseSheetOpen(false);
    try {
      const course = await getCourse(courseId, accessToken ?? undefined);
      mapRef.current?.showCourse(course.path, course.bounds);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    }
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
    <SafeAreaView style={styles.container}>
      <Header
        pointCount={waypoints.length}
        isRouting={isRouting}
        onPressSavedRoutes={() => {
          if (requireAuth()) navigation.navigate('SavedRoutes');
        }}
      />

      <View style={styles.mapContainer}>
        <KakaoMapView ref={mapRef} onMapPress={handleMapPress} />

        {/* 우측 상단 코스 조회 버튼 */}
        <View style={styles.topRightButtons}>
          <FAB icon="search" onPress={handleOpenCourseSearch} disabled={isLoadingCourses} />
        </View>

        {/* 경로 탐색 중 오버레이 */}
        {isRouting && (
          <View style={styles.routingOverlay}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        )}

        {/* 우측 플로팅 버튼 */}
        <View style={styles.floatingButtons}>
          <FAB icon="locate" onPress={handleLocate} />
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

      <CourseSearchSheet
        visible={isCourseSheetOpen}
        courses={nearbyCourses}
        isLoading={isLoadingCourses}
        onSelectCourse={handleSelectCourse}
        onClose={() => setIsCourseSheetOpen(false)}
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
    bottom: 16,
    gap: 10,
  },
  topRightButtons: {
    position: 'absolute',
    right: 16,
    top: 16,
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
