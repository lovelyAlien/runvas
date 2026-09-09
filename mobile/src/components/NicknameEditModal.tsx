import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { Colors } from '../constants/theme';

const MIN_NICKNAME_LENGTH = 2;
const MAX_NICKNAME_LENGTH = 30;

type Props = {
  visible: boolean;
  initialNickname: string;
  cancelLabel?: string;
  onConfirm: (nickname: string) => void;
  onClose: () => void;
  isSaving: boolean;
};

export default function NicknameEditModal({
  visible,
  initialNickname,
  cancelLabel = '취소',
  onConfirm,
  onClose,
  isSaving,
}: Props) {
  const [inputText, setInputText] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      setInputText(initialNickname);
      setValidationError(null);
    }
  }, [visible, initialNickname]);

  function handleConfirm() {
    const trimmed = inputText.trim();
    if (trimmed.length < MIN_NICKNAME_LENGTH || trimmed.length > MAX_NICKNAME_LENGTH) {
      setValidationError(`${MIN_NICKNAME_LENGTH}~${MAX_NICKNAME_LENGTH}자로 입력해 주세요.`);
      return;
    }
    onConfirm(trimmed);
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>닉네임 설정</Text>
          <Text style={styles.subtitle}>다른 사용자에게 공개되는 이름입니다</Text>

          <TextInput
            style={[styles.input, validationError ? styles.inputError : null]}
            value={inputText}
            onChangeText={(text) => {
              setInputText(text);
              setValidationError(null);
            }}
            placeholder="닉네임을 입력해 주세요"
            placeholderTextColor={Colors.gray400}
            maxLength={MAX_NICKNAME_LENGTH}
            returnKeyType="done"
            onSubmitEditing={handleConfirm}
            editable={!isSaving}
          />
          {validationError && <Text style={styles.errorText}>{validationError}</Text>}

          <View style={styles.actions}>
            <TouchableOpacity style={styles.cancelButton} onPress={onClose} disabled={isSaving}>
              <Text style={styles.cancelLabel}>{cancelLabel}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.confirmButton, isSaving && styles.confirmButtonDisabled]}
              onPress={handleConfirm}
              disabled={isSaving}
              activeOpacity={0.8}
            >
              {isSaving ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.confirmLabel}>저장</Text>
              )}
            </TouchableOpacity>
          </View>
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
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 12,
    color: Colors.gray400,
    marginBottom: 16,
  },
  input: {
    borderWidth: 1,
    borderColor: Colors.gray100,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: Colors.gray900,
    fontWeight: '600',
  },
  inputError: {
    borderColor: Colors.danger,
  },
  errorText: {
    fontSize: 12,
    color: Colors.danger,
    marginTop: 4,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 16,
  },
  cancelButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  cancelLabel: {
    color: Colors.gray500,
    fontWeight: '600',
    fontSize: 14,
  },
  confirmButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    minWidth: 60,
    alignItems: 'center',
  },
  confirmButtonDisabled: {
    backgroundColor: Colors.gray300,
  },
  confirmLabel: {
    color: Colors.white,
    fontWeight: '700',
    fontSize: 14,
  },
});
