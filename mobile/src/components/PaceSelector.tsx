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
import { PacePreset } from '../types';
import { formatPace } from '../utils/format';

interface PacePresetOption {
  label: PacePreset;
  paceSecPerKm: number;
  description: string;
}

const PACE_PRESETS: PacePresetOption[] = [
  { label: '초보', paceSecPerKm: 480, description: '8:00/km' },
  { label: '중수', paceSecPerKm: 360, description: '6:00/km' },
  { label: '고수', paceSecPerKm: 270, description: '4:30/km' },
];

const MIN_PACE_SEC = 120; // 2:00/km
const MAX_PACE_SEC = 900; // 15:00/km

type Props = {
  visible: boolean;
  currentPace: number;
  onConfirm: (paceSecPerKm: number) => void;
  onClose: () => void;
  isSaving: boolean;
};

export default function PaceSelector({ visible, currentPace, onConfirm, onClose, isSaving }: Props) {
  const [inputText, setInputText] = useState('');
  const [parseError, setParseError] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      setInputText(paceToMinSec(currentPace));
      setParseError(null);
    }
  }, [visible, currentPace]);

  function paceToMinSec(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${String(s).padStart(2, '0')}`;
  }

  function parsePaceInput(text: string): number | null {
    const trimmed = text.trim();
    const parts = trimmed.split(':');
    if (parts.length !== 2) return null;
    const minutes = parseInt(parts[0], 10);
    const seconds = parseInt(parts[1], 10);
    if (isNaN(minutes) || isNaN(seconds)) return null;
    if (seconds < 0 || seconds > 59) return null;
    const total = minutes * 60 + seconds;
    if (total < MIN_PACE_SEC || total > MAX_PACE_SEC) return null;
    return total;
  }

  function handlePreset(paceSecPerKm: number) {
    setInputText(paceToMinSec(paceSecPerKm));
    setParseError(null);
  }

  function handleConfirm() {
    const parsed = parsePaceInput(inputText);
    if (parsed === null) {
      setParseError(`2:00 ~ 15:00 사이의 페이스를 입력해 주세요 (예: 6:00)`);
      return;
    }
    onConfirm(parsed);
  }

  const activePreset = PACE_PRESETS.find((p) => paceToMinSec(p.paceSecPerKm) === inputText);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>달리기 페이스 설정</Text>
          <Text style={styles.subtitle}>저장 후 경로의 예상 시간에 반영됩니다</Text>

          <View style={styles.presets}>
            {PACE_PRESETS.map((preset) => {
              const isActive = activePreset?.label === preset.label;
              return (
                <TouchableOpacity
                  key={preset.label}
                  style={[styles.presetButton, isActive && styles.presetButtonActive]}
                  onPress={() => handlePreset(preset.paceSecPerKm)}
                  activeOpacity={0.7}
                >
                  <Text style={[styles.presetLabel, isActive && styles.presetLabelActive]}>
                    {preset.label}
                  </Text>
                  <Text style={[styles.presetDesc, isActive && styles.presetDescActive]}>
                    {preset.description}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          <Text style={styles.inputLabel}>직접 입력 (분:초)</Text>
          <TextInput
            style={[styles.input, parseError ? styles.inputError : null]}
            value={inputText}
            onChangeText={(text) => {
              setInputText(text);
              setParseError(null);
            }}
            placeholder="예: 6:00"
            placeholderTextColor={Colors.gray400}
            keyboardType="numbers-and-punctuation"
            returnKeyType="done"
            onSubmitEditing={handleConfirm}
          />
          {parseError && <Text style={styles.errorText}>{parseError}</Text>}

          <View style={styles.actions}>
            <TouchableOpacity style={styles.cancelButton} onPress={onClose} disabled={isSaving}>
              <Text style={styles.cancelLabel}>취소</Text>
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
  presets: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  presetButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: Colors.gray100,
    backgroundColor: Colors.gray50,
  },
  presetButtonActive: {
    borderColor: Colors.primary,
    backgroundColor: Colors.white,
  },
  presetLabel: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.gray500,
    marginBottom: 2,
  },
  presetLabelActive: {
    color: Colors.primary,
  },
  presetDesc: {
    fontSize: 11,
    color: Colors.gray400,
  },
  presetDescActive: {
    color: Colors.primary,
  },
  inputLabel: {
    fontSize: 12,
    color: Colors.gray500,
    marginBottom: 6,
    fontWeight: '600',
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
