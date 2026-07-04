// 좋아요 API (PUT/DELETE /api/likes/{targetType}/{targetId}) — runvas/backend의 LikeController와 연동됨.
// docs/api-contract.md §Like APIs 기준으로 targetType은 'courses' | 'posts'를 받는다.
import { LikeResponse } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function putLike(
  targetType: string,
  targetId: string,
  accessToken: string
): Promise<LikeResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/likes/${targetType}/${targetId}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as LikeResponse;
}

export async function deleteLike(
  targetType: string,
  targetId: string,
  accessToken: string
): Promise<LikeResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/likes/${targetType}/${targetId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as LikeResponse;
}
