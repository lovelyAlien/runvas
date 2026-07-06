// 댓글 API (GET/POST /api/posts/{postId}/comments) — runvas/backend의 CommentController와 연동됨.
import { Comment, CreateCommentRequestBody } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function getComments(postId: string): Promise<Comment[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts/${postId}/comments`);

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { comments } = (await response.json()) as { comments: Comment[] };
  return comments;
}

export async function createComment(
  postId: string,
  body: CreateCommentRequestBody,
  accessToken: string
): Promise<Comment> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts/${postId}/comments`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { comment } = (await response.json()) as { comment: Comment };
  return comment;
}
