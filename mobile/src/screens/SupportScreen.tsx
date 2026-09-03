import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, Linking, StyleSheet } from 'react-native';
import { Colors } from '../constants/theme';

const SUPPORT_EMAILS = ['tkfdkskarl78@gmail.com', 'jaeseung425@gmail.com'];

export default function SupportScreen() {
  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>고객 지원</Text>
      <Text style={styles.body}>
        RunSketch 이용 중 문의사항이나 부적절한 콘텐츠·이용자 신고 관련 문의는 아래 이메일로
        연락해주세요. 신고 접수 후 24시간 이내에 검토합니다.
      </Text>
      {SUPPORT_EMAILS.map((email) => (
        <TouchableOpacity key={email} onPress={() => Linking.openURL(`mailto:${email}`)}>
          <Text style={styles.email}>{email}</Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.white },
  content: { padding: 20 },
  title: { fontSize: 18, fontWeight: '700', color: Colors.gray900, marginBottom: 12 },
  body: { fontSize: 13, color: Colors.gray500, lineHeight: 18, marginBottom: 16 },
  email: { fontSize: 14, fontWeight: '600', color: Colors.gray900, marginBottom: 8 },
});
