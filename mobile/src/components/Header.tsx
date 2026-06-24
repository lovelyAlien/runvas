import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors } from '../constants/theme';

interface Props {
  pointCount: number;
  isRouting?: boolean;
}

export default function Header({ pointCount, isRouting }: Props) {
  const hint = isRouting
    ? '보행 경로를 탐색하는 중...'
    : pointCount === 0
    ? '지도를 탭해서 코스를 그려보세요'
    : `${pointCount}개 포인트 — 계속 탭해서 경로를 이어가세요`;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Runvas</Text>
      <Text style={styles.hint}>{hint}</Text>
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
