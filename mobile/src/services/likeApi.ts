// MOCK 구현 — postApi.ts와 동일한 이유로 실제 fetch 없이 in-memory로 동작한다.
// docs/api-contract.md §Like APIs는 targetType(`courses`/`posts`)을 받지만, 이번 범위는
// 게시글 좋아요만 다루므로 targetType 파라미터 없이 posts 전용으로 구현한다.
// 백엔드 Like API(컨트롤러)가 구현되면 courseApi.ts처럼 실제 fetch 호출로 교체할 것.
import { getPost, updateLikeState } from './postApi';

interface LikeResult {
  liked: boolean;
  likeCount: number;
}

export async function putLike(postId: string, _accessToken: string): Promise<LikeResult> {
  const post = await getPost(postId);
  if (post.likedByMe) return { liked: true, likeCount: post.likeCount };
  const likeCount = post.likeCount + 1;
  updateLikeState(postId, true, likeCount);
  return { liked: true, likeCount };
}

export async function deleteLike(postId: string, _accessToken: string): Promise<LikeResult> {
  const post = await getPost(postId);
  if (!post.likedByMe) return { liked: false, likeCount: post.likeCount };
  const likeCount = Math.max(0, post.likeCount - 1);
  updateLikeState(postId, false, likeCount);
  return { liked: false, likeCount };
}
