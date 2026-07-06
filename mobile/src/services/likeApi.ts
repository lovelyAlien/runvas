// 좋아요 API (PUT/DELETE /api/likes/posts/{postId}) — runvas/backend의 LikeController와 연동됨.
// docs/api-contract.md §Like APIs는 targetType(courses/posts)을 받지만, 모바일에서는 게시글
// 좋아요만 다루므로 targetType은 'posts'로 고정한다 (코스 좋아요 UI는 범위 밖).
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

interface LikeResult {
  liked: boolean;
  likeCount: number;
}

export async function putLike(postId: string, accessToken: string): Promise<LikeResult> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/likes/posts/${postId}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { liked, likeCount } = (await response.json()) as LikeResult;
  return { liked, likeCount };
}

export async function deleteLike(postId: string, accessToken: string): Promise<LikeResult> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/likes/posts/${postId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { liked, likeCount } = (await response.json()) as LikeResult;
  return { liked, likeCount };
}
