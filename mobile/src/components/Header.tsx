import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../constants/theme';

interface Props {
  pointCount: number;
  isRouting?: boolean;
  onPressSavedRoutes?: () => void;
}

export default function Header({ pointCount, isRouting, onPressSavedRoutes }: Props) {
  const hint = isRouting
    ? '보행 경로를 탐색하는 중...'
    : pointCount === 0
    ? '지도를 탭해서 코스를 그려보세요'
    : `${pointCount}개 포인트 — 계속 탭해서 경로를 이어가세요`;

  return (
    <View style={styles.container}>
      <View style={styles.titleRow}>
        <View>
          <Text style={styles.title}>RunSketch</Text>
          <Text style={styles.hint}>{hint}</Text>
        </View>
        {onPressSavedRoutes && (
          <TouchableOpacity onPress={onPressSavedRoutes} activeOpacity={0.7}>
            <Ionicons name="bookmark-outline" size={22} color={Colors.gray900} />
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.white,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
  },
  title: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.primary,
    letterSpacing: -0.5,
  },
  hint: {
    fontSize: 12,
    color: Colors.gray500,
    marginTop: 2,
  },
});
