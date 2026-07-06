// 게시글 API (GET/POST /api/posts 등) — runvas/backend의 PostController와 연동됨.
import { Post, CreatePostRequestBody } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

interface GetPostsParams {
  attachedCourseId?: string;
  sort?: 'createdAtDesc' | 'popularDesc';
}

export async function getPosts(params: GetPostsParams = {}, accessToken?: string): Promise<Post[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const query = new URLSearchParams();
  if (params.attachedCourseId) query.set('attachedCourseId', params.attachedCourseId);
  if (params.sort) query.set('sort', params.sort);

  const response = await fetch(`${API_BASE_URL}/api/posts?${query.toString()}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { posts } = (await response.json()) as { posts: Post[] };
  return posts;
}

export async function getPost(postId: string, accessToken?: string): Promise<Post> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts/${postId}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { post } = (await response.json()) as { post: Post };
  return post;
}

export async function createPost(body: CreatePostRequestBody, accessToken: string): Promise<Post> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/posts`, {
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

  const { post } = (await response.json()) as { post: Post };
  return post;
}
