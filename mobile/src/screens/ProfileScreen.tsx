import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { patchMe } from '../services/authApi';
import PaceSelector from '../components/PaceSelector';
import WithdrawalReasonModal from '../components/WithdrawalReasonModal';
import { WithdrawalReason } from '../types';
import { DEFAULT_PACE_SEC_PER_KM } from '../hooks/useRoute';
import { formatPace } from '../utils/format';
import { Colors } from '../constants/theme';

export default function ProfileScreen() {
  const { user, logout, withdraw, updateUser, accessToken } = useAuth();
  const { requireAuth } = useAuthGate();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isPaceSelectorOpen, setIsPaceSelectorOpen] = useState(false);
  const [isSavingPace, setIsSavingPace] = useState(false);
  const [isWithdrawalModalOpen, setIsWithdrawalModalOpen] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);

  useEffect(() => {
    requireAuth();
  }, [requireAuth]);

  const handleLogout = useCallback(() => {
    Alert.alert('로그아웃', '로그아웃 하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '로그아웃',
        style: 'destructive',
        onPress: async () => {
          setIsLoggingOut(true);
          try {
            await logout();
          } catch (e: unknown) {
            Alert.alert('오류', e instanceof Error ? e.message : '로그아웃에 실패했습니다.');
          } finally {
            setIsLoggingOut(false);
          }
        },
      },
    ]);
  }, [logout]);

  const handleWithdraw = useCallback(
    async (reason: WithdrawalReason, reasonDetail: string | null) => {
      setIsWithdrawing(true);
      try {
        await withdraw(reason, reasonDetail);
        setIsWithdrawalModalOpen(false);
      } catch (e: unknown) {
        Alert.alert('오류', e instanceof Error ? e.message : '탈퇴에 실패했습니다.');
      } finally {
        setIsWithdrawing(false);
      }
    },
    [withdraw],
  );

  const handlePaceConfirm = async (paceSecPerKm: number) => {
    if (!accessToken) return;
    setIsSavingPace(true);
    try {
      const result = await patchMe({ runningPaceSecPerKm: paceSecPerKm }, accessToken);
      await updateUser(result.user);
      setIsPaceSelectorOpen(false);
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSavingPace(false);
    }
  };

  const currentPace = user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM;

  return (
    <SafeAreaView style={styles.container} edges={['top', 'left', 'right']}>
      <View style={styles.content}>
        {user ? (
          <>
            {user.profileImageUrl ? (
              <Image source={{ uri: user.profileImageUrl }} style={styles.avatar} />
            ) : (
              <Ionicons
                name="person-circle-outline"
                size={80}
                color={Colors.gray300}
                style={styles.avatarPlaceholder}
              />
            )}
            <Text style={styles.nickname}>{user.nickname}</Text>

            <TouchableOpacity
              style={styles.paceRow}
              activeOpacity={0.7}
              onPress={() => setIsPaceSelectorOpen(true)}
            >
              <View style={styles.paceInfo}>
                <Text style={styles.paceLabel}>달리기 페이스</Text>
                <Text style={styles.paceValue}>{formatPace(currentPace)}</Text>
              </View>
              <Text style={styles.paceChevron}>›</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.logoutButton, isLoggingOut && styles.logoutButtonDisabled]}
              activeOpacity={0.8}
              disabled={isLoggingOut}
              onPress={handleLogout}
            >
              {isLoggingOut ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.logoutButtonText}>로그아웃</Text>
              )}
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.withdrawButton}
              activeOpacity={0.6}
              onPress={() => setIsWithdrawalModalOpen(true)}
            >
              <Text style={styles.withdrawButtonText}>회원 탈퇴</Text>
            </TouchableOpacity>
          </>
        ) : (
          <Text style={styles.emptyText}>로그인이 필요합니다.</Text>
        )}
      </View>

      <PaceSelector
        visible={isPaceSelectorOpen}
        currentPace={currentPace}
        onConfirm={handlePaceConfirm}
        onClose={() => setIsPaceSelectorOpen(false)}
        isSaving={isSavingPace}
      />
      <WithdrawalReasonModal
        visible={isWithdrawalModalOpen}
        onConfirm={handleWithdraw}
        onClose={() => setIsWithdrawalModalOpen(false)}
        isSubmitting={isWithdrawing}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatar: {
    width: 80,
    height: 80,
    borderRadius: 40,
    marginBottom: 12,
  },
  avatarPlaceholder: {
    marginBottom: 12,
  },
  nickname: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 24,
  },
  paceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '80%',
    paddingVertical: 14,
    paddingHorizontal: 16,
    backgroundColor: Colors.gray50,
    borderRadius: 12,
    marginBottom: 12,
  },
  paceInfo: {
    gap: 2,
  },
  paceLabel: {
    fontSize: 12,
    color: Colors.gray400,
    fontWeight: '500',
  },
  paceValue: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.primary,
  },
  paceChevron: {
    fontSize: 22,
    color: Colors.gray300,
    fontWeight: '300',
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  logoutButton: {
    marginTop: 16,
    backgroundColor: Colors.danger,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 10,
  },
  logoutButtonDisabled: {
    backgroundColor: Colors.gray100,
  },
  logoutButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
  withdrawButton: {
    marginTop: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  withdrawButtonText: {
    color: Colors.gray400,
    fontSize: 12,
    fontWeight: '500',
  },
});
