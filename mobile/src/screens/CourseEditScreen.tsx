import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  Alert,
  ActivityIndicator,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { getCourse, patchCourse } from '../services/courseApi';
import { evictCourse } from '../services/courseCache';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { Course, CourseVisibility } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'CourseEdit'>;

const TITLE_MAX = 60;
const DESCRIPTION_MAX = 500;
const TAG_MAX_COUNT = 10;
const TAG_MAX_LENGTH = 20;

export default function CourseEditScreen({ route, navigation }: Props) {
  const { courseId } = route.params;
  const { accessToken } = useAuth();

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] = useState<CourseVisibility>('PRIVATE');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');

  useFocusEffect(
    useCallback(() => {
      let isActive = true;
      getCourse(courseId, accessToken ?? undefined)
        .then((course: Course) => {
          if (!isActive) return;
          setTitle(course.title);
          setDescription(course.description ?? '');
          setVisibility(course.visibility);
          setTags(course.tags);
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

  const handleAddTag = () => {
    const trimmed = tagInput.trim();
    if (!trimmed) return;
    if (trimmed.length > TAG_MAX_LENGTH) {
      Alert.alert('태그 오류', `태그는 ${TAG_MAX_LENGTH}자 이하여야 합니다.`);
      return;
    }
    if (tags.length >= TAG_MAX_COUNT) {
      Alert.alert('태그 오류', `태그는 최대 ${TAG_MAX_COUNT}개까지 추가할 수 있습니다.`);
      return;
    }
    if (tags.includes(trimmed)) {
      setTagInput('');
      return;
    }
    setTags((prev) => [...prev, trimmed]);
    setTagInput('');
  };

  const handleRemoveTag = (tag: string) => {
    setTags((prev) => prev.filter((t) => t !== tag));
  };

  const handleSave = async () => {
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      Alert.alert('저장 실패', '코스 제목을 입력해주세요.');
      return;
    }
    if (!accessToken) return;

    setIsSaving(true);
    try {
      await patchCourse(
        courseId,
        {
          title: trimmedTitle,
          description: description.trim() || null,
          visibility,
          tags,
        },
        accessToken
      );
      evictCourse(courseId);
      navigation.goBack();
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
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
        <Text style={styles.headerTitle}>코스 수정</Text>
        <TouchableOpacity onPress={handleSave} activeOpacity={0.7} disabled={isSaving}>
          {isSaving ? (
            <ActivityIndicator size="small" color={Colors.primary} />
          ) : (
            <Text style={styles.saveText}>저장</Text>
          )}
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView style={styles.form} contentContainerStyle={styles.formContent}>
          <Text style={styles.label}>
            제목 <Text style={styles.required}>*</Text>
          </Text>
          <TextInput
            style={styles.input}
            value={title}
            onChangeText={(t) => t.length <= TITLE_MAX && setTitle(t)}
            placeholder="코스 제목"
            placeholderTextColor={Colors.gray400}
            returnKeyType="done"
          />
          <Text style={styles.charCount}>{title.length}/{TITLE_MAX}</Text>

          <Text style={styles.label}>설명</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            value={description}
            onChangeText={(t) => t.length <= DESCRIPTION_MAX && setDescription(t)}
            placeholder="코스에 대한 설명 (선택)"
            placeholderTextColor={Colors.gray400}
            multiline
            numberOfLines={4}
            textAlignVertical="top"
          />
          <Text style={styles.charCount}>{description.length}/{DESCRIPTION_MAX}</Text>

          <Text style={styles.label}>공개 설정</Text>
          <View style={styles.visibilityRow}>
            <TouchableOpacity
              style={[styles.visibilityOption, visibility === 'PUBLIC' && styles.visibilityActive]}
              onPress={() => setVisibility('PUBLIC')}
              activeOpacity={0.7}
            >
              <Ionicons
                name="globe-outline"
                size={16}
                color={visibility === 'PUBLIC' ? Colors.primary : Colors.gray500}
              />
              <Text style={[styles.visibilityText, visibility === 'PUBLIC' && styles.visibilityActiveText]}>
                공개
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.visibilityOption, visibility === 'PRIVATE' && styles.visibilityActive]}
              onPress={() => setVisibility('PRIVATE')}
              activeOpacity={0.7}
            >
              <Ionicons
                name="lock-closed-outline"
                size={16}
                color={visibility === 'PRIVATE' ? Colors.primary : Colors.gray500}
              />
              <Text style={[styles.visibilityText, visibility === 'PRIVATE' && styles.visibilityActiveText]}>
                비공개
              </Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.label}>태그 ({tags.length}/{TAG_MAX_COUNT})</Text>
          <View style={styles.tagInputRow}>
            <TextInput
              style={[styles.input, styles.tagInput]}
              value={tagInput}
              onChangeText={setTagInput}
              placeholder="태그 입력 후 추가"
              placeholderTextColor={Colors.gray400}
              returnKeyType="done"
              onSubmitEditing={handleAddTag}
              maxLength={TAG_MAX_LENGTH}
            />
            <TouchableOpacity onPress={handleAddTag} activeOpacity={0.7} style={styles.tagAddButton}>
              <Text style={styles.tagAddText}>추가</Text>
            </TouchableOpacity>
          </View>
          {tags.length > 0 && (
            <View style={styles.tagList}>
              {tags.map((tag) => (
                <TouchableOpacity
                  key={tag}
                  style={styles.tag}
                  onPress={() => handleRemoveTag(tag)}
                  activeOpacity={0.7}
                >
                  <Text style={styles.tagText}>{tag}</Text>
                  <Ionicons name="close" size={12} color={Colors.primary} />
                </TouchableOpacity>
              ))}
            </View>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
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
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  saveText: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.primary,
  },
  form: {
    flex: 1,
  },
  formContent: {
    padding: 20,
    paddingBottom: 40,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.gray900,
    marginBottom: 6,
    marginTop: 20,
  },
  required: {
    color: Colors.danger,
  },
  input: {
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: Colors.gray900,
  },
  textArea: {
    height: 100,
    paddingTop: 10,
  },
  charCount: {
    fontSize: 11,
    color: Colors.gray400,
    textAlign: 'right',
    marginTop: 4,
  },
  visibilityRow: {
    flexDirection: 'row',
    gap: 12,
  },
  visibilityOption: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 8,
  },
  visibilityActive: {
    borderColor: Colors.primary,
  },
  visibilityText: {
    fontSize: 14,
    color: Colors.gray500,
  },
  visibilityActiveText: {
    color: Colors.primary,
    fontWeight: '600',
  },
  tagInputRow: {
    flexDirection: 'row',
    gap: 8,
  },
  tagInput: {
    flex: 1,
  },
  tagAddButton: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: Colors.primary,
    borderRadius: 8,
    justifyContent: 'center',
  },
  tagAddText: {
    fontSize: 14,
    fontWeight: '600',
    color: Colors.white,
  },
  tagList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 8,
  },
  tag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: Colors.gray100,
    borderRadius: 20,
  },
  tagText: {
    fontSize: 13,
    color: Colors.primary,
    fontWeight: '500',
  },
});
