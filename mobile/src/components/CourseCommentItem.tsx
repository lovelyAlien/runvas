import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Colors } from '../constants/theme';
import { CourseComment } from '../types';

interface Props {
  comment: CourseComment;
  currentUserId?: string | null;
  onDelete?: (commentId: string, parentCommentId: string | null) => void;
  isReply?: boolean;
  onReply?: (comment: CourseComment) => void;
  replies?: CourseComment[];
  isRepliesExpanded?: boolean;
  isRepliesLoading?: boolean;
  onToggleReplies?: (commentId: string) => void;
}

export default function CourseCommentItem({
  comment,
  currentUserId,
  onDelete,
  isReply = false,
  onReply,
  replies,
  isRepliesExpanded = false,
  isRepliesLoading = false,
  onToggleReplies,
}: Props) {
  const isMine = currentUserId != null && currentUserId === comment.author.id;

  return (
    <View style={[styles.container, isReply && styles.replyContainer]}>
      <View style={styles.header}>
        <Text style={styles.nickname} numberOfLines={1}>
          {comment.author.nickname}
        </Text>
        {isMine && onDelete && (
          <TouchableOpacity
            onPress={() => onDelete(comment.id, comment.parentCommentId)}
            activeOpacity={0.7}
            hitSlop={8}
          >
            <Ionicons name="trash-outline" size={16} color={Colors.gray400} />
          </TouchableOpacity>
        )}
      </View>
      <Text style={styles.body}>{comment.body}</Text>
      {comment.imageUrl && (
        <Image source={{ uri: comment.imageUrl }} style={styles.image} resizeMode="cover" />
      )}

      {!isReply && (
        <View style={styles.actionsRow}>
          {onReply && (
            <TouchableOpacity onPress={() => onReply(comment)} activeOpacity={0.7}>
              <Text style={styles.actionText}>답글 달기</Text>
            </TouchableOpacity>
          )}
          {comment.replyCount > 0 && onToggleReplies && (
            <TouchableOpacity onPress={() => onToggleReplies(comment.id)} activeOpacity={0.7}>
              <Text style={styles.actionText}>
                {isRepliesExpanded ? '답글 숨기기' : `답글 ${comment.replyCount}개 보기`}
              </Text>
            </TouchableOpacity>
          )}
        </View>
      )}

      {!isReply && isRepliesExpanded && (
        <View style={styles.repliesList}>
          {isRepliesLoading ? (
            <ActivityIndicator size="small" color={Colors.primary} style={styles.repliesLoading} />
          ) : (
            replies?.map((reply) => (
              <CourseCommentItem
                key={reply.id}
                comment={reply}
                currentUserId={currentUserId}
                onDelete={onDelete}
                isReply
              />
            ))
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  replyContainer: {
    paddingVertical: 8,
    borderBottomWidth: 0,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  nickname: {
    flex: 1,
    fontSize: 13,
    fontWeight: '700',
    color: Colors.gray900,
  },
  body: {
    fontSize: 14,
    color: Colors.gray900,
    lineHeight: 20,
  },
  image: {
    marginTop: 8,
    width: 120,
    height: 120,
    borderRadius: 8,
    backgroundColor: Colors.gray100,
  },
  actionsRow: {
    flexDirection: 'row',
    gap: 16,
    marginTop: 6,
  },
  actionText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.gray500,
  },
  repliesList: {
    marginTop: 8,
    marginLeft: 16,
    paddingLeft: 10,
    borderLeftWidth: 2,
    borderLeftColor: Colors.gray100,
  },
  repliesLoading: {
    marginVertical: 8,
  },
});
