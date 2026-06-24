import React, { useRef, useCallback, useState } from 'react';
import {
  View,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { Ionicons } from '@expo/vector-icons';

import Header from './src/components/Header';
import KakaoMapView, { KakaoMapViewRef } from './src/components/KakaoMapView';
import RouteStatsBar from './src/components/RouteStatsBar';
import { useRoute } from './src/hooks/useRoute';
import { useLocation } from './src/hooks/useLocation';
import { fetchPedestrianRoute } from './src/utils/tmapRouting';
import { exportGpx } from './src/utils/exportGpx';
import { Colors } from './src/constants/theme';
import { Coordinate } from './src/types';

export default function App() {
  const mapRef = useRef<KakaoMapViewRef>(null);
  const {
    waypoints,
    routeCoords,
    stats,
    addFirstPoint,
    addSegment,
    undoLast,
    clearRoute,
    toRoutePoints,
  } = useRoute();

  const { getCurrentLocation } = useLocation();
  const [isExporting, setIsExporting] = useState(false);
  const [isRouting, setIsRouting] = useState(false); // 경로 탐색 중 여부

  const handleMapPress = useCallback(
    async (coord: Coordinate) => {
      if (isRouting) return; // 이미 경로 탐색 중이면 무시

      // 첫 번째 포인트: 마커만 추가
      if (waypoints.length === 0) {
        addFirstPoint(coord);
        mapRef.current?.addWaypoint(coord, 1);
        return;
      }

      // 두 번째 이후: T-MAP API로 이전 포인트 → 새 포인트 경로 탐색
      const prevWaypoint = waypoints[waypoints.length - 1];
      setIsRouting(true);
      try {
        const segmentCoords = await fetchPedestrianRoute(prevWaypoint, coord);
        addSegment(coord, segmentCoords);
        mapRef.current?.addWaypoint(coord, waypoints.length + 1);
        mapRef.current?.addRouteSegment(segmentCoords);
      } finally {
        setIsRouting(false);
      }
    },
    [waypoints, isRouting, addFirstPoint, addSegment]
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

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="dark" />

      <Header pointCount={waypoints.length} isRouting={isRouting} />

      <View style={styles.mapContainer}>
        <KakaoMapView
          ref={mapRef}
          onMapPress={handleMapPress}
        />

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
      />
    </SafeAreaView>
  );
}

interface FABProps {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
  disabled?: boolean;
  color?: string;
  loading?: boolean;
}

function FAB({ icon, onPress, disabled, color = Colors.primary, loading }: FABProps) {
  return (
    <TouchableOpacity
      style={[styles.fab, disabled && styles.fabDisabled]}
      onPress={onPress}
      disabled={disabled}
      activeOpacity={0.8}
    >
      {loading ? (
        <ActivityIndicator size="small" color={color} />
      ) : (
        <Ionicons name={icon} size={20} color={disabled ? Colors.gray300 : color} />
      )}
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
});
