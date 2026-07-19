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
import { WithdrawalReason } from '../types';
import { Colors } from '../constants/theme';

const REASON_OPTIONS: { value: WithdrawalReason; label: string }[] = [
  { value: 'NOT_USING', label: '자주 사용하지 않아요' },
  { value: 'MISSING_FEATURES', label: '원하는 코스·기능이 없어요' },
  { value: 'BUGS_OR_ERRORS', label: '오류·버그가 많아요' },
  { value: 'PRIVACY_CONCERN', label: '개인정보가 걱정돼요' },
  { value: 'OTHER', label: '기타' },
];

interface WithdrawalReasonModalProps {
  visible: boolean;
  onConfirm: (reason: WithdrawalReason, reasonDetail: string | null) => void;
  onClose: () => void;
  isSubmitting: boolean;
}

export default function WithdrawalReasonModal({
  visible,
  onConfirm,
  onClose,
  isSubmitting,
}: WithdrawalReasonModalProps) {
  const [selectedReason, setSelectedReason] = useState<WithdrawalReason | null>(null);
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
          <Text style={styles.title}>회원 탈퇴</Text>
          <Text style={styles.subtitle}>탈퇴 사유를 선택해주세요.</Text>

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

          <Text style={styles.notice}>
            탈퇴 후 30일 동안은 같은 카카오 계정으로 로그인하면 계정이 복구돼요. 30일이 지나면
            작성한 코스/게시글은 남지만 작성자는 &apos;탈퇴한 사용자&apos;로 표시돼요.
          </Text>

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
                <Text style={styles.confirmButtonText}>탈퇴하기</Text>
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
  notice: {
    fontSize: 12,
    color: Colors.gray400,
    marginTop: 12,
    marginBottom: 16,
    lineHeight: 18,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 10,
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
