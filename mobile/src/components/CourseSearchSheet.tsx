import React from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Modal,
  StyleSheet,
} from 'react-native';
import { formatDistance, formatDuration } from '../utils/format';
import { Colors } from '../constants/theme';
import { CourseSummary } from '../types';

interface Props {
  visible: boolean;
  courses: CourseSummary[];
  isLoading: boolean;
  onSelectCourse: (courseId: string) => void;
  onClose: () => void;
}

export default function CourseSearchSheet({
  visible,
  courses,
  isLoading,
  onSelectCourse,
  onClose,
}: Props) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
        <View style={styles.sheet}>
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
              renderItem={({ item }) => (
                <TouchableOpacity
                  style={styles.row}
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
              )}
            />
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  sheet: {
    backgroundColor: Colors.white,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    maxHeight: '60%',
    paddingBottom: 20,
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
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
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
});
