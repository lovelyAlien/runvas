import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer, createNavigationContainerRef } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';

import MapScreen from './src/screens/MapScreen';
import BoardScreen from './src/screens/BoardScreen';
import SavedRoutesScreen from './src/screens/SavedRoutesScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import CourseDetailScreen from './src/screens/CourseDetailScreen';
import PostCreateScreen from './src/screens/PostCreateScreen';
import PostDetailScreen from './src/screens/PostDetailScreen';
import CourseBoardScreen from './src/screens/CourseBoardScreen';
import CourseEditScreen from './src/screens/CourseEditScreen';
import BlockedUsersScreen from './src/screens/BlockedUsersScreen';
import SupportScreen from './src/screens/SupportScreen';
import LoginPromptModal from './src/components/LoginPromptModal';
import NicknameEditModal from './src/components/NicknameEditModal';
import KakaoLoginWebView from './src/components/KakaoLoginWebView';
import Toast from 'react-native-toast-message';
import { AuthProvider, useAuth } from './src/contexts/AuthContext';
import { patchMe } from './src/services/authApi';
import { RootTabParamList, RootStackParamList } from './src/navigation/types';
import { Colors } from './src/constants/theme';

const Tab = createBottomTabNavigator<RootTabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();
const navigationRef = createNavigationContainerRef<RootStackParamList>();

// 로그인 성공 후 "닉네임 확인 → 게시판 1회 자동 이동" 부수효과를 처리하는 단일 지점.
// consumeNewUserRedirect()를 여기 한 곳에서만 호출해 중복 소비를 막는다.
function NewUserRedirectWatcher() {
  const { user, consumeNewUserRedirect, accessToken, updateUser } = useAuth();
  const [isNicknamePromptVisible, setIsNicknamePromptVisible] = useState(false);
  const [isSavingNickname, setIsSavingNickname] = useState(false);

  useEffect(() => {
    if (user && consumeNewUserRedirect()) {
      // LoginPromptModal이 닫히는 애니메이션과 겹치지 않도록 지연 후 표시한다.
      // 같은 렌더에서 두 Modal이 동시에 열고 닫히면 iOS에서 두 번째 Modal이
      // 조용히 나타나지 않는 경합이 생길 수 있다 (RN 기본 fade 애니메이션 기준 350ms 여유).
      const timer = setTimeout(() => setIsNicknamePromptVisible(true), 350);
      return () => clearTimeout(timer);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  function goToBoard() {
    setIsNicknamePromptVisible(false);
    if (navigationRef.isReady()) {
      navigationRef.navigate('Tabs', { screen: 'Board' });
    }
  }

  async function handleNicknameConfirm(nickname: string) {
    if (!accessToken) {
      goToBoard();
      return;
    }
    setIsSavingNickname(true);
    try {
      const result = await patchMe({ nickname }, accessToken);
      await updateUser(result.user);
      goToBoard();
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSavingNickname(false);
    }
  }

  return (
    <NicknameEditModal
      visible={isNicknamePromptVisible}
      initialNickname={user?.nickname ?? ''}
      cancelLabel="건너뛰기"
      onConfirm={handleNicknameConfirm}
      onClose={goToBoard}
      isSaving={isSavingNickname}
    />
  );
}

function gatedTabPressListener(requireAuth: () => boolean) {
  return () => ({
    tabPress: (e: { preventDefault: () => void }) => {
      if (!requireAuth()) {
        e.preventDefault();
      }
    },
  });
}

function RootTabs() {
  const { requireAuth } = useAuth();

  return (
    <Tab.Navigator screenOptions={{ headerShown: false }}>
      <Tab.Screen
        name="Map"
        component={MapScreen}
        options={{
          tabBarLabel: '지도',
          tabBarIcon: ({ color, size }) => <Ionicons name="map-outline" size={size} color={color} />,
        }}
      />
      <Tab.Screen
        name="Board"
        component={BoardScreen}
        options={{
          tabBarLabel: '게시판',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="chatbubbles-outline" size={size} color={color} />
          ),
        }}
      />
      <Tab.Screen
        name="SavedRoutes"
        component={SavedRoutesScreen}
        listeners={gatedTabPressListener(requireAuth)}
        options={{
          tabBarLabel: '내 코스',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="bookmark-outline" size={size} color={color} />
          ),
        }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileScreen}
        listeners={gatedTabPressListener(requireAuth)}
        options={{
          tabBarLabel: '프로필',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="person-outline" size={size} color={color} />
          ),
        }}
      />
    </Tab.Navigator>
  );
}

// AuthProvider 내부에서 isInitializing을 소비해 초기화 완료 전 NavigationContainer 렌더를 막는다.
function AppContent() {
  const { isInitializing } = useAuth();

  if (isInitializing) {
    return (
      <View style={{ flex: 1, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" translucent={false} backgroundColor={Colors.white} />
      <NavigationContainer ref={navigationRef}>
        <Stack.Navigator screenOptions={{ headerShown: false }}>
          <Stack.Screen name="Tabs" component={RootTabs} />
          <Stack.Screen name="CourseDetail" component={CourseDetailScreen} />
          <Stack.Screen name="PostCreate" component={PostCreateScreen} />
          <Stack.Screen name="PostDetail" component={PostDetailScreen} />
          <Stack.Screen name="CourseBoard" component={CourseBoardScreen} />
          <Stack.Screen name="CourseEdit" component={CourseEditScreen} />
          <Stack.Screen name="BlockedUsers" component={BlockedUsersScreen} />
          <Stack.Screen name="Support" component={SupportScreen} />
        </Stack.Navigator>
        <NewUserRedirectWatcher />
      </NavigationContainer>
      <LoginPromptModal />
      <KakaoLoginWebView />
      <Toast />
    </SafeAreaProvider>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
