import AsyncStorage from '@react-native-async-storage/async-storage';
import { LocalSavedRoute } from '../types';

// 백엔드 Course API 연동 전까지 기기 로컬에 저장한 경로 목록을 보관하는 키.
// 연동 후에는 이 스토리지를 GET /api/courses 응답으로 대체한다.
const STORAGE_KEY = 'runvas:localSavedRoutes';

export async function getLocalRoutes(): Promise<LocalSavedRoute[]> {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  if (!raw) return [];
  return JSON.parse(raw) as LocalSavedRoute[];
}

export async function saveLocalRoute(
  route: Omit<LocalSavedRoute, 'id' | 'createdAt'>
): Promise<LocalSavedRoute> {
  const newRoute: LocalSavedRoute = {
    ...route,
    id: `local_${Date.now()}`,
    createdAt: new Date().toISOString(),
  };
  const existing = await getLocalRoutes();
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify([newRoute, ...existing]));
  return newRoute;
}

export async function deleteLocalRoute(id: string): Promise<void> {
  const existing = await getLocalRoutes();
  const next = existing.filter((route) => route.id !== id);
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}
