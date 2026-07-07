import { CourseComment } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export interface CourseCommentsResult {
  comments: CourseComment[];
  nextCursor: string | null;
}

export interface CourseCommentImageInput {
  uri: string;
  name: string;
  type: string;
}

export interface UpdateCourseCommentInput {
  body?: string;
  image?: CourseCommentImageInput;
  removeImage?: boolean;
}

function ensureApiBaseUrl(): void {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }
}

export async function getCourseComments(
  courseId: string,
  accessToken?: string,
  params?: { limit?: number; cursor?: string }
): Promise<CourseCommentsResult> {
  ensureApiBaseUrl();

  const query = new URLSearchParams();
  if (params?.limit) query.set('limit', String(params.limit));
  if (params?.cursor) query.set('cursor', params.cursor);

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/comments?${query}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const data = (await response.json()) as {
    comments: CourseComment[];
    pageInfo: { nextCursor: string | null };
  };
  return { comments: data.comments, nextCursor: data.pageInfo.nextCursor };
}

export async function createCourseComment(
  courseId: string,
  body: string,
  image: CourseCommentImageInput | null,
  accessToken: string,
  parentCommentId?: string
): Promise<CourseComment> {
  ensureApiBaseUrl();

  const formData = new FormData();
  formData.append('body', body);
  if (image) {
    formData.append('image', image as unknown as Blob);
  }
  if (parentCommentId) {
    formData.append('parentCommentId', parentCommentId);
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/comments`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: formData,
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const data = (await response.json()) as { comment: CourseComment };
  return data.comment;
}

export async function getCourseCommentReplies(
  courseId: string,
  commentId: string,
  accessToken?: string
): Promise<CourseComment[]> {
  ensureApiBaseUrl();

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/comments/${commentId}/replies`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const data = (await response.json()) as { replies: CourseComment[] };
  return data.replies;
}

export async function updateCourseComment(
  courseId: string,
  commentId: string,
  input: UpdateCourseCommentInput,
  accessToken: string
): Promise<CourseComment> {
  ensureApiBaseUrl();

  const formData = new FormData();
  if (input.body !== undefined) formData.append('body', input.body);
  if (input.image) formData.append('image', input.image as unknown as Blob);
  if (input.removeImage !== undefined) formData.append('removeImage', String(input.removeImage));

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/comments/${commentId}`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: formData,
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const data = (await response.json()) as { comment: CourseComment };
  return data.comment;
}

export async function deleteCourseComment(
  courseId: string,
  commentId: string,
  accessToken: string
): Promise<void> {
  ensureApiBaseUrl();

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/comments/${commentId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
