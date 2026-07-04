import React, { forwardRef, useCallback, useImperativeHandle, useRef } from 'react';
import {
  Animated,
  FlatList,
  LayoutChangeEvent,
  PanResponder,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  ActivityIndicator,
} from 'react-native';
import { formatDistance, formatDuration } from '../utils/format';
import { Colors } from '../constants/theme';
import { CourseSummary } from '../types';

// 콘텐츠 높이 대비 이 비율 이상 아래로 끌면 접힘으로 스냅한다.
const DRAG_COLLAPSE_RATIO = 0.35;
// 이 속도(px/ms) 이상으로 빠르게 튕기면 드래그 거리와 상관없이 방향대로 스냅한다.
const FLICK_VELOCITY_THRESHOLD = 0.5;
// handleArea 스타일(paddingVertical 10*2 + handleBar 4)과 반드시 일치해야 한다 — MapScreen이
// 우측 FAB을 시트 상단 바로 위로 띄울 때 이 값을 기준으로 계산한다.
export const SHEET_HANDLE_HEIGHT = 24;

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

export interface CourseSearchSheetRef {
  expand: () => void; // 접혀 있는 시트를 다시 펼친다 (코스 탐색 FAB 재클릭 시 사용)
  translateY: Animated.Value; // 시트의 현재 이동값 — 다른 UI(탐색 버튼 등)를 시트 위치에 실시간으로 맞춰 움직일 때 사용
}

interface Props {
  visible: boolean;
  courses: CourseSummary[];
  isLoading: boolean;
  selectedCourseId: string | null;
  onSelectCourse: (courseId: string) => void;
  onViewDetail: (courseId: string) => void;
  onClose: () => void;
  onCollapsedChange?: (collapsed: boolean) => void; // 접힘/펼침 스냅이 끝날 때마다 알림 (다른 UI를 시트 위치에 맞춰 움직일 때 사용)
  onContentHeightChange?: (height: number) => void; // 콘텐츠(헤더+목록) 높이 측정값 전달
}

const CourseSearchSheet = forwardRef<CourseSearchSheetRef, Props>(function CourseSearchSheet(
  {
    visible,
    courses,
    isLoading,
    selectedCourseId,
    onSelectCourse,
    onViewDetail,
    onClose,
    onCollapsedChange,
    onContentHeightChange,
  },
  ref
) {
  const translateY = useRef(new Animated.Value(0)).current;
  const contentHeightRef = useRef(0); // 핸들을 제외한 콘텐츠(헤더+목록) 높이 — 접혔을 때 이 값만큼 내린다
  const isCollapsedRef = useRef(false);
  const dragStartValueRef = useRef(0);

  const snapTo = useCallback(
    (collapsed: boolean) => {
      isCollapsedRef.current = collapsed;
      onCollapsedChange?.(collapsed);
      Animated.timing(translateY, {
        toValue: collapsed ? contentHeightRef.current : 0,
        duration: 220,
        useNativeDriver: true,
      }).start();
    },
    [translateY, onCollapsedChange]
  );

  useImperativeHandle(ref, () => ({
    expand: () => snapTo(false),
    translateY,
  }));

  const handleContentLayout = useCallback(
    (e: LayoutChangeEvent) => {
      const height = e.nativeEvent.layout.height;
      contentHeightRef.current = height;
      onContentHeightChange?.(height);
      // 로딩 → 목록 전환 등으로 콘텐츠 높이가 바뀌면, 접힌 상태에서는 새 높이만큼 즉시 맞춰준다.
      if (isCollapsedRef.current) {
        translateY.setValue(height);
      }
    },
    [translateY, onContentHeightChange]
  );

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_evt, gestureState) => Math.abs(gestureState.dy) > 4,
      onPanResponderGrant: () => {
        dragStartValueRef.current = isCollapsedRef.current ? contentHeightRef.current : 0;
      },
      onPanResponderMove: (_evt, gestureState) => {
        const next = clamp(
          dragStartValueRef.current + gestureState.dy,
          0,
          contentHeightRef.current
        );
        translateY.setValue(next);
      },
      onPanResponderRelease: (_evt, gestureState) => {
        const dragged = dragStartValueRef.current + gestureState.dy;
        let shouldCollapse: boolean;
        if (gestureState.vy > FLICK_VELOCITY_THRESHOLD) {
          shouldCollapse = true;
        } else if (gestureState.vy < -FLICK_VELOCITY_THRESHOLD) {
          shouldCollapse = false;
        } else {
          shouldCollapse = dragged > contentHeightRef.current * DRAG_COLLAPSE_RATIO;
        }
        snapTo(shouldCollapse);
      },
    })
  ).current;

  // Modal은 화면 전체를 별도 네이티브 레이어로 덮어써서, 배경을 투명하게 해도 시트 밖(지도) 영역의
  // 터치가 막힌다 (특히 Android). 코스 탐색 중에도 지도를 이동할 수 있어야 하므로 Modal 대신
  // MapScreen의 mapContainer 안에 절대 위치로 떠 있는 일반 뷰로 렌더링한다.
  if (!visible) return null;

  return (
    <Animated.View style={[styles.sheet, { transform: [{ translateY }] }]}>
      <View style={styles.handleArea} {...panResponder.panHandlers}>
        <View style={styles.handleBar} />
      </View>

      <View onLayout={handleContentLayout}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>주변 코스</Text>
          <TouchableOpacity onPress={onClose} activeOpacity={0.7}>
            <Text style={styles.closeLabel}>닫기</Text>
          </TouchableOpacity>
        </View>

        {isLoading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        ) : (
          <FlatList
            data={courses}
            keyExtractor={(item) => item.id}
            contentContainerStyle={courses.length === 0 ? styles.emptyContainer : undefined}
            ListEmptyComponent={
              <Text style={styles.emptyText}>주변에 표시된 코스가 없습니다.</Text>
            }
            renderItem={({ item }) => {
              const isSelected = item.id === selectedCourseId;
              return (
                <View style={[styles.row, isSelected && styles.rowSelected]}>
                  <TouchableOpacity
                    style={styles.rowInfo}
                    activeOpacity={0.7}
                    onPress={() => onSelectCourse(item.id)}
                  >
                    <Text style={styles.rowTitle} numberOfLines={1}>
                      {item.title}
                    </Text>
                    <Text style={styles.rowMeta}>
                      {formatDistance(item.distanceMeters)} ·{' '}
                      {formatDuration(item.estimatedDurationSeconds)}
                      {item.tags.length > 0 ? ` · ${item.tags.join(', ')}` : ''}
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.detailButton, isSelected && styles.detailButtonActive]}
                    activeOpacity={0.7}
                    disabled={!isSelected}
                    onPress={() => onViewDetail(item.id)}
                  >
                    <Text
                      style={[
                        styles.detailButtonLabel,
                        isSelected && styles.detailButtonLabelActive,
                      ]}
                    >
                      상세 보기
                    </Text>
                  </TouchableOpacity>
                </View>
              );
            }}
          />
        )}
      </View>
    </Animated.View>
  );
});

export default CourseSearchSheet;

const styles = StyleSheet.create({
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    maxHeight: '60%',
    backgroundColor: Colors.white,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 8,
  },
  handleArea: {
    alignItems: 'center',
    paddingVertical: 10,
  },
  handleBar: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: Colors.gray300,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  closeLabel: {
    fontSize: 14,
    color: Colors.gray500,
  },
  loadingContainer: {
    paddingVertical: 40,
    alignItems: 'center',
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  rowSelected: {
    backgroundColor: Colors.gray50,
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
  detailButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: Colors.gray100,
  },
  detailButtonActive: {
    backgroundColor: Colors.primary,
  },
  detailButtonLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.gray400,
  },
  detailButtonLabelActive: {
    color: Colors.white,
  },
});
