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
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CompositeScreenProps } from '@react-navigation/native';

import Header from '../components/Header';
import KakaoMapView, { KakaoMapViewRef, PublicCourseMarker } from '../components/KakaoMapView';
import RouteStatsBar from '../components/RouteStatsBar';
import PaceSelector from '../components/PaceSelector';
import { useRoute, DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { useLocation } from '../hooks/useLocation';
import { useAuth } from '../contexts/AuthContext';
import { fetchPedestrianRoute } from '../services/routingApi';
import { exportGpx } from '../utils/exportGpx';
import { postCourse, buildCreateCourseRequest, getPublicCourses } from '../services/courseApi';
import { patchMe } from '../services/authApi';
import { Colors } from '../constants/theme';
import { Coordinate, GeoBounds } from '../types';
import { formatPace } from '../utils/format';
import { RootTabParamList, RootStackParamList } from '../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Map'>,
  NativeStackScreenProps<RootStackParamList>
>;

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
  const [isRouting, setIsRouting] = useState(false);
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [routeTitle, setRouteTitle] = useState('');
  const [isPaceSelectorOpen, setIsPaceSelectorOpen] = useState(false);
  const [isSavingPace, setIsSavingPace] = useState(false);
  const [isBrowseMode, setIsBrowseMode] = useState(false);
  const [isFetchingCourses, setIsFetchingCourses] = useState(false);

  const boundsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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
      } catch {
        // 네트워크 오류 시 마커 표시 생략
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
    setIsBrowseMode((prev) => {
      const next = !prev;
      mapRef.current?.setBrowseMode(next);
      if (!next) {
        mapRef.current?.clearPublicCourses();
        if (boundsTimerRef.current) clearTimeout(boundsTimerRef.current);
      }
      return next;
    });
  }, []);

  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isBrowseMode) return;
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
    [waypoints, isRouting, isBrowseMode, accessToken, addFirstPoint, addSegment]
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
    <SafeAreaView style={styles.container}>
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

        <View style={styles.floatingButtons}>
          <FAB
            icon={isBrowseMode ? 'pencil' : 'search'}
            onPress={toggleBrowseMode}
            color={isBrowseMode ? Colors.primary : Colors.gray500}
          />
          <FAB icon="locate" onPress={handleLocate} />
          {!isBrowseMode && (
            <>
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
