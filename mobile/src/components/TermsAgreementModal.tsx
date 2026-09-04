import React from 'react';
import { Modal, View, Text, TouchableOpacity, StyleSheet, Linking } from 'react-native';
import { Colors } from '../constants/theme';

const TERMS_URL = 'https://github.com/lovelyAlien/runvas/blob/main/docs/terms-of-service.md';

interface Props {
  visible: boolean;
  onAgree: () => void;
  onCancel: () => void;
}

export default function TermsAgreementModal({ visible, onAgree, onCancel }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>이용약관 동의</Text>
          <Text style={styles.body}>
            RunSketch는 부적절한 콘텐츠와 이용자에 대해 무관용 원칙을 적용합니다. 서비스를
            이용하려면 이용약관에 동의해야 합니다.
          </Text>
          <TouchableOpacity onPress={() => Linking.openURL(TERMS_URL)}>
            <Text style={styles.link}>이용약관 전문 보기</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.agreeButton} onPress={onAgree} activeOpacity={0.8}>
            <Text style={styles.agreeButtonLabel}>동의하고 계속하기</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={onCancel} activeOpacity={0.7}>
            <Text style={styles.cancelLabel}>취소</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 14,
    padding: 20,
  },
  title: { fontSize: 16, fontWeight: '700', color: Colors.gray900, marginBottom: 8 },
  body: { fontSize: 13, color: Colors.gray500, marginBottom: 12, lineHeight: 18 },
  link: { fontSize: 13, color: Colors.gray900, fontWeight: '600', marginBottom: 16 },
  agreeButton: {
    backgroundColor: Colors.gray900,
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 8,
  },
  agreeButtonLabel: { fontSize: 13, fontWeight: '700', color: Colors.white },
  cancelLabel: { textAlign: 'center', color: Colors.gray500, marginTop: 4, fontWeight: '600' },
});
