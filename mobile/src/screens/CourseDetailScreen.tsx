import React, { useCallback, useRef, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  ScrollView,
  Image,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  Keyboard,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import * as ImagePicker from 'expo-image-picker';

import KakaoMapView, { KakaoMapViewRef } from '../components/KakaoMapView';
import RouteStatsBar from '../components/RouteStatsBar';
import TagList from '../components/TagList';
import CourseCommentItem from '../components/CourseCommentItem';
import { getCourse } from '../services/courseApi';
import { putLike, deleteLike } from '../services/likeApi';
import { postBookmark, deleteBookmark } from '../services/bookmarkApi';
import {
  getCourseComments,
  getCourseCommentReplies,
  createCourseComment,
  deleteCourseComment,
  CourseCommentImageInput,
} from '../services/courseCommentApi';
import { exportGpx } from '../utils/exportGpx';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { Colors } from '../constants/theme';
import { Course, CourseComment } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'CourseDetail'>;

export default function CourseDetailScreen({ route, navigation }: Props) {
  const { courseId } = route.params;
  const { accessToken, user } = useAuth();
  const { requireAuth } = useAuthGate();
  const mapRef = useRef<KakaoMapViewRef>(null);
  const [course, setCourse] = useState<Course | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);
  const [likedByMe, setLikedByMe] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [bookmarkedByMe, setBookmarkedByMe] = useState(false);
  const [comments, setComments] = useState<CourseComment[]>([]);
  const [isCommentsLoading, setIsCommentsLoading] = useState(false);
  const [commentBody, setCommentBody] = useState('');
  const [commentImage, setCommentImage] = useState<CourseCommentImageInput | null>(null);
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);
  const [replyTarget, setReplyTarget] = useState<CourseComment | null>(null);
  const [repliesByParentId, setRepliesByParentId] = useState<Record<string, CourseComment[]>>({});
  const [expandedReplyIds, setExpandedReplyIds] = useState<Record<string, boolean>>({});
  const [loadingReplyIds, setLoadingReplyIds] = useState<Record<string, boolean>>({});

  const userPace = user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM;

  useFocusEffect(
    useCallback(() => {
      let isActive = true;
      getCourse(courseId, accessToken ?? undefined)
        .then((result) => {
          if (isActive) {
            setCourse(result);
            setLikedByMe(result.likedByMe);
            setLikeCount(result.likeCount);
            setBookmarkedByMe(result.bookmarkedByMe ?? false);
          }
        })
        .catch((e: unknown) => {
          Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          navigation.goBack();
        })
        .finally(() => {
          if (isActive) setIsLoading(false);
        });
      return () => {
        isActive = false;
      };
    }, [courseId, accessToken, navigation])
  );

  const loadComments = useCallback(() => {
    setIsCommentsLoading(true);
    getCourseComments(courseId, accessToken ?? undefined)
      .then((result) => setComments(result.comments))
      .catch((e: unknown) => {
        Alert.alert('댓글 불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      })
      .finally(() => setIsCommentsLoading(false));
  }, [courseId, accessToken]);

  useFocusEffect(
    useCallback(() => {
      if (course?.visibility !== 'PUBLIC') return;
      loadComments();
    }, [course?.visibility, loadComments])
  );

  const handleMapReady = () => {
    if (!course) return;
    mapRef.current?.addRouteSegment(course.path);
    course.waypoints.forEach((wp, i) => mapRef.current?.addWaypoint(wp, i + 1));
    mapRef.current?.fitBounds(course.bounds);
  };

  const handleLike = async () => {
    if (!requireAuth()) return;
    if (!accessToken) return;

    const wasLiked = likedByMe;
    setLikedByMe(!wasLiked);
    setLikeCount((prev) => (wasLiked ? prev - 1 : prev + 1));

    try {
      const result = wasLiked
        ? await deleteLike('courses', courseId, accessToken)
        : await putLike('courses', courseId, accessToken);
      setLikedByMe(result.liked);
      setLikeCount(result.likeCount);
    } catch (e: unknown) {
      setLikedByMe(wasLiked);
      setLikeCount((prev) => (wasLiked ? prev + 1 : prev - 1));
      Alert.alert('오류', e instanceof Error ? e.message : '좋아요 처리 중 오류가 발생했습니다.');
    }
  };

  const handleBookmark = async () => {
    if (!requireAuth()) return;
    if (!accessToken) return;

    const wasBookmarked = bookmarkedByMe;
    setBookmarkedByMe(!wasBookmarked);

    try {
      if (wasBookmarked) {
        await deleteBookmark(courseId, accessToken);
      } else {
        await postBookmark(courseId, accessToken);
      }
    } catch (e: unknown) {
      setBookmarkedByMe(wasBookmarked);
      Alert.alert('오류', e instanceof Error ? e.message : '북마크 처리 중 오류가 발생했습니다.');
    }
  };

  const handleExport = async () => {
    if (!course) return;
    setIsExporting(true);
    try {
      await exportGpx(course.path, course.title);
    } catch (e: unknown) {
      Alert.alert('내보내기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsExporting(false);
    }
  };

  const handlePressWriteReview = () => {
    if (!requireAuth() || !course) return;
    navigation.navigate('PostCreate', {
      attachedCourseId: course.id,
      attachedCourseTitle: course.title,
    });
  };

  const handlePressReviewBoard = () => {
    if (!course) return;
    navigation.navigate('CourseBoard', {
      courseId: course.id,
      courseTitle: course.title,
    });
  };

  const handlePickCommentImage = async () => {
    if (!requireAuth()) return;

    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('권한 필요', '사진 라이브러리 접근 권한이 필요합니다.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.8,
    });
    if (result.canceled || result.assets.length === 0) return;

    const asset = result.assets[0];
    const name = asset.fileName ?? `comment-${Date.now()}.jpg`;
    const type = asset.mimeType ?? 'image/jpeg';
    setCommentImage({ uri: asset.uri, name, type });
  };

  const handleSubmitComment = async () => {
    if (!requireAuth()) return;
    if (!accessToken) return;

    const trimmedBody = commentBody.trim();
    if (!trimmedBody) {
      Alert.alert('알림', '댓글 내용을 입력해주세요.');
      return;
    }

    setIsSubmittingComment(true);
    try {
      const parentCommentId = replyTarget?.id;
      const created = await createCourseComment(
        courseId,
        trimmedBody,
        commentImage,
        accessToken,
        parentCommentId
      );
      if (parentCommentId) {
        setRepliesByParentId((prev) => ({
          ...prev,
          [parentCommentId]: [...(prev[parentCommentId] ?? []), created],
        }));
        setComments((prev) =>
          prev.map((comment) =>
            comment.id === parentCommentId
              ? { ...comment, replyCount: comment.replyCount + 1 }
              : comment
          )
        );
        setExpandedReplyIds((prev) => ({ ...prev, [parentCommentId]: true }));
        setReplyTarget(null);
      } else {
        setComments((prev) => [created, ...prev]);
      }
      setCommentBody('');
      setCommentImage(null);
      Keyboard.dismiss();
    } catch (e: unknown) {
      Alert.alert('댓글 작성 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmittingComment(false);
    }
  };

  const handleReply = (comment: CourseComment) => {
    if (!requireAuth()) return;
    setReplyTarget(comment);
  };

  const handleCancelReply = () => {
    setReplyTarget(null);
  };

  const handleToggleReplies = (commentId: string) => {
    setExpandedReplyIds((prev) => {
      const isExpanded = prev[commentId] ?? false;
      if (isExpanded) {
        return { ...prev, [commentId]: false };
      }
      if (!repliesByParentId[commentId]) {
        setLoadingReplyIds((loadingPrev) => ({ ...loadingPrev, [commentId]: true }));
        getCourseCommentReplies(courseId, commentId, accessToken ?? undefined)
          .then((replies) => {
            setRepliesByParentId((repliesPrev) => ({ ...repliesPrev, [commentId]: replies }));
          })
          .catch((e: unknown) => {
            Alert.alert('답글 불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          })
          .finally(() => {
            setLoadingReplyIds((loadingPrev) => ({ ...loadingPrev, [commentId]: false }));
          });
      }
      return { ...prev, [commentId]: true };
    });
  };

  const handleDeleteComment = (commentId: string, parentCommentId: string | null) => {
    if (!accessToken) return;

    Alert.alert('댓글 삭제', '이 댓글을 삭제하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          const prevComments = comments;
          const prevReplies = repliesByParentId;
          if (parentCommentId) {
            setRepliesByParentId((prev) => ({
              ...prev,
              [parentCommentId]: (prev[parentCommentId] ?? []).filter((reply) => reply.id !== commentId),
            }));
            setComments((prev) =>
              prev.map((comment) =>
                comment.id === parentCommentId
                  ? { ...comment, replyCount: Math.max(0, comment.replyCount - 1) }
                  : comment
              )
            );
          } else {
            setComments((prev) => prev.filter((comment) => comment.id !== commentId));
          }
          try {
            await deleteCourseComment(courseId, commentId, accessToken);
          } catch (e: unknown) {
            setComments(prevComments);
            setRepliesByParentId(prevReplies);
            Alert.alert('삭제 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          }
        },
      },
    ]);
  };

  if (isLoading || !course) {
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
          {course.title}
        </Text>
        {user?.id === course.authorId ? (
          <TouchableOpacity
            onPress={() => navigation.navigate('CourseEdit', { courseId })}
            activeOpacity={0.7}
          >
            <Ionicons name="pencil-outline" size={22} color={Colors.gray900} />
          </TouchableOpacity>
        ) : (
          <View style={styles.headerSpacer} />
        )}
      </View>

      <View style={styles.metaBar}>
        <View
          style={[
            styles.visibilityBadge,
            course.visibility === 'PUBLIC' ? styles.visibilityBadgePublic : styles.visibilityBadgePrivate,
          ]}
        >
          <Ionicons
            name={course.visibility === 'PUBLIC' ? 'earth-outline' : 'lock-closed-outline'}
            size={12}
            color={course.visibility === 'PUBLIC' ? Colors.primary : Colors.gray500}
          />
          <Text
            style={[
              styles.visibilityBadgeText,
              course.visibility === 'PUBLIC' ? styles.visibilityBadgeTextPublic : styles.visibilityBadgeTextPrivate,
            ]}
          >
            {course.visibility === 'PUBLIC' ? '공개' : '비공개'}
          </Text>
        </View>
        {course.startAddress && (
          <View style={styles.addressBar}>
            <Ionicons name="location-outline" size={13} color={Colors.gray500} />
            <Text style={styles.addressText} numberOfLines={1}>{course.startAddress}</Text>
          </View>
        )}
      </View>

      <KeyboardAvoidingView
        style={styles.keyboardAvoider}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View style={styles.mapContainer}>
          <KakaoMapView ref={mapRef} onMapPress={() => {}} onMapReady={handleMapReady} />

          <View style={styles.floatingButtons}>
            <FAB icon="create-outline" onPress={handlePressWriteReview} />
            <FAB icon="list-outline" onPress={handlePressReviewBoard} />
          </View>
        </View>

        <ScrollView style={styles.bottomScroll} keyboardShouldPersistTaps="handled">
          <RouteStatsBar
            stats={{
              distanceMeters: course.distanceMeters,
              estimatedDurationSeconds: Math.round((course.distanceMeters / 1000) * userPace),
              pointCount: course.waypoints.length,
            }}
            onExport={handleExport}
            isExporting={isExporting}
          />
          <View style={styles.likeBar}>
            <TouchableOpacity onPress={handleLike} activeOpacity={0.7} style={styles.likeButton}>
              <Ionicons
                name={likedByMe ? 'heart' : 'heart-outline'}
                size={22}
                color={likedByMe ? Colors.danger : Colors.gray500}
              />
              <Text style={[styles.likeCount, likedByMe && styles.likeCountActive]}>
                {likeCount}
              </Text>
            </TouchableOpacity>
            {user?.id !== course.authorId && (
              <TouchableOpacity onPress={handleBookmark} activeOpacity={0.7} style={styles.bookmarkButton}>
                <Ionicons
                  name={bookmarkedByMe ? 'bookmark' : 'bookmark-outline'}
                  size={22}
                  color={bookmarkedByMe ? Colors.primary : Colors.gray500}
                />
              </TouchableOpacity>
            )}
          </View>
          <TagList tags={course.tags} style={styles.tagList} />

          {course.visibility === 'PUBLIC' && (
            <View style={styles.commentSection}>
              <Text style={styles.commentSectionTitle}>댓글</Text>

              <View style={styles.commentForm}>
                {replyTarget && (
                  <View style={styles.replyTargetBar}>
                    <Text style={styles.replyTargetText}>
                      {replyTarget.author.nickname}님에게 답글 작성 중
                    </Text>
                    <TouchableOpacity onPress={handleCancelReply} activeOpacity={0.7}>
                      <Ionicons name="close" size={16} color={Colors.gray500} />
                    </TouchableOpacity>
                  </View>
                )}
                <TextInput
                  style={styles.commentInput}
                  placeholder={replyTarget ? '답글을 입력하세요' : '댓글을 입력하세요'}
                  placeholderTextColor={Colors.gray400}
                  value={commentBody}
                  onChangeText={setCommentBody}
                  multiline
                />
                {commentImage && (
                  <View style={styles.commentImagePreviewWrapper}>
                    <Image source={{ uri: commentImage.uri }} style={styles.commentImagePreview} />
                    <TouchableOpacity
                      onPress={() => setCommentImage(null)}
                      style={styles.commentImageRemoveButton}
                      activeOpacity={0.7}
                    >
                      <Ionicons name="close-circle" size={20} color={Colors.gray500} />
                    </TouchableOpacity>
                  </View>
                )}
                <View style={styles.commentFormActions}>
                  <TouchableOpacity onPress={handlePickCommentImage} activeOpacity={0.7}>
                    <Ionicons name="camera-outline" size={22} color={Colors.gray500} />
                  </TouchableOpacity>
                  <TouchableOpacity
                    onPress={handleSubmitComment}
                    activeOpacity={0.7}
                    style={styles.commentSubmitButton}
                    disabled={isSubmittingComment}
                  >
                    {isSubmittingComment ? (
                      <ActivityIndicator size="small" color={Colors.white} />
                    ) : (
                      <Text style={styles.commentSubmitText}>등록</Text>
                    )}
                  </TouchableOpacity>
                </View>
              </View>

              {isCommentsLoading ? (
                <ActivityIndicator size="small" color={Colors.primary} style={styles.commentLoading} />
              ) : comments.length === 0 ? (
                <Text style={styles.commentEmptyText}>아직 댓글이 없습니다.</Text>
              ) : (
                comments.map((comment) => (
                  <CourseCommentItem
                    key={comment.id}
                    comment={comment}
                    currentUserId={user?.id}
                    onDelete={handleDeleteComment}
                    onReply={handleReply}
                    replies={repliesByParentId[comment.id]}
                    isRepliesExpanded={expandedReplyIds[comment.id] ?? false}
                    isRepliesLoading={loadingReplyIds[comment.id] ?? false}
                    onToggleReplies={handleToggleReplies}
                  />
                ))
              )}
            </View>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

interface FABProps {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
}

function FAB({ icon, onPress }: FABProps) {
  return (
    <TouchableOpacity style={styles.fab} onPress={onPress} activeOpacity={0.8}>
      <Ionicons name={icon} size={20} color={Colors.primary} />
    </TouchableOpacity>
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
  keyboardAvoider: {
    flex: 1,
  },
  metaBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  visibilityBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 999,
  },
  visibilityBadgePublic: {
    backgroundColor: '#E8F2FF',
  },
  visibilityBadgePrivate: {
    backgroundColor: Colors.gray100,
  },
  visibilityBadgeText: {
    fontSize: 12,
    fontWeight: '600',
  },
  visibilityBadgeTextPublic: {
    color: Colors.primary,
  },
  visibilityBadgeTextPrivate: {
    color: Colors.gray500,
  },
  addressBar: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  addressText: {
    flex: 1,
    fontSize: 13,
    color: Colors.gray500,
  },
  mapContainer: {
    height: 260,
  },
  floatingButtons: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    gap: 10,
  },
  fab: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
  bottomScroll: {
    flex: 1,
  },
  likeBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  likeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  bookmarkButton: {
    marginLeft: 16,
    padding: 4,
  },
  likeCount: {
    fontSize: 14,
    color: Colors.gray500,
    fontWeight: '500',
  },
  likeCountActive: {
    color: Colors.danger,
  },
  tagList: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  commentSection: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 24,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  commentSectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 10,
  },
  commentForm: {
    marginBottom: 12,
  },
  replyTargetBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.gray100,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    marginBottom: 6,
  },
  replyTargetText: {
    fontSize: 12,
    color: Colors.gray500,
  },
  commentInput: {
    minHeight: 44,
    maxHeight: 100,
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: Colors.gray900,
  },
  commentImagePreviewWrapper: {
    marginTop: 8,
    alignSelf: 'flex-start',
    position: 'relative',
  },
  commentImagePreview: {
    width: 80,
    height: 80,
    borderRadius: 8,
    backgroundColor: Colors.gray100,
  },
  commentImageRemoveButton: {
    position: 'absolute',
    top: -6,
    right: -6,
    backgroundColor: Colors.white,
    borderRadius: 10,
  },
  commentFormActions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  commentSubmitButton: {
    backgroundColor: Colors.primary,
    borderRadius: 6,
    paddingHorizontal: 16,
    paddingVertical: 8,
    minWidth: 56,
    alignItems: 'center',
  },
  commentSubmitText: {
    color: Colors.white,
    fontSize: 13,
    fontWeight: '700',
  },
  commentLoading: {
    marginVertical: 16,
  },
  commentEmptyText: {
    fontSize: 13,
    color: Colors.gray500,
    textAlign: 'center',
    marginVertical: 16,
  },
});
