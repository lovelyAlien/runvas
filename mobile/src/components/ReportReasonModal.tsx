import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { ReportReason } from '../types';
import { Colors } from '../constants/theme';

const REASON_OPTIONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: '스팸/광고예요' },
  { value: 'ABUSIVE', label: '욕설·혐오 표현이에요' },
  { value: 'INAPPROPRIATE', label: '부적절한 콘텐츠예요' },
  { value: 'OTHER', label: '기타' },
];

interface ReportReasonModalProps {
  visible: boolean;
  onConfirm: (reason: ReportReason, reasonDetail: string | null) => void;
  onClose: () => void;
  isSubmitting: boolean;
}

export default function ReportReasonModal({
  visible,
  onConfirm,
  onClose,
  isSubmitting,
}: ReportReasonModalProps) {
  const [selectedReason, setSelectedReason] = useState<ReportReason | null>(null);
  const [reasonDetail, setReasonDetail] = useState('');

  const isOtherSelected = selectedReason === 'OTHER';
  const isDetailValid = !isOtherSelected || reasonDetail.trim().length > 0;
  const canSubmit = selectedReason !== null && isDetailValid && !isSubmitting;

  const handleConfirm = () => {
    if (!selectedReason || !isDetailValid) return;
    onConfirm(selectedReason, isOtherSelected ? reasonDetail.trim() : null);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>신고하기</Text>
          <Text style={styles.subtitle}>신고 사유를 선택해주세요.</Text>

          {REASON_OPTIONS.map((option) => (
            <Pressable
              key={option.value}
              style={styles.optionRow}
              onPress={() => setSelectedReason(option.value)}
            >
              <View
                style={[
                  styles.radio,
                  selectedReason === option.value && styles.radioSelected,
                ]}
              />
              <Text style={styles.optionLabel}>{option.label}</Text>
            </Pressable>
          ))}

          {isOtherSelected && (
            <TextInput
              style={styles.input}
              placeholder="사유를 입력해주세요"
              placeholderTextColor={Colors.gray400}
              value={reasonDetail}
              onChangeText={setReasonDetail}
              maxLength={200}
              multiline
            />
          )}

          <View style={styles.buttonRow}>
            <Pressable style={styles.cancelButton} onPress={onClose} disabled={isSubmitting}>
              <Text style={styles.cancelButtonText}>취소</Text>
            </Pressable>
            <Pressable
              style={[styles.confirmButton, !canSubmit && styles.confirmButtonDisabled]}
              onPress={handleConfirm}
              disabled={!canSubmit}
            >
              {isSubmitting ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.confirmButtonText}>신고하기</Text>
              )}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 20,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: Colors.gray400,
    marginBottom: 16,
  },
  optionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
  },
  radio: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: Colors.gray300,
    marginRight: 10,
  },
  radioSelected: {
    borderColor: Colors.primary,
    backgroundColor: Colors.primary,
  },
  optionLabel: {
    fontSize: 14,
    color: Colors.gray900,
  },
  input: {
    borderWidth: 1,
    borderColor: Colors.gray300,
    borderRadius: 10,
    padding: 10,
    minHeight: 60,
    marginTop: 4,
    marginBottom: 8,
    fontSize: 14,
    color: Colors.gray900,
    textAlignVertical: 'top',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 12,
  },
  cancelButton: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: Colors.gray100,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: Colors.gray900,
    fontSize: 14,
    fontWeight: '600',
  },
  confirmButton: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: Colors.danger,
    alignItems: 'center',
  },
  confirmButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  confirmButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
});
