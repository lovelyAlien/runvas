import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  FlatList,
  Text,
  ActivityIndicator,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { CourseSummary } from '../types';
import { Colors } from '../constants/theme';
import { formatDistance } from '../utils/format';
import TagList from './TagList';

interface Props {
  onClose: () => void;
  onSelectCourse: (courseId: string) => void;
  onSearch: (q: string, signal: AbortSignal) => Promise<CourseSummary[]>;
  onSearchByTag: (tag: string, signal: AbortSignal) => Promise<CourseSummary[]>;
}

export default function CourseSearchBar({ onClose, onSelectCourse, onSearch, onSearchByTag }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CourseSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<TextInput>(null);

  const isTagMode = query.startsWith('#');

  useEffect(() => {
    inputRef.current?.focus();
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
      if (abortRef.current) abortRef.current.abort();
    };
  }, []);

  const handleQueryChange = useCallback(
    (text: string) => {
      setQuery(text);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      if (abortRef.current) abortRef.current.abort();
      if (!text.trim() || text === '#') {
        setResults([]);
        setHasSearched(false);
        return;
      }
      debounceRef.current = setTimeout(async () => {
        const controller = new AbortController();
        abortRef.current = controller;
        setIsLoading(true);
        try {
          const tagSearch = text.startsWith('#');
          const term = tagSearch ? text.slice(1).trim() : text.trim();
          if (!term) {
            setResults([]);
            setHasSearched(false);
            return;
          }
          const courses = tagSearch
            ? await onSearchByTag(term, controller.signal)
            : await onSearch(term, controller.signal);
          setResults(courses);
          setHasSearched(true);
        } catch (e) {
          if (e instanceof Error && e.name === 'AbortError') return;
          setResults([]);
          setHasSearched(true);
        } finally {
          setIsLoading(false);
        }
      }, 300);
    },
    [onSearch, onSearchByTag]
  );

  const renderItem = useCallback(
    ({ item }: { item: CourseSummary }) => (
      <TouchableOpacity
        style={styles.resultItem}
        onPress={() => onSelectCourse(item.id)}
        activeOpacity={0.7}
      >
        <View style={styles.resultIcon}>
          <Ionicons name="walk-outline" size={18} color={Colors.primary} />
        </View>
        <View style={styles.resultText}>
          <Text style={styles.resultTitle} numberOfLines={1}>
            {item.title}
          </Text>
          <Text style={styles.resultMeta}>
            {formatDistance(item.distanceMeters)}
            {item.startAddress ? `  ·  ${item.startAddress}` : ''}
          </Text>
          <TagList tags={item.tags} style={styles.resultTags} />
        </View>
        <Ionicons name="chevron-forward" size={16} color={Colors.gray300} />
      </TouchableOpacity>
    ),
    [onSelectCourse]
  );

  const emptyMessage = isTagMode
    ? `"${query}" 태그의 코스가 없습니다`
    : `"${query}"에 해당하는 코스가 없습니다`;

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.container}
    >
      <View style={styles.searchRow}>
        <View style={styles.inputWrapper}>
          <Ionicons
            name={isTagMode ? 'pricetag-outline' : 'search'}
            size={18}
            color={isTagMode ? Colors.primary : Colors.gray400}
            style={styles.searchIcon}
          />
          <TextInput
            ref={inputRef}
            style={styles.input}
            value={query}
            onChangeText={handleQueryChange}
            placeholder="코스 이름 또는 #태그로 검색"
            placeholderTextColor={Colors.gray400}
            returnKeyType="search"
            clearButtonMode="while-editing"
            maxLength={isTagMode ? 21 : 100}
            autoCapitalize="none"
          />
          {isLoading && (
            <ActivityIndicator size="small" color={Colors.primary} style={styles.spinner} />
          )}
        </View>
        <TouchableOpacity onPress={onClose} style={styles.cancelButton} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Text style={styles.cancelLabel}>취소</Text>
        </TouchableOpacity>
      </View>

      {results.length > 0 && (
        <FlatList
          data={results}
          keyExtractor={(item) => item.id}
          renderItem={renderItem}
          style={styles.list}
          keyboardShouldPersistTaps="handled"
        />
      )}

      {hasSearched && results.length === 0 && !isLoading && (
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>{emptyMessage}</Text>
        </View>
      )}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    backgroundColor: Colors.white,
    zIndex: 100,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 8,
  },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 8,
  },
  inputWrapper: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.gray50,
    borderRadius: 10,
    paddingHorizontal: 10,
    height: 40,
  },
  searchIcon: { marginRight: 6 },
  input: {
    flex: 1,
    fontSize: 15,
    color: Colors.gray900,
    paddingVertical: 0,
  },
  spinner: { marginLeft: 6 },
  cancelButton: { paddingHorizontal: 4 },
  cancelLabel: { fontSize: 15, color: Colors.primary, fontWeight: '500' },
  list: {
    maxHeight: 340,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  resultItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
    gap: 12,
  },
  resultIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: Colors.gray50,
    justifyContent: 'center',
    alignItems: 'center',
  },
  resultText: { flex: 1 },
  resultTitle: { fontSize: 14, fontWeight: '600', color: Colors.gray900, marginBottom: 2 },
  resultMeta: { fontSize: 12, color: Colors.gray400 },
  resultTags: { marginTop: 4 },
  emptyState: { padding: 24, alignItems: 'center' },
  emptyText: { fontSize: 14, color: Colors.gray400 },
});
