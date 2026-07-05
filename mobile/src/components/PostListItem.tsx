import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../constants/theme';
import { Post } from '../types';

interface Props {
  post: Post;
  onPress: (postId: string) => void;
}

export default function PostListItem({ post, onPress }: Props) {
  return (
    <TouchableOpacity style={styles.row} activeOpacity={0.7} onPress={() => onPress(post.id)}>
      <View style={styles.rowHeader}>
        <Text style={styles.title} numberOfLines={1}>
          {post.title}
        </Text>
        {post.attachedCourseId && (
          <Ionicons name="map-outline" size={14} color={Colors.primary} style={styles.courseIcon} />
        )}
      </View>
      <Text style={styles.body} numberOfLines={2}>
        {post.body}
      </Text>
      <View style={styles.metaRow}>
        <Text style={styles.metaText}>{post.author.nickname}</Text>
        <Text style={styles.metaDot}>·</Text>
        <Text style={styles.metaText}>{new Date(post.createdAt).toLocaleDateString()}</Text>
        <Text style={styles.metaDot}>·</Text>
        <Ionicons name="heart-outline" size={12} color={Colors.gray400} />
        <Text style={styles.metaText}>{post.likeCount}</Text>
        <Ionicons name="chatbubble-outline" size={12} color={Colors.gray400} style={styles.commentIcon} />
        <Text style={styles.metaText}>{post.commentCount}</Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  rowHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  title: {
    flex: 1,
    fontSize: 15,
    fontWeight: '600',
    color: Colors.gray900,
  },
  courseIcon: {
    marginLeft: 6,
  },
  body: {
    fontSize: 13,
    color: Colors.gray500,
    marginTop: 4,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    gap: 4,
  },
  metaText: {
    fontSize: 12,
    color: Colors.gray400,
  },
  metaDot: {
    fontSize: 12,
    color: Colors.gray300,
  },
  commentIcon: {
    marginLeft: 6,
  },
});
