import React, { useEffect } from 'react';
import { View } from 'react-native';
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
import CourseEditScreen from './src/screens/CourseEditScreen';
import LoginPromptModal from './src/components/LoginPromptModal';
import KakaoLoginWebView from './src/components/KakaoLoginWebView';
import { AuthProvider, useAuth } from './src/contexts/AuthContext';
import { RootTabParamList, RootStackParamList } from './src/navigation/types';
import { Colors } from './src/constants/theme';

const Tab = createBottomTabNavigator<RootTabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();
const navigationRef = createNavigationContainerRef<RootStackParamList>();

// 로그인 성공 후 "게시판 1회 자동 이동" 부수효과를 처리하는 단일 지점.
// consumeNewUserRedirect()를 여기 한 곳에서만 호출해 중복 소비를 막는다.
function NewUserRedirectWatcher() {
  const { user, consumeNewUserRedirect } = useAuth();

  useEffect(() => {
    if (user && consumeNewUserRedirect() && navigationRef.isReady()) {
      navigationRef.navigate('Tabs', { screen: 'Board' });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  return null;
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
    return <View style={{ flex: 1, backgroundColor: '#fff' }} />;
  }

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" translucent={false} backgroundColor={Colors.white} />
      <NavigationContainer ref={navigationRef}>
        <Stack.Navigator screenOptions={{ headerShown: false }}>
          <Stack.Screen name="Tabs" component={RootTabs} />
          <Stack.Screen name="CourseDetail" component={CourseDetailScreen} />
          <Stack.Screen name="CourseEdit" component={CourseEditScreen} />
        </Stack.Navigator>
        <NewUserRedirectWatcher />
      </NavigationContainer>
      <LoginPromptModal />
      <KakaoLoginWebView />
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
