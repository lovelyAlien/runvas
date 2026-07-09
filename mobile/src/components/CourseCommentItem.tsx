import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Colors } from '../constants/theme';
import { CourseComment } from '../types';
import { formatDateTime } from '../utils/format';

interface Props {
  comment: CourseComment;
  currentUserId?: string | null;
  onDelete: (commentId: string, parentCommentId: string | null) => void;
  onReply: (comment: CourseComment) => void;
  onUpdate: (commentId: string, parentCommentId: string | null, body: string) => Promise<void>;
  onToggleReplies: (commentId: string) => void;
  repliesByParentId: Record<string, CourseComment[]>;
  expandedReplyIds: Record<string, boolean>;
  loadingReplyIds: Record<string, boolean>;
  activeReplyId: string | null;
  replyBody: string;
  isSubmittingReply: boolean;
  onChangeReplyBody: (text: string) => void;
  onSubmitReply: (parentCommentId: string) => void;
  onCancelReply: () => void;
}

export default function CourseCommentItem({
  comment,
  currentUserId,
  onDelete,
  onReply,
  onUpdate,
  onToggleReplies,
  repliesByParentId,
  expandedReplyIds,
  loadingReplyIds,
  activeReplyId,
  replyBody,
  isSubmittingReply,
  onChangeReplyBody,
  onSubmitReply,
  onCancelReply,
}: Props) {
  const [isEditing, setIsEditing] = useState(false);
  const [editBody, setEditBody] = useState(comment.body);
  const [isSaving, setIsSaving] = useState(false);

  const isMine = currentUserId != null && currentUserId === comment.author.id;
  const isReply = comment.parentCommentId !== null;
  const replies = repliesByParentId[comment.id];
  const isRepliesExpanded = expandedReplyIds[comment.id] ?? false;
  const isRepliesLoading = loadingReplyIds[comment.id] ?? false;
  const isReplyFormOpen = activeReplyId === comment.id;

  const handleStartEdit = () => {
    setEditBody(comment.body);
    setIsEditing(true);
  };

  const handleCancelEdit = () => {
    setIsEditing(false);
    setEditBody(comment.body);
  };

  const handleSaveEdit = async () => {
    const trimmedBody = editBody.trim();
    if (!trimmedBody) {
      Alert.alert('알림', '댓글 내용을 입력해주세요.');
      return;
    }

    setIsSaving(true);
    try {
      await onUpdate(comment.id, comment.parentCommentId, trimmedBody);
      setIsEditing(false);
    } catch (e: unknown) {
      Alert.alert('수정 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <View style={[styles.container, isReply && styles.replyContainer]}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.nickname} numberOfLines={1}>
            {comment.author.nickname}
          </Text>
          <Text style={styles.time}>{formatDateTime(comment.createdAt)}</Text>
        </View>
        {isMine && !isEditing && (
          <View style={styles.headerActions}>
            <TouchableOpacity onPress={handleStartEdit} activeOpacity={0.7} hitSlop={8}>
              <Ionicons name="pencil-outline" size={15} color={Colors.gray400} />
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => onDelete(comment.id, comment.parentCommentId)}
              activeOpacity={0.7}
              hitSlop={8}
            >
              <Ionicons name="trash-outline" size={16} color={Colors.gray400} />
            </TouchableOpacity>
          </View>
        )}
      </View>

      {isEditing ? (
        <View style={styles.editForm}>
          <TextInput
            style={styles.editInput}
            value={editBody}
            onChangeText={setEditBody}
            multiline
            autoFocus
          />
          <View style={styles.editActions}>
            <TouchableOpacity onPress={handleCancelEdit} activeOpacity={0.7} disabled={isSaving}>
              <Text style={styles.editCancelText}>취소</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={handleSaveEdit} activeOpacity={0.7} disabled={isSaving}>
              {isSaving ? (
                <ActivityIndicator size="small" color={Colors.primary} />
              ) : (
                <Text style={styles.editSaveText}>저장</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <Text style={styles.body}>{comment.body}</Text>
      )}

      <View style={styles.actionsRow}>
        <TouchableOpacity onPress={() => onReply(comment)} activeOpacity={0.7}>
          <Text style={styles.actionText}>{isReplyFormOpen ? '답글 취소' : '답글 달기'}</Text>
        </TouchableOpacity>
        {comment.replyCount > 0 && (
          <TouchableOpacity onPress={() => onToggleReplies(comment.id)} activeOpacity={0.7}>
            <Text style={styles.actionText}>
              {isRepliesExpanded ? '답글 숨기기' : `답글 ${comment.replyCount}개 보기`}
            </Text>
          </TouchableOpacity>
        )}
      </View>

      {isReplyFormOpen && (
        <View style={styles.replyForm}>
          <TextInput
            style={styles.replyInput}
            placeholder={`${comment.author.nickname}님에게 답글 남기기`}
            placeholderTextColor={Colors.gray400}
            value={replyBody}
            onChangeText={onChangeReplyBody}
            multiline
            autoFocus
          />
          <View style={styles.replyFormActions}>
            <TouchableOpacity onPress={onCancelReply} activeOpacity={0.7} disabled={isSubmittingReply}>
              <Text style={styles.replyCancelText}>취소</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => onSubmitReply(comment.id)}
              activeOpacity={0.7}
              disabled={isSubmittingReply}
            >
              {isSubmittingReply ? (
                <ActivityIndicator size="small" color={Colors.primary} />
              ) : (
                <Text style={styles.replySubmitText}>등록</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      )}

      {isRepliesExpanded && (
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
                onReply={onReply}
                onUpdate={onUpdate}
                onToggleReplies={onToggleReplies}
                repliesByParentId={repliesByParentId}
                expandedReplyIds={expandedReplyIds}
                loadingReplyIds={loadingReplyIds}
                activeReplyId={activeReplyId}
                replyBody={replyBody}
                isSubmittingReply={isSubmittingReply}
                onChangeReplyBody={onChangeReplyBody}
                onSubmitReply={onSubmitReply}
                onCancelReply={onCancelReply}
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
  headerLeft: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 6,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  nickname: {
    flexShrink: 1,
    fontSize: 13,
    fontWeight: '700',
    color: Colors.gray900,
  },
  time: {
    fontSize: 11,
    color: Colors.gray400,
  },
  body: {
    fontSize: 14,
    color: Colors.gray900,
    lineHeight: 20,
  },
  editForm: {
    marginTop: 2,
  },
  editInput: {
    minHeight: 40,
    maxHeight: 100,
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontSize: 14,
    color: Colors.gray900,
  },
  editActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 16,
    marginTop: 6,
  },
  editCancelText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.gray500,
  },
  editSaveText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.primary,
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
  replyForm: {
    marginTop: 8,
    marginLeft: 16,
    padding: 8,
    backgroundColor: Colors.gray100,
    borderRadius: 8,
  },
  replyInput: {
    minHeight: 36,
    maxHeight: 100,
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontSize: 13,
    color: Colors.gray900,
    backgroundColor: Colors.white,
  },
  replyFormActions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 14,
    marginTop: 6,
  },
  replyCancelText: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.gray500,
  },
  replySubmitText: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.primary,
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
