// MOCK 구현 — postApi.ts와 동일한 이유로 실제 fetch 없이 in-memory로 동작한다.
// 백엔드 Comment API가 구현되면 courseApi.ts처럼 실제 fetch 호출로 교체할 것.
import { Comment, PublicProfile, CreateCommentRequestBody } from '../types';
import { incrementCommentCount } from './postApi';

let commentsByPostId: Record<string, Comment[]> = {};

export async function getComments(postId: string): Promise<Comment[]> {
  return commentsByPostId[postId] ?? [];
}

export async function createComment(
  postId: string,
  body: CreateCommentRequestBody,
  _accessToken: string,
  author: PublicProfile
): Promise<Comment> {
  const now = new Date().toISOString();
  const comment: Comment = {
    id: `comment_${Date.now()}`,
    postId,
    author,
    body: body.body,
    createdAt: now,
    updatedAt: now,
  };
  commentsByPostId = {
    ...commentsByPostId,
    [postId]: [...(commentsByPostId[postId] ?? []), comment],
  };
  incrementCommentCount(postId);
  return comment;
}
