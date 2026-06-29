import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { RouteStats } from '../types';
import { Colors } from '../constants/theme';
import { formatDistance, formatDuration } from '../utils/format';

interface Props {
  stats: RouteStats;
  onExport: () => void;
  isExporting: boolean;
}

export default function RouteStatsBar({ stats, onExport, isExporting }: Props) {
  const canExport = stats.pointCount >= 2;

  return (
    <View style={styles.container}>
      <View style={styles.stats}>
        <StatItem label="거리" value={formatDistance(stats.distanceMeters)} />
        <Divider />
        <StatItem label="예상 시간" value={formatDuration(stats.estimatedDurationSeconds)} />
        <Divider />
        <StatItem label="포인트" value={`${stats.pointCount}개`} />
      </View>

      <TouchableOpacity
        style={[styles.exportButton, !canExport && styles.exportButtonDisabled]}
        onPress={onExport}
        disabled={!canExport || isExporting}
        activeOpacity={0.8}
      >
        {isExporting ? (
          <ActivityIndicator size="small" color={Colors.white} />
        ) : (
          <>
            <Ionicons
              name="download-outline"
              size={16}
              color={canExport ? Colors.white : Colors.gray400}
            />
            <Text style={[styles.exportLabel, !canExport && styles.exportLabelDisabled]}>
              GPX
            </Text>
          </>
        )}
      </TouchableOpacity>
    </View>
  );
}

function StatItem({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.statItem}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

function Divider() {
  return <View style={styles.divider} />;
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    backgroundColor: Colors.white,
    paddingVertical: 12,
    paddingHorizontal: 16,
    alignItems: 'center',
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
    gap: 12,
  },
  stats: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
  },
  statItem: {
    alignItems: 'center',
    flex: 1,
  },
  label: {
    fontSize: 10,
    color: Colors.gray400,
    marginBottom: 2,
  },
  value: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.gray900,
  },
  divider: {
    width: 1,
    height: 28,
    backgroundColor: Colors.gray100,
  },
  exportButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.success,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 10,
    gap: 4,
  },
  exportButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  exportLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.white,
  },
  exportLabelDisabled: {
    color: Colors.gray400,
  },
});
