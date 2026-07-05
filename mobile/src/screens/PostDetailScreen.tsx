import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  Alert,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { getPost } from '../services/postApi';
import { getComments, createComment } from '../services/commentApi';
import { putLike, deleteLike } from '../services/likeApi';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';
import { Post, Comment } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PostDetail'>;

export default function PostDetailScreen({ route, navigation }: Props) {
  const { postId } = route.params;
  const { accessToken, user } = useAuth();
  const { requireAuth } = useAuthGate();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [commentBody, setCommentBody] = useState('');
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);

  const loadPost = useCallback(async () => {
    try {
      const [postResult, commentsResult] = await Promise.all([
        getPost(postId),
        getComments(postId),
      ]);
      setPost(postResult);
      setComments(commentsResult);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      navigation.goBack();
    } finally {
      setIsLoading(false);
    }
  }, [postId, navigation]);

  useFocusEffect(
    useCallback(() => {
      loadPost();
    }, [loadPost])
  );

  const handleToggleLike = async () => {
    if (!requireAuth() || !post || !accessToken) return;
    try {
      const result = post.likedByMe
        ? await deleteLike(post.id, accessToken)
        : await putLike(post.id, accessToken);
      setPost({ ...post, likedByMe: result.liked, likeCount: result.likeCount });
    } catch (e: unknown) {
      Alert.alert('실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    }
  };

  const handleSubmitComment = async () => {
    if (!requireAuth() || !accessToken || !user || !commentBody.trim()) return;
    setIsSubmittingComment(true);
    try {
      const comment = await createComment(
        postId,
        { body: commentBody.trim() },
        accessToken,
        {
          id: user.id,
          nickname: user.nickname,
          profileImageUrl: user.profileImageUrl,
          bio: user.bio,
        }
      );
      setComments((prev) => [...prev, comment]);
      setCommentBody('');
      setPost((prev) => (prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev));
    } catch (e: unknown) {
      Alert.alert('작성 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmittingComment(false);
    }
  };

  if (isLoading || !post) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {post.title}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      <FlatList
        data={comments}
        keyExtractor={(item) => item.id}
        ListHeaderComponent={
          <View style={styles.postBody}>
            <Text style={styles.postTitle}>{post.title}</Text>
            <Text style={styles.postMeta}>
              {post.author.nickname} · {new Date(post.createdAt).toLocaleDateString()}
            </Text>
            {post.attachedCourseId && (
              <TouchableOpacity
                style={styles.courseChip}
                activeOpacity={0.7}
                onPress={() =>
                  navigation.navigate('CourseDetail', { courseId: post.attachedCourseId as string })
                }
              >
                <Ionicons name="map-outline" size={14} color={Colors.primary} />
                <Text style={styles.courseChipLabel}>첨부된 코스 보기</Text>
              </TouchableOpacity>
            )}
            <Text style={styles.postText}>{post.body}</Text>
            <TouchableOpacity style={styles.likeButton} onPress={handleToggleLike} activeOpacity={0.7}>
              <Ionicons
                name={post.likedByMe ? 'heart' : 'heart-outline'}
                size={18}
                color={post.likedByMe ? Colors.danger : Colors.gray500}
              />
              <Text style={styles.likeCount}>{post.likeCount}</Text>
            </TouchableOpacity>
            <Text style={styles.commentsHeading}>댓글 {comments.length}</Text>
          </View>
        }
        renderItem={({ item }) => (
          <View style={styles.commentRow}>
            <Text style={styles.commentAuthor}>{item.author.nickname}</Text>
            <Text style={styles.commentBody}>{item.body}</Text>
          </View>
        )}
        ListEmptyComponent={<Text style={styles.emptyComments}>아직 댓글이 없습니다.</Text>}
      />

      {user ? (
        <View style={styles.commentInputRow}>
          <TextInput
            style={styles.commentInput}
            placeholder="댓글을 입력하세요"
            placeholderTextColor={Colors.gray400}
            value={commentBody}
            onChangeText={setCommentBody}
          />
          <TouchableOpacity onPress={handleSubmitComment} disabled={isSubmittingComment} activeOpacity={0.7}>
            {isSubmittingComment ? (
              <ActivityIndicator size="small" color={Colors.primary} />
            ) : (
              <Text style={styles.commentSubmitLabel}>등록</Text>
            )}
          </TouchableOpacity>
        </View>
      ) : (
        <TouchableOpacity style={styles.loginPrompt} onPress={() => requireAuth()} activeOpacity={0.7}>
          <Text style={styles.loginPromptLabel}>로그인하고 댓글 남기기</Text>
        </TouchableOpacity>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  loadingContainer: {
    flex: 1,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  headerSpacer: {
    width: 24,
  },
  postBody: {
    paddingHorizontal: 20,
    paddingTop: 16,
  },
  postTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
  },
  postMeta: {
    fontSize: 12,
    color: Colors.gray500,
    marginTop: 6,
  },
  courseChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 12,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: Colors.gray50,
    alignSelf: 'flex-start',
  },
  courseChipLabel: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '600',
  },
  postText: {
    fontSize: 14,
    color: Colors.gray900,
    marginTop: 16,
    lineHeight: 20,
  },
  likeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 16,
  },
  likeCount: {
    fontSize: 13,
    color: Colors.gray500,
  },
  commentsHeading: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.gray900,
    marginTop: 20,
    marginBottom: 8,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
    paddingTop: 16,
  },
  commentRow: {
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  commentAuthor: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.gray900,
  },
  commentBody: {
    fontSize: 13,
    color: Colors.gray500,
    marginTop: 2,
  },
  emptyComments: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    fontSize: 13,
    color: Colors.gray400,
  },
  commentInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  commentInput: {
    flex: 1,
    fontSize: 14,
    color: Colors.gray900,
    paddingVertical: 8,
  },
  commentSubmitLabel: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.primary,
  },
  loginPrompt: {
    paddingVertical: 14,
    alignItems: 'center',
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  loginPromptLabel: {
    fontSize: 13,
    color: Colors.primary,
    fontWeight: '600',
  },
});
