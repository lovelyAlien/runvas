import React from 'react';
import { View, Text, StyleSheet, ViewStyle } from 'react-native';
import { Colors } from '../constants/theme';

interface Props {
  tags: string[];
  style?: ViewStyle;
}

export default function TagList({ tags, style }: Props) {
  if (!tags || tags.length === 0) return null;
  return (
    <View style={[styles.container, style]}>
      {tags.map((tag) => (
        <View key={tag} style={styles.chip}>
          <Text style={styles.chipText}>#{tag}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  chip: {
    backgroundColor: Colors.gray100,
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  chipText: {
    fontSize: 12,
    color: Colors.gray500,
    fontWeight: '500',
  },
});
