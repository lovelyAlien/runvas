import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { createPost } from '../services/postApi';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { formatDateYYYYMMDD } from '../utils/format';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PostCreate'>;

function buildDefaultTitle(courseTitle?: string): string {
  if (!courseTitle) return '';
  return `[후기] ${courseTitle} - ${formatDateYYYYMMDD(new Date())}`;
}

export default function PostCreateScreen({ route, navigation }: Props) {
  const { attachedCourseId, attachedCourseTitle } = route.params;
  const { accessToken, user } = useAuth();
  const [title, setTitle] = useState(buildDefaultTitle(attachedCourseTitle));
  const [body, setBody] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!accessToken || !user) return;
    if (!title.trim() || !body.trim()) {
      Alert.alert('입력 필요', '제목과 본문을 모두 입력해주세요.');
      return;
    }
    setIsSubmitting(true);
    try {
      const post = await createPost(
        {
          title: title.trim(),
          body: body.trim(),
          attachedCourseId: attachedCourseId ?? null,
        },
        accessToken
      );
      navigation.replace('PostDetail', { postId: post.id });
    } catch (e: unknown) {
      Alert.alert('작성 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>게시글 작성</Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isSubmitting} activeOpacity={0.7}>
          {isSubmitting ? (
            <ActivityIndicator size="small" color={Colors.primary} />
          ) : (
            <Text style={styles.submitLabel}>완료</Text>
          )}
        </TouchableOpacity>
      </View>

      {attachedCourseTitle && (
        <View style={styles.courseChip}>
          <Ionicons name="map-outline" size={14} color={Colors.primary} />
          <Text style={styles.courseChipLabel} numberOfLines={1}>
            {attachedCourseTitle}
          </Text>
        </View>
      )}

      <TextInput
        style={styles.titleInput}
        placeholder="제목을 입력하세요"
        placeholderTextColor={Colors.gray400}
        value={title}
        onChangeText={setTitle}
      />
      <TextInput
        style={styles.bodyInput}
        placeholder="러닝 경험을 자유롭게 남겨보세요"
        placeholderTextColor={Colors.gray400}
        value={body}
        onChangeText={setBody}
        multiline
        textAlignVertical="top"
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
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
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  submitLabel: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.primary,
  },
  courseChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginHorizontal: 16,
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
  titleInput: {
    marginHorizontal: 16,
    marginTop: 16,
    fontSize: 16,
    fontWeight: '600',
    color: Colors.gray900,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
    paddingBottom: 10,
  },
  bodyInput: {
    flex: 1,
    marginHorizontal: 16,
    marginTop: 16,
    fontSize: 14,
    color: Colors.gray900,
  },
});
