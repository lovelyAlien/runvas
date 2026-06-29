import React, { useCallback, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, Alert, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import KakaoMapView, { KakaoMapViewRef } from '../components/KakaoMapView';
import RouteStatsBar from '../components/RouteStatsBar';
import { getCourse } from '../services/courseApi';
import { exportGpx } from '../utils/exportGpx';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { Course } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'CourseDetail'>;

export default function CourseDetailScreen({ route, navigation }: Props) {
  const { courseId } = route.params;
  const { accessToken } = useAuth();
  const mapRef = useRef<KakaoMapViewRef>(null);
  const [course, setCourse] = useState<Course | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);

  useFocusEffect(
    useCallback(() => {
      let isActive = true;
      getCourse(courseId, accessToken ?? undefined)
        .then((result) => {
          if (isActive) setCourse(result);
        })
        .catch((e: unknown) => {
          Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          navigation.goBack();
        })
        .finally(() => {
          if (isActive) setIsLoading(false);
        });
      return () => {
        isActive = false;
      };
    }, [courseId, accessToken, navigation])
  );

  const handleMapReady = () => {
    if (!course) return;
    mapRef.current?.addRouteSegment(course.path);
    course.waypoints.forEach((wp, i) => mapRef.current?.addWaypoint(wp, i + 1));
    mapRef.current?.fitBounds(course.bounds);
  };

  const handleExport = async () => {
    if (!course) return;
    setIsExporting(true);
    try {
      await exportGpx(course.path, course.title);
    } catch (e: unknown) {
      Alert.alert('내보내기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsExporting(false);
    }
  };

  if (isLoading || !course) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {course.title}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      <View style={styles.mapContainer}>
        <KakaoMapView ref={mapRef} onMapPress={() => {}} onMapReady={handleMapReady} />
      </View>

      <RouteStatsBar
        stats={{
          distanceMeters: course.distanceMeters,
          estimatedDurationSeconds: course.estimatedDurationSeconds,
          pointCount: course.waypoints.length,
        }}
        onExport={handleExport}
        isExporting={isExporting}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  loadingContainer: {
    flex: 1,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
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
  mapContainer: {
    flex: 1,
  },
});
