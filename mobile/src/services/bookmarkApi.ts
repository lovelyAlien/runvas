import { BookmarkedCourseSummary } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export interface BookmarkResponse {
  bookmark: {
    courseId: string;
    createdAt: string;
  };
}

export interface BookmarkedCoursesResult {
  courses: BookmarkedCourseSummary[];
  nextCursor: string | null;
}

export async function postBookmark(courseId: string, accessToken: string): Promise<BookmarkResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/bookmarks`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as BookmarkResponse;
}

export async function deleteBookmark(courseId: string, accessToken: string): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}/bookmarks`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }
}

export async function getBookmarkedCourses(
  accessToken: string,
  params?: { limit?: number; cursor?: string }
): Promise<BookmarkedCoursesResult> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const query = new URLSearchParams();
  if (params?.limit) query.set('limit', String(params.limit));
  if (params?.cursor) query.set('cursor', params.cursor);

  const response = await fetch(`${API_BASE_URL}/api/me/bookmarked-courses?${query}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const data = (await response.json()) as {
    courses: BookmarkedCourseSummary[];
    pageInfo: { nextCursor: string | null };
  };
  return { courses: data.courses, nextCursor: data.pageInfo.nextCursor };
}
