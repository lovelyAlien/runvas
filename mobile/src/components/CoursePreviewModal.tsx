import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  TouchableWithoutFeedback,
  ActivityIndicator,
  StyleSheet,
  Dimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

import KakaoMapView, { KakaoMapViewRef } from './KakaoMapView';
import { getCachedCourse } from '../services/courseCache';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { Course } from '../types';

type Props = {
  courseId: string | null;
  onClose: () => void;
};

const CLOSE_HIT_SLOP = { top: 8, bottom: 8, left: 8, right: 8 } as const;
const noop = () => {};
const SHEET_HEIGHT = Dimensions.get('window').height * 0.55;

export default function CoursePreviewModal({ courseId, onClose }: Props) {
  const { accessToken } = useAuth();
  const { bottom } = useSafeAreaInsets();
  const mapRef = useRef<KakaoMapViewRef>(null);
  const [course, setCourse] = useState<Course | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!courseId) {
      setCourse(null);
      return;
    }
    let active = true;
    setIsLoading(true);
    getCachedCourse(courseId, accessToken ?? undefined)
      .then((result) => { if (active) setCourse(result); })
      .catch(() => { if (active) setCourse(null); })
      .finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
  }, [courseId, accessToken]);

  const handleMapReady = useCallback(() => {
    if (!course) return;
    mapRef.current?.addRouteSegment(course.path);
    course.waypoints.forEach((wp, i) => mapRef.current?.addWaypoint(wp, i + 1));
    mapRef.current?.fitBounds(course.bounds);
  }, [course]);

  return (
    <Modal
      visible={courseId !== null}
      animationType="slide"
      transparent
      onRequestClose={onClose}
    >
      <TouchableWithoutFeedback onPress={onClose}>
        <View style={styles.overlay} />
      </TouchableWithoutFeedback>

      <View style={[styles.sheet, { paddingBottom: bottom || 16 }]}>
        <View style={styles.handle} />

        <View style={styles.header}>
          <Text style={styles.title} numberOfLines={1}>
            {course?.title ?? ''}
          </Text>
          <TouchableOpacity onPress={onClose} activeOpacity={0.7} hitSlop={CLOSE_HIT_SLOP}>
            <Ionicons name="close" size={20} color={Colors.gray900} />
          </TouchableOpacity>
        </View>

        {isLoading || !course ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        ) : (
          <KakaoMapView ref={mapRef} onMapPress={noop} onMapReady={handleMapReady} />
        )}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: SHEET_HEIGHT,
    backgroundColor: Colors.white,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    overflow: 'hidden',
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: Colors.gray300,
    alignSelf: 'center',
    marginTop: 8,
    marginBottom: 4,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  title: {
    flex: 1,
    fontSize: 15,
    fontWeight: '700',
    color: Colors.gray900,
    marginRight: 12,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
