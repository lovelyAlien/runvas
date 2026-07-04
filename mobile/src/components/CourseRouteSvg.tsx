import React, { useEffect, useMemo, useState } from 'react';
import { View, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import Svg, { Polyline } from 'react-native-svg';
import { Ionicons } from '@expo/vector-icons';

import { getCachedCourse } from '../services/courseCache';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { RoutePoint } from '../types';

type Props = {
  courseId: string;
  size?: number;
  onPress: () => void;
};

const PADDING = 6;
const FALLBACK_ICON_SIZE = 20;

function normalizePoints(path: RoutePoint[], size: number): string {
  if (path.length < 2) return '';

  const lats = path.map(p => p.latitude);
  const lngs = path.map(p => p.longitude);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);

  const latSpan = maxLat - minLat || 1e-6;
  const lngSpan = maxLng - minLng || 1e-6;
  const drawSize = size - PADDING * 2;

  return path
    .map(p => {
      const x = PADDING + ((p.longitude - minLng) / lngSpan) * drawSize;
      // latitude 증가 = 위쪽 → SVG y 감소
      const y = PADDING + ((maxLat - p.latitude) / latSpan) * drawSize;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
}

export default function CourseRouteSvg({ courseId, size = 72, onPress }: Props) {
  const { accessToken } = useAuth();
  const [path, setPath] = useState<RoutePoint[] | null>(null);
  const [hasError, setHasError] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setIsLoading(true);
    setHasError(false);
    getCachedCourse(courseId, accessToken ?? undefined)
      .then(course => { if (active) setPath(course.path); })
      .catch(() => { if (active) setHasError(true); })
      .finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
  }, [courseId, accessToken]);

  const points = useMemo(
    () => (path ? normalizePoints(path, size) : ''),
    [path, size],
  );

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      style={[styles.container, { width: size, height: size }]}
    >
      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator size="small" color={Colors.gray400} />
        </View>
      ) : hasError || !points ? (
        <View style={styles.center}>
          <Ionicons name="map-outline" size={FALLBACK_ICON_SIZE} color={Colors.gray400} />
        </View>
      ) : (
        <Svg width={size} height={size}>
          <Polyline
            points={points}
            fill="none"
            stroke={Colors.primary}
            strokeWidth={2.5}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </Svg>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: Colors.gray100,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
