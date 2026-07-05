// MOCK 구현 — 백엔드 Post API가 아직 없다 (backend/.../community 패키지에는 Like 엔티티만 존재).
// docs/api-contract.md §Post APIs 계약과 동일한 모양으로 응답하되, 실제 fetch 없이 in-memory
// 배열로 상태를 흉내낸다. 백엔드 Post API가 구현되면 courseApi.ts처럼 실제 fetch 호출로 교체할 것.
// 앱을 재시작하면 아래 시드 데이터로 초기화된다 (의도된 동작).
import { Post, PublicProfile, CreatePostRequestBody } from '../types';

const SEED_AUTHOR: PublicProfile = {
  id: 'user_seed_1',
  nickname: 'Seoul Runner',
  profileImageUrl: null,
  bio: 'Drawing routes around Seoul.',
};

let posts: Post[] = [
  {
    id: 'post_seed_1',
    author: SEED_AUTHOR,
    title: '한강 하트 코스 후기',
    body: '초반 구간이 평탄해서 가볍게 뛰기 좋았습니다.',
    attachedCourseId: null,
    tags: [],
    likeCount: 3,
    likedByMe: false,
    commentCount: 0,
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    id: 'post_seed_2',
    author: SEED_AUTHOR,
    title: '남산 둘레길 야간 러닝',
    body: '야간에는 조명이 부족한 구간이 있어 헤드랜턴을 추천합니다.',
    attachedCourseId: null,
    tags: [],
    likeCount: 1,
    likedByMe: false,
    commentCount: 0,
    createdAt: new Date(Date.now() - 3600000).toISOString(),
    updatedAt: new Date(Date.now() - 3600000).toISOString(),
  },
];

interface GetPostsParams {
  attachedCourseId?: string;
  sort?: 'createdAtDesc' | 'popularDesc';
}

export async function getPosts(params: GetPostsParams = {}): Promise<Post[]> {
  const filtered = params.attachedCourseId
    ? posts.filter((p) => p.attachedCourseId === params.attachedCourseId)
    : [...posts];

  if (params.sort === 'popularDesc') {
    return filtered.sort((a, b) => b.likeCount - a.likeCount);
  }
  return filtered.sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );
}

export async function getPost(postId: string): Promise<Post> {
  const post = posts.find((p) => p.id === postId);
  if (!post) throw new Error('게시글을 찾을 수 없습니다.');
  return post;
}

export async function createPost(
  body: CreatePostRequestBody,
  _accessToken: string,
  author: PublicProfile
): Promise<Post> {
  const now = new Date().toISOString();
  const post: Post = {
    id: `post_${Date.now()}`,
    author,
    title: body.title,
    body: body.body,
    attachedCourseId: body.attachedCourseId ?? null,
    tags: body.tags ?? [],
    likeCount: 0,
    likedByMe: false,
    commentCount: 0,
    createdAt: now,
    updatedAt: now,
  };
  posts = [post, ...posts];
  return post;
}

// commentApi.ts가 댓글 작성 시 게시글의 commentCount를 갱신하기 위해 호출한다.
export function incrementCommentCount(postId: string): void {
  posts = posts.map((p) => (p.id === postId ? { ...p, commentCount: p.commentCount + 1 } : p));
}

// likeApi.ts가 좋아요 토글 결과를 게시글에 반영하기 위해 호출한다.
export function updateLikeState(postId: string, liked: boolean, likeCount: number): void {
  posts = posts.map((p) => (p.id === postId ? { ...p, likedByMe: liked, likeCount } : p));
}
