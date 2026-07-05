# 커뮤니티 게시글 모바일 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Map 화면에서 코스를 선택하면 "게시글 작성"/"게시판" 원형 버튼이 활성화되고, 코스 첨부 후기 게시글 작성·코스별 게시판·전역 게시판·게시글 상세(좋아요/댓글)까지 이어지는 커뮤니티 흐름을 모바일에 구현한다. 백엔드 Post/Comment가 아직 없으므로 `docs/api-contract.md` 계약을 그대로 따르는 명시적 mock 서비스로 동작시킨다.

**Architecture:** `docs/api-contract.md`의 GET /posts에 `attachedCourseId` 필터를 먼저 추가한다(문서 우선). 모바일은 in-memory mock 서비스(`postApi.ts`/`commentApi.ts`/`likeApi.ts`)를 새로 만들어 실제 `fetch` 없이 계약과 동일한 모양으로 응답하고, 신규 화면 3개(`PostCreateScreen`/`PostDetailScreen`/`CourseBoardScreen`)와 기존 `BoardScreen` 교체, `MapScreen`의 우측 FAB 2개 추가로 연결한다.

**Tech Stack:** React Native (Expo SDK 54), TypeScript, `@react-navigation` (bottom-tabs + native-stack), 기존 `AuthContext`/`useAuthGate` 패턴.

## Global Constraints

- API 필드(쿼리 파라미터 포함)를 바꾸려면 구현보다 `docs/api-contract.md`를 먼저 커밋한다 (`CLAUDE.md` 문서 우선 원칙) — Task 1이 이 저장소의 첫 커밋이어야 한다.
- Post/Comment/Like는 **명시적 mock**이다. 각 mock 서비스 파일 최상단에 "백엔드 Post API 구현되면 courseApi.ts 패턴의 실제 fetch로 교체" 주석을 남긴다. 실제 `fetch`/`EXPO_PUBLIC_API_BASE_URL`을 사용하지 않는다.
- 이번 범위는 게시글/댓글 **작성·조회**와 게시글 **좋아요 토글**만 다룬다. 수정·삭제, 태그 입력 UI, 태그/검색어 필터 UI, 페이지네이션은 만들지 않는다 (`docs/superpowers/specs/2026-07-05-community-post-mobile-ui-design.md` §범위 밖).
- 좌표 관련 규칙(`Coordinate`/`RoutePoint` 구분 등)은 이번 작업과 무관하다 — 새로 다루는 데이터는 좌표가 아니다.
- 이 저장소에는 jest 등 테스트 러너가 없다 (`mobile/CLAUDE.md` §테스트). 각 태스크 검증은 `npx tsc --noEmit` + 코드 리뷰로 하고, 전체 동작 수동 확인은 마지막 태스크에서 한 번에 한다.
- 인증이 필요한 동작(게시글 작성, 댓글 작성, 좋아요)은 화면 진입/버튼 클릭 시점에 `requireAuth()`로 게이트한다. 조회(게시글 목록/상세/댓글 목록)는 게이트하지 않는다 (`GET /posts` 등은 `Auth: Optional`).
- 커밋 메시지는 Conventional Commits 형식, `git add`는 파일을 명시해서 add한다 (`git add -A` 금지).
- 현재 브랜치(`feat/community-post-board`)는 `feat/course-select-preview-without-camera-move`(PR #13, 미병합)에서 분기했다 — `core.hooksPath=.githooks`가 이미 적용되어 있으므로 별도 훅 설정은 불필요하다.

---

### Task 1: 문서 변경 — `GET /posts`에 `attachedCourseId` 필터 추가

**Files:**
- Modify: `docs/api-contract.md:940-948`

**Interfaces:**
- Consumes: 없음
- Produces: `GET /posts?attachedCourseId=` 쿼리 계약 — Task 3의 mock `getPosts()`가 이 계약을 그대로 구현

- [ ] **Step 1: Query Params 표에 `attachedCourseId` 행 추가**

`docs/api-contract.md:940-948`의 아래 블록을:

```markdown
#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `q` | string | N | 제목 또는 본문 검색어 |
| `tag` | string | N | 단일 태그 필터 |
| `sort` | string | N | `createdAtDesc`, `popularDesc`. 기본 `createdAtDesc` |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |
```

아래로 교체한다 (`attachedCourseId` 행과 필터 조합 설명 문장 추가):

```markdown
#### Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `attachedCourseId` | string | N | 첨부된 코스 ID로 필터링 |
| `q` | string | N | 제목 또는 본문 검색어 |
| `tag` | string | N | 단일 태그 필터 |
| `sort` | string | N | `createdAtDesc`, `popularDesc`. 기본 `createdAtDesc` |
| `limit` | number | N | 기본 20, 최대 50 |
| `cursor` | string | N | 다음 페이지 조회용 커서 |

검색과 필터 조건이 함께 전달되면 서버는 모든 조건을 만족하는 게시글만 반환합니다.
```

- [ ] **Step 2: Commit**

```bash
git add docs/api-contract.md
git commit -m "docs: GET /posts에 attachedCourseId 필터 쿼리 파라미터 추가"
```

---

### Task 2: 타입 추가 — `Post`/`Comment`/`PublicProfile` 및 날짜 포맷 유틸

**Files:**
- Modify: `mobile/src/types/index.ts`
- Modify: `mobile/src/utils/format.ts`

**Interfaces:**
- Consumes: 없음
- Produces: `PublicProfile`, `Post`, `Comment`, `CreatePostRequestBody`, `CreateCommentRequestBody` 타입 (`../types`) — Task 3~11 전체가 사용. `formatDateYYYYMMDD(date: Date): string` (`../utils/format`) — Task 8이 사용

- [ ] **Step 1: 타입 추가**

`mobile/src/types/index.ts` 파일 끝(`CourseSummary` 인터페이스 뒤)에 추가:

```ts

// docs/data-model.md PublicProfile과 1:1 대응. 공개 화면·커뮤니티 응답에서는 User 전체가 아니라
// 이 필드만 노출한다.
export interface PublicProfile {
  id: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
}

// docs/data-model.md Post와 1:1 대응.
export interface Post {
  id: string;
  author: PublicProfile;
  title: string;
  body: string;
  attachedCourseId: string | null;
  tags: string[];
  likeCount: number;
  likedByMe: boolean;
  commentCount: number;
  createdAt: string;
  updatedAt: string;
}

// docs/data-model.md Comment와 1:1 대응.
export interface Comment {
  id: string;
  postId: string;
  author: PublicProfile;
  body: string;
  createdAt: string;
  updatedAt: string;
}

// docs/api-contract.md POST /posts 요청 본문과 1:1 대응.
export interface CreatePostRequestBody {
  title: string;
  body: string;
  attachedCourseId?: string | null;
  tags?: string[];
}

// docs/api-contract.md POST /posts/{postId}/comments 요청 본문과 1:1 대응.
export interface CreateCommentRequestBody {
  body: string;
}
```

- [ ] **Step 2: 날짜 포맷 유틸 추가**

`mobile/src/utils/format.ts` 파일 끝에 추가:

```ts

// 2026-07-05 → "2026.07.05" — 게시글 작성 화면의 기본 제목([후기] 코스명 - 날짜)에 사용.
export function formatDateYYYYMMDD(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}.${m}.${d}`;
}
```

- [ ] **Step 3: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: Commit**

```bash
git add mobile/src/types/index.ts mobile/src/utils/format.ts
git commit -m "feat(mobile): 커뮤니티 게시글 타입과 날짜 포맷 유틸 추가"
```

---

### Task 3: `postApi.ts` mock 서비스 생성

**Files:**
- Create: `mobile/src/services/postApi.ts`

**Interfaces:**
- Consumes: `Post`, `PublicProfile`, `CreatePostRequestBody` 타입 (`../types`, Task 2)
- Produces: `getPosts(params)`, `getPost(postId)`, `createPost(body, accessToken, author)`,
  `incrementCommentCount(postId)`, `updateLikeState(postId, liked, likeCount)` — Task 4(`commentApi.ts`), Task 5(`likeApi.ts`), Task 9~11 화면들이 사용

- [ ] **Step 1: mock 서비스 파일 작성**

`mobile/src/services/postApi.ts` 생성:

```ts
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
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/services/postApi.ts
git commit -m "feat(mobile): 게시글 mock 서비스(postApi) 추가"
```

---

### Task 4: `commentApi.ts` mock 서비스 생성

**Files:**
- Create: `mobile/src/services/commentApi.ts`

**Interfaces:**
- Consumes: `Comment`, `PublicProfile`, `CreateCommentRequestBody` 타입 (`../types`, Task 2), `incrementCommentCount(postId)` (`./postApi`, Task 3)
- Produces: `getComments(postId)`, `createComment(postId, body, accessToken, author)` — Task 10(`PostDetailScreen`)이 사용

- [ ] **Step 1: mock 서비스 파일 작성**

`mobile/src/services/commentApi.ts` 생성:

```ts
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
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/services/commentApi.ts
git commit -m "feat(mobile): 댓글 mock 서비스(commentApi) 추가"
```

---

### Task 5: `likeApi.ts` mock 서비스 생성

**Files:**
- Create: `mobile/src/services/likeApi.ts`

**Interfaces:**
- Consumes: `getPost(postId)`, `updateLikeState(postId, liked, likeCount)` (`./postApi`, Task 3)
- Produces: `putLike(postId, accessToken)`, `deleteLike(postId, accessToken)` — Task 10(`PostDetailScreen`)이 사용

- [ ] **Step 1: mock 서비스 파일 작성**

`mobile/src/services/likeApi.ts` 생성:

```ts
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
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: Commit**

```bash
git add mobile/src/services/likeApi.ts
git commit -m "feat(mobile): 게시글 좋아요 mock 서비스(likeApi) 추가"
```

---

### Task 6: 네비게이션 타입 및 화면 등록

**Files:**
- Modify: `mobile/src/navigation/types.ts`
- Modify: `mobile/App.tsx`

**Interfaces:**
- Consumes: 없음 (아직 존재하지 않는 화면 컴포넌트를 이 태스크에서 먼저 참조하므로, Task 7~11에서
  실제 파일이 생기기 전까지는 `App.tsx`의 `import` 4줄이 빨간 줄(모듈 없음)로 남는다 — 이 태스크의
  타입 체크는 실패가 정상이며, Task 11 완료 후 다시 통과를 확인한다)
- Produces: `RootStackParamList`에 `PostCreate`/`PostDetail`/`CourseBoard` 추가 — Task 8~12 전체가 이 타입을 사용

- [ ] **Step 1: `RootStackParamList`에 화면 3개 추가**

`mobile/src/navigation/types.ts:10-13`의 아래 블록을:

```ts
export type RootStackParamList = {
  Tabs: NavigatorScreenParams<RootTabParamList> | undefined;
  CourseDetail: { courseId: string };
};
```

아래로 교체:

```ts
export type RootStackParamList = {
  Tabs: NavigatorScreenParams<RootTabParamList> | undefined;
  CourseDetail: { courseId: string };
  PostCreate: { attachedCourseId?: string; attachedCourseTitle?: string };
  PostDetail: { postId: string };
  CourseBoard: { courseId: string; courseTitle: string };
};
```

- [ ] **Step 2: `App.tsx`에 신규 화면 import 및 등록**

`mobile/App.tsx:14`(`import CourseDetailScreen from './src/screens/CourseDetailScreen';`) 바로
뒤에 추가:

```ts
import PostCreateScreen from './src/screens/PostCreateScreen';
import PostDetailScreen from './src/screens/PostDetailScreen';
import CourseBoardScreen from './src/screens/CourseBoardScreen';
```

`mobile/App.tsx:113`(`<Stack.Screen name="CourseDetail" component={CourseDetailScreen} />`) 바로
뒤에 추가:

```tsx
          <Stack.Screen name="PostCreate" component={PostCreateScreen} />
          <Stack.Screen name="PostDetail" component={PostDetailScreen} />
          <Stack.Screen name="CourseBoard" component={CourseBoardScreen} />
```

- [ ] **Step 3: Commit**

```bash
git add mobile/src/navigation/types.ts mobile/App.tsx
git commit -m "feat(mobile): 게시글 작성/상세/코스 게시판 화면을 네비게이션에 등록"
```

(이 태스크 직후 `npx tsc --noEmit`은 아직 존재하지 않는 화면 모듈 때문에 실패한다 — Task 7~11에서
실제 파일을 만들면서 각자 확인한다.)

---

### Task 7: `PostListItem` 공용 목록 행 컴포넌트

**Files:**
- Create: `mobile/src/components/PostListItem.tsx`

**Interfaces:**
- Consumes: `Post` 타입 (`../types`, Task 2), `Colors` (`../constants/theme`)
- Produces: `PostListItem` 기본 export, props `{ post: Post; onPress: (postId: string) => void }` — Task 9(`BoardScreen`), Task 11(`CourseBoardScreen`)이 사용

- [ ] **Step 1: 컴포넌트 파일 작성**

`mobile/src/components/PostListItem.tsx` 생성:

```tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Colors } from '../constants/theme';
import { Post } from '../types';

interface Props {
  post: Post;
  onPress: (postId: string) => void;
}

export default function PostListItem({ post, onPress }: Props) {
  return (
    <TouchableOpacity style={styles.row} activeOpacity={0.7} onPress={() => onPress(post.id)}>
      <View style={styles.rowHeader}>
        <Text style={styles.title} numberOfLines={1}>
          {post.title}
        </Text>
        {post.attachedCourseId && (
          <Ionicons name="map-outline" size={14} color={Colors.primary} style={styles.courseIcon} />
        )}
      </View>
      <Text style={styles.body} numberOfLines={2}>
        {post.body}
      </Text>
      <View style={styles.metaRow}>
        <Text style={styles.metaText}>{post.author.nickname}</Text>
        <Text style={styles.metaDot}>·</Text>
        <Text style={styles.metaText}>{new Date(post.createdAt).toLocaleDateString()}</Text>
        <Text style={styles.metaDot}>·</Text>
        <Ionicons name="heart-outline" size={12} color={Colors.gray400} />
        <Text style={styles.metaText}>{post.likeCount}</Text>
        <Ionicons name="chatbubble-outline" size={12} color={Colors.gray400} style={styles.commentIcon} />
        <Text style={styles.metaText}>{post.commentCount}</Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  rowHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  title: {
    flex: 1,
    fontSize: 15,
    fontWeight: '600',
    color: Colors.gray900,
  },
  courseIcon: {
    marginLeft: 6,
  },
  body: {
    fontSize: 13,
    color: Colors.gray500,
    marginTop: 4,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    gap: 4,
  },
  metaText: {
    fontSize: 12,
    color: Colors.gray400,
  },
  metaDot: {
    fontSize: 12,
    color: Colors.gray300,
  },
  commentIcon: {
    marginLeft: 6,
  },
});
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: `PostListItem.tsx` 관련 에러 없음 (Task 6에서 남긴 `App.tsx`의 미해결 import 에러는
이 시점에도 계속 나타나는 것이 정상)

- [ ] **Step 3: Commit**

```bash
git add mobile/src/components/PostListItem.tsx
git commit -m "feat(mobile): 게시글 목록 행 공용 컴포넌트(PostListItem) 추가"
```

---

### Task 8: `PostCreateScreen` 생성

**Files:**
- Create: `mobile/src/screens/PostCreateScreen.tsx`

**Interfaces:**
- Consumes: `createPost` (`../services/postApi`, Task 3), `useAuth` (`../contexts/AuthContext`),
  `formatDateYYYYMMDD` (`../utils/format`, Task 2), `RootStackParamList` (`../navigation/types`, Task 6)
- Produces: `PostCreateScreen` 기본 export — Task 6에서 이미 네비게이션에 등록됨, Task 9(`BoardScreen`)/
  Task 11(`CourseBoardScreen`)/Task 12(`MapScreen`)가 `navigation.navigate('PostCreate', ...)`로 진입

- [ ] **Step 1: 화면 파일 작성**

`mobile/src/screens/PostCreateScreen.tsx` 생성:

```tsx
import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { createPost } from '../services/postApi';
import { useAuth } from '../contexts/AuthContext';
import { Colors } from '../constants/theme';
import { formatDateYYYYMMDD } from '../utils/format';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PostCreate'>;

function buildDefaultTitle(courseTitle?: string): string {
  if (!courseTitle) return '';
  return `[후기] ${courseTitle} - ${formatDateYYYYMMDD(new Date())}`;
}

export default function PostCreateScreen({ route, navigation }: Props) {
  const { attachedCourseId, attachedCourseTitle } = route.params;
  const { accessToken, user } = useAuth();
  const [title, setTitle] = useState(buildDefaultTitle(attachedCourseTitle));
  const [body, setBody] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!accessToken || !user) return;
    if (!title.trim() || !body.trim()) {
      Alert.alert('입력 필요', '제목과 본문을 모두 입력해주세요.');
      return;
    }
    setIsSubmitting(true);
    try {
      const post = await createPost(
        {
          title: title.trim(),
          body: body.trim(),
          attachedCourseId: attachedCourseId ?? null,
        },
        accessToken,
        {
          id: user.id,
          nickname: user.nickname,
          profileImageUrl: user.profileImageUrl,
          bio: user.bio,
        }
      );
      navigation.replace('PostDetail', { postId: post.id });
    } catch (e: unknown) {
      Alert.alert('작성 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>게시글 작성</Text>
        <TouchableOpacity onPress={handleSubmit} disabled={isSubmitting} activeOpacity={0.7}>
          {isSubmitting ? (
            <ActivityIndicator size="small" color={Colors.primary} />
          ) : (
            <Text style={styles.submitLabel}>완료</Text>
          )}
        </TouchableOpacity>
      </View>

      {attachedCourseTitle && (
        <View style={styles.courseChip}>
          <Ionicons name="map-outline" size={14} color={Colors.primary} />
          <Text style={styles.courseChipLabel} numberOfLines={1}>
            {attachedCourseTitle}
          </Text>
        </View>
      )}

      <TextInput
        style={styles.titleInput}
        placeholder="제목을 입력하세요"
        placeholderTextColor={Colors.gray400}
        value={title}
        onChangeText={setTitle}
      />
      <TextInput
        style={styles.bodyInput}
        placeholder="러닝 경험을 자유롭게 남겨보세요"
        placeholderTextColor={Colors.gray400}
        value={body}
        onChangeText={setBody}
        multiline
        textAlignVertical="top"
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  submitLabel: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.primary,
  },
  courseChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginHorizontal: 16,
    marginTop: 12,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: Colors.gray50,
    alignSelf: 'flex-start',
  },
  courseChipLabel: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '600',
  },
  titleInput: {
    marginHorizontal: 16,
    marginTop: 16,
    fontSize: 16,
    fontWeight: '600',
    color: Colors.gray900,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
    paddingBottom: 10,
  },
  bodyInput: {
    flex: 1,
    marginHorizontal: 16,
    marginTop: 16,
    fontSize: 14,
    color: Colors.gray900,
  },
});
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: `PostCreateScreen.tsx` 관련 에러 없음 (Task 9~11 화면이 아직 없어서 나는 `App.tsx`의
import 에러는 계속 나타나는 것이 정상)

- [ ] **Step 3: Commit**

```bash
git add mobile/src/screens/PostCreateScreen.tsx
git commit -m "feat(mobile): 게시글 작성 화면(PostCreateScreen) 추가"
```

---

### Task 9: `PostDetailScreen` 생성

**Files:**
- Create: `mobile/src/screens/PostDetailScreen.tsx`

**Interfaces:**
- Consumes: `getPost` (`../services/postApi`, Task 3), `getComments`/`createComment`
  (`../services/commentApi`, Task 4), `putLike`/`deleteLike` (`../services/likeApi`, Task 5),
  `useAuth`, `useAuthGate`, `RootStackParamList` (Task 6)
- Produces: `PostDetailScreen` 기본 export — Task 8/9/11/12에서 `navigation.navigate('PostDetail', { postId })`로 진입

- [ ] **Step 1: 화면 파일 작성**

`mobile/src/screens/PostDetailScreen.tsx` 생성:

```tsx
import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  Alert,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { getPost } from '../services/postApi';
import { getComments, createComment } from '../services/commentApi';
import { putLike, deleteLike } from '../services/likeApi';
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';
import { Post, Comment } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PostDetail'>;

export default function PostDetailScreen({ route, navigation }: Props) {
  const { postId } = route.params;
  const { accessToken, user } = useAuth();
  const { requireAuth } = useAuthGate();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [commentBody, setCommentBody] = useState('');
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);

  const loadPost = useCallback(async () => {
    try {
      const [postResult, commentsResult] = await Promise.all([
        getPost(postId),
        getComments(postId),
      ]);
      setPost(postResult);
      setComments(commentsResult);
    } catch (e: unknown) {
      Alert.alert('불러오기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
      navigation.goBack();
    } finally {
      setIsLoading(false);
    }
  }, [postId, navigation]);

  useFocusEffect(
    useCallback(() => {
      loadPost();
    }, [loadPost])
  );

  const handleToggleLike = async () => {
    if (!requireAuth() || !post || !accessToken) return;
    try {
      const result = post.likedByMe
        ? await deleteLike(post.id, accessToken)
        : await putLike(post.id, accessToken);
      setPost({ ...post, likedByMe: result.liked, likeCount: result.likeCount });
    } catch (e: unknown) {
      Alert.alert('실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    }
  };

  const handleSubmitComment = async () => {
    if (!requireAuth() || !accessToken || !user || !commentBody.trim()) return;
    setIsSubmittingComment(true);
    try {
      const comment = await createComment(
        postId,
        { body: commentBody.trim() },
        accessToken,
        {
          id: user.id,
          nickname: user.nickname,
          profileImageUrl: user.profileImageUrl,
          bio: user.bio,
        }
      );
      setComments((prev) => [...prev, comment]);
      setCommentBody('');
      setPost((prev) => (prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev));
    } catch (e: unknown) {
      Alert.alert('작성 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSubmittingComment(false);
    }
  };

  if (isLoading || !post) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {post.title}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      <FlatList
        data={comments}
        keyExtractor={(item) => item.id}
        ListHeaderComponent={
          <View style={styles.postBody}>
            <Text style={styles.postTitle}>{post.title}</Text>
            <Text style={styles.postMeta}>
              {post.author.nickname} · {new Date(post.createdAt).toLocaleDateString()}
            </Text>
            {post.attachedCourseId && (
              <TouchableOpacity
                style={styles.courseChip}
                activeOpacity={0.7}
                onPress={() =>
                  navigation.navigate('CourseDetail', { courseId: post.attachedCourseId as string })
                }
              >
                <Ionicons name="map-outline" size={14} color={Colors.primary} />
                <Text style={styles.courseChipLabel}>첨부된 코스 보기</Text>
              </TouchableOpacity>
            )}
            <Text style={styles.postText}>{post.body}</Text>
            <TouchableOpacity style={styles.likeButton} onPress={handleToggleLike} activeOpacity={0.7}>
              <Ionicons
                name={post.likedByMe ? 'heart' : 'heart-outline'}
                size={18}
                color={post.likedByMe ? Colors.danger : Colors.gray500}
              />
              <Text style={styles.likeCount}>{post.likeCount}</Text>
            </TouchableOpacity>
            <Text style={styles.commentsHeading}>댓글 {comments.length}</Text>
          </View>
        }
        renderItem={({ item }) => (
          <View style={styles.commentRow}>
            <Text style={styles.commentAuthor}>{item.author.nickname}</Text>
            <Text style={styles.commentBody}>{item.body}</Text>
          </View>
        )}
        ListEmptyComponent={<Text style={styles.emptyComments}>아직 댓글이 없습니다.</Text>}
      />

      {user ? (
        <View style={styles.commentInputRow}>
          <TextInput
            style={styles.commentInput}
            placeholder="댓글을 입력하세요"
            placeholderTextColor={Colors.gray400}
            value={commentBody}
            onChangeText={setCommentBody}
          />
          <TouchableOpacity onPress={handleSubmitComment} disabled={isSubmittingComment} activeOpacity={0.7}>
            {isSubmittingComment ? (
              <ActivityIndicator size="small" color={Colors.primary} />
            ) : (
              <Text style={styles.commentSubmitLabel}>등록</Text>
            )}
          </TouchableOpacity>
        </View>
      ) : (
        <TouchableOpacity style={styles.loginPrompt} onPress={() => requireAuth()} activeOpacity={0.7}>
          <Text style={styles.loginPromptLabel}>로그인하고 댓글 남기기</Text>
        </TouchableOpacity>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  loadingContainer: {
    flex: 1,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  headerSpacer: {
    width: 24,
  },
  postBody: {
    paddingHorizontal: 20,
    paddingTop: 16,
  },
  postTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
  },
  postMeta: {
    fontSize: 12,
    color: Colors.gray500,
    marginTop: 6,
  },
  courseChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 12,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: Colors.gray50,
    alignSelf: 'flex-start',
  },
  courseChipLabel: {
    fontSize: 12,
    color: Colors.primary,
    fontWeight: '600',
  },
  postText: {
    fontSize: 14,
    color: Colors.gray900,
    marginTop: 16,
    lineHeight: 20,
  },
  likeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 16,
  },
  likeCount: {
    fontSize: 13,
    color: Colors.gray500,
  },
  commentsHeading: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.gray900,
    marginTop: 20,
    marginBottom: 8,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
    paddingTop: 16,
  },
  commentRow: {
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  commentAuthor: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.gray900,
  },
  commentBody: {
    fontSize: 13,
    color: Colors.gray500,
    marginTop: 2,
  },
  emptyComments: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    fontSize: 13,
    color: Colors.gray400,
  },
  commentInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  commentInput: {
    flex: 1,
    fontSize: 14,
    color: Colors.gray900,
    paddingVertical: 8,
  },
  commentSubmitLabel: {
    fontSize: 14,
    fontWeight: '700',
    color: Colors.primary,
  },
  loginPrompt: {
    paddingVertical: 14,
    alignItems: 'center',
    borderTopWidth: 1,
    borderTopColor: Colors.gray100,
  },
  loginPromptLabel: {
    fontSize: 13,
    color: Colors.primary,
    fontWeight: '600',
  },
});
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: `PostDetailScreen.tsx` 관련 에러 없음 (Task 10의 `CourseBoardScreen`이 아직 없어서 나는
`App.tsx`의 미해결 import 에러는 계속 나타나는 것이 정상)

- [ ] **Step 3: Commit**

```bash
git add mobile/src/screens/PostDetailScreen.tsx
git commit -m "feat(mobile): 게시글 상세 화면(PostDetailScreen) 추가 — 좋아요/댓글 포함"
```

---

### Task 10: `CourseBoardScreen` 생성

**Files:**
- Create: `mobile/src/screens/CourseBoardScreen.tsx`

**Interfaces:**
- Consumes: `getPosts` (`../services/postApi`, Task 3), `PostListItem` (`../components/PostListItem`,
  Task 7), `useAuthGate`, `RootStackParamList` (Task 6)
- Produces: `CourseBoardScreen` 기본 export — Task 12(`MapScreen`)의 "게시판" 버튼이
  `navigation.navigate('CourseBoard', { courseId, courseTitle })`로 진입

- [ ] **Step 1: 화면 파일 작성**

`mobile/src/screens/CourseBoardScreen.tsx` 생성:

```tsx
import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import PostListItem from '../components/PostListItem';
import { getPosts } from '../services/postApi';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';
import { Post } from '../types';
import { RootStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'CourseBoard'>;

export default function CourseBoardScreen({ route, navigation }: Props) {
  const { courseId, courseTitle } = route.params;
  const { requireAuth } = useAuthGate();
  const [posts, setPosts] = useState<Post[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useFocusEffect(
    useCallback(() => {
      let isActive = true;
      setIsLoading(true);
      getPosts({ attachedCourseId: courseId }).then((result) => {
        if (isActive) {
          setPosts(result);
          setIsLoading(false);
        }
      });
      return () => {
        isActive = false;
      };
    }, [courseId])
  );

  const handlePressWrite = () => {
    if (!requireAuth()) return;
    navigation.navigate('PostCreate', {
      attachedCourseId: courseId,
      attachedCourseTitle: courseTitle,
    });
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Ionicons name="chevron-back" size={24} color={Colors.gray900} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>
          {courseTitle}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item) => item.id}
          contentContainerStyle={posts.length === 0 ? styles.emptyContainer : undefined}
          ListEmptyComponent={
            <Text style={styles.emptyText}>이 코스로 작성된 게시글이 없습니다.</Text>
          }
          renderItem={({ item }) => (
            <PostListItem
              post={item}
              onPress={(postId) => navigation.navigate('PostDetail', { postId })}
            />
          )}
        />
      )}

      <TouchableOpacity style={styles.writeFab} onPress={handlePressWrite} activeOpacity={0.8}>
        <Ionicons name="create-outline" size={20} color={Colors.white} />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  headerSpacer: {
    width: 24,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  writeFab: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
});
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 — `App.tsx`가 참조하는 `PostCreate`/`PostDetail`/`CourseBoard` 화면이 모두
갖춰졌으므로, Task 6에서 시작된 미해결 import 에러가 이 시점에 모두 해소된다 (Task 11은 기존에
이미 컴파일되던 `BoardScreen.tsx`의 내용만 바꾸는 것이라 이 통과 여부에 영향을 주지 않는다)

- [ ] **Step 3: Commit**

```bash
git add mobile/src/screens/CourseBoardScreen.tsx
git commit -m "feat(mobile): 코스별 게시판 화면(CourseBoardScreen) 추가"
```

---

### Task 11: `BoardScreen` 교체 — 전역 게시글 목록/작성

**Files:**
- Modify: `mobile/src/screens/BoardScreen.tsx`

**Interfaces:**
- Consumes: `getPosts` (`../services/postApi`, Task 3), `PostListItem`
  (`../components/PostListItem`, Task 7), `useAuthGate`, `RootTabParamList`/`RootStackParamList`
  (Task 6)
- Produces: 없음 (탭 화면 최종 조립). `npx tsc --noEmit`은 Task 10 완료 시점에 이미 통과 상태다 —
  이 태스크는 기존에 컴파일되던 placeholder를 실제 기능으로 바꾸는 것뿐이다

- [ ] **Step 1: 파일 전체 교체**

`mobile/src/screens/BoardScreen.tsx` 전체를 아래로 교체:

```tsx
import React, { useCallback, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import PostListItem from '../components/PostListItem';
import { getPosts } from '../services/postApi';
import { useAuthGate } from '../hooks/useAuthGate';
import { Colors } from '../constants/theme';
import { Post } from '../types';
import { RootTabParamList, RootStackParamList } from '../navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Board'>,
  NativeStackScreenProps<RootStackParamList>
>;

export default function BoardScreen({ navigation }: Props) {
  const { requireAuth } = useAuthGate();
  const [posts, setPosts] = useState<Post[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useFocusEffect(
    useCallback(() => {
      let isActive = true;
      setIsLoading(true);
      getPosts({}).then((result) => {
        if (isActive) {
          setPosts(result);
          setIsLoading(false);
        }
      });
      return () => {
        isActive = false;
      };
    }, [])
  );

  const handlePressWrite = () => {
    if (!requireAuth()) return;
    navigation.navigate('PostCreate', {});
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>게시판</Text>
      </View>

      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item) => item.id}
          contentContainerStyle={posts.length === 0 ? styles.emptyContainer : undefined}
          ListEmptyComponent={<Text style={styles.emptyText}>아직 게시글이 없습니다.</Text>}
          renderItem={({ item }) => (
            <PostListItem
              post={item}
              onPress={(postId) => navigation.navigate('PostDetail', { postId })}
            />
          )}
        />
      )}

      <TouchableOpacity style={styles.writeFab} onPress={handlePressWrite} activeOpacity={0.8}>
        <Ionicons name="create-outline" size={20} color={Colors.white} />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.white,
  },
  header: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.gray100,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: Colors.gray400,
    fontSize: 14,
  },
  writeFab: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
});
```

- [ ] **Step 2: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 (Task 10부터 이미 통과 상태 — 이 태스크는 이를 유지하는지 재확인하는 것)

- [ ] **Step 3: Commit**

```bash
git add mobile/src/screens/BoardScreen.tsx
git commit -m "feat(mobile): 게시판 탭을 전역 게시글 목록/작성 화면으로 교체"
```

---

### Task 12: `MapScreen` — 코스 선택 시 커뮤니티 버튼 연결

**Files:**
- Modify: `mobile/src/screens/MapScreen.tsx`

**Interfaces:**
- Consumes: `selectedCourseId`, `selectedCourseDetail`, `isCourseSheetOpen`, `searchButtonBottom`,
  `requireAuth` (모두 기존 `MapScreen` 내부 상태/훅), `RootStackParamList` (Task 6)
- Produces: 없음 (최종 화면 조립)

- [ ] **Step 1: import 추가/수정**

`mobile/src/screens/MapScreen.tsx:13`(`import { SafeAreaView } from 'react-native-safe-area-context';`)
바로 뒤에 추가:

```ts
import { CompositeScreenProps } from '@react-navigation/native';
```

`mobile/src/screens/MapScreen.tsx:15`를 교체:

```ts
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
```

`mobile/src/screens/MapScreen.tsx:35`(`import { RootTabParamList } from '../navigation/types';`)를
교체:

```ts
import { RootTabParamList, RootStackParamList } from '../navigation/types';
```

- [ ] **Step 2: Props 타입을 CompositeScreenProps로 변경**

`mobile/src/screens/MapScreen.tsx:37`의 아래 줄을:

```ts
type Props = BottomTabScreenProps<RootTabParamList, 'Map'>;
```

아래로 교체 (Map은 탭 화면이지만 `PostCreate`/`CourseBoard`는 부모 Stack의 화면이라, `SavedRoutesScreen.tsx`와 동일한 패턴으로 합성 타입이 필요하다):

```ts
type Props = CompositeScreenProps<
  BottomTabScreenProps<RootTabParamList, 'Map'>,
  NativeStackScreenProps<RootStackParamList>
>;
```

- [ ] **Step 3: 코스 커뮤니티 액션 핸들러 추가**

`mobile/src/screens/MapScreen.tsx`의 `handleViewCourseDetail` 함수(현재 파일 기준 200-205줄)
바로 뒤, `handleUndo` 함수 앞에 추가:

```ts

  const handlePressWritePost = () => {
    if (!requireAuth() || !selectedCourseDetail) return;
    navigation.navigate('PostCreate', {
      attachedCourseId: selectedCourseDetail.id,
      attachedCourseTitle: selectedCourseDetail.title,
    });
  };

  const handlePressCourseBoard = () => {
    if (!selectedCourseDetail) return;
    navigation.navigate('CourseBoard', {
      courseId: selectedCourseDetail.id,
      courseTitle: selectedCourseDetail.title,
    });
  };
```

- [ ] **Step 4: 우측 FAB에 커뮤니티 버튼 2개 추가**

`mobile/src/screens/MapScreen.tsx`에서 기존 "우측 플로팅 버튼" 블록(현재 파일 기준 327-345줄)을
찾는다:

```tsx
        {/* 우측 플로팅 버튼 — 시트가 펼쳐진 동안은 '내 경로' 도구라 맥락에 안 맞으므로 숨기고,
            접혔을 때는 시트 상단(핸들) 바로 위로 떠오른다 */}
        {(!isCourseSheetOpen || isCourseSheetCollapsed) && (
          <Animated.View style={[styles.floatingButtons, { bottom: floatingButtonsBottom }]}>
            <FAB icon="locate" onPress={handleLocate} />
            <FAB
              icon="arrow-undo"
              onPress={handleUndo}
              disabled={waypoints.length === 0 || isRouting}
            />
            <FAB icon="save-outline" onPress={handleOpenSaveModal} disabled={!canSave} />
            <FAB
              icon="trash-outline"
              onPress={handleClear}
              disabled={waypoints.length === 0 || isRouting}
              color={Colors.danger}
            />
          </Animated.View>
        )}
```

이 블록 바로 뒤(`<CourseSearchSheet` 블록 앞)에 아래를 추가한다. `bottom`은 `floatingButtonsBottom`이
아니라 **`searchButtonBottom`**을 재사용한다 — `searchButtonBottom`은 이미 좌측 "코스 조회" 버튼이
시트의 실시간 위치(펼침/접힘 모두)를 따라가도록 만들어진 값이라, 시트가 펼쳐져 있을 때도 시트에
가려지지 않고 바로 위에 뜬다. 반대로 `floatingButtonsBottom`은 시트가 펼쳐진 동안 `16`(기본값)으로
떨어져 있어서 시트에 가려진다:

```tsx

        {/* 코스 커뮤니티 액션 버튼 — 코스 탐색 시트가 열려 있는 동안(펼침/접힘 모두) 보이고,
            좌측 탐색 버튼과 같은 방식으로 시트 상단 위치를 따라간다. 코스를 선택해야 눌린다. */}
        {isCourseSheetOpen && (
          <Animated.View style={[styles.floatingButtons, { bottom: searchButtonBottom }]}>
            <FAB
              icon="create-outline"
              onPress={handlePressWritePost}
              disabled={!selectedCourseDetail}
            />
            <FAB
              icon="list-outline"
              onPress={handlePressCourseBoard}
              disabled={!selectedCourseDetail}
            />
          </Animated.View>
        )}
```

- [ ] **Step 5: 타입 체크로 확인**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 6: Commit**

```bash
git add mobile/src/screens/MapScreen.tsx
git commit -m "feat(mobile): Map 화면에 코스 선택 기반 게시글 작성/게시판 버튼 연결"
```

---

### Task 13: 수동 검증 + 구현 문서 기록

**Files:**
- Create: `mobile/docs/implementations/community-post-mobile-ui.md`

**Interfaces:**
- Consumes: 없음 (검증 및 문서화 태스크)
- Produces: 없음

- [ ] **Step 1: Expo 번들 기동 확인**

Run:
```bash
cd mobile && npx expo start --non-interactive &
sleep 8
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"
```
Expected: `200`

번들 확인 후 `npx expo start` 프로세스를 종료한다 (`kill %1` 또는 해당 터미널 종료).

- [ ] **Step 2: 시뮬레이터/실기기 수동 확인**

아래 항목을 실기기 또는 시뮬레이터에서 직접 확인한다 (`mobile/CLAUDE.md` §변경 후 검증 규칙):

1. Map 화면 → 코스 탐색 버튼 → 주변 코스 목록에서 코스를 하나 선택하면, 우측에 "게시글 작성"/
   "게시판" 원형 버튼이 (비활성 상태로) 나타나고, 선택 즉시 활성화(색이 채워짐)된다.
2. 코스를 선택하지 않은 상태(시트만 연 상태)에서는 두 버튼이 비활성(회색, 탭 무시) 상태다.
3. 비로그인 상태에서 "게시글 작성"을 누르면 로그인 유도 모달이 뜨고, 로그인 후에는 작성 화면으로
   이동한다.
4. "게시글 작성" 진입 시 제목이 `[후기] {코스명} - {오늘 날짜}` 형식으로 미리 채워져 있고, 수정할
   수 있다.
5. 본문을 입력하고 완료를 누르면 게시글 상세 화면으로 이동하고, 첨부된 코스 chip이 보인다.
6. "게시판" 버튼(로그인 여부 무관하게 눌림)을 누르면 방금 작성한 글이 코스별 게시판 목록에 보인다.
7. 하단 탭바 "게시판" 탭에서도 같은 글이 전역 목록에 보인다.
8. 게시글 상세에서 좋아요 버튼을 누르면 하트가 채워지고 개수가 1 증가하며, 다시 누르면 원복된다.
9. 게시글 상세에서 댓글을 작성하면 목록에 즉시 반영되고 "댓글 N" 카운트가 올라간다.
10. 비로그인 상태로 게시글 상세에 들어가면 댓글 입력창 대신 "로그인하고 댓글 남기기" 버튼이 보인다.
11. 앱을 완전히 재시작하면 mock 시드 데이터(2개)만 남고, 방금 작성한 글은 사라진다 (의도된 동작 —
    `postApi.ts` 상단 주석 참고).

- [ ] **Step 3: 구현 문서 작성**

`mobile/docs/implementations/community-post-mobile-ui.md` 생성:

```markdown
# 커뮤니티 게시글 모바일 UI

## 배경

`docs/product-scope.md` MVP 범위의 "게시글 작성/조회", "댓글 작성/조회", "코스와 게시글 좋아요"를
Map 화면(코스 선택 → 게시글 작성/게시판)과 게시판 탭에 실제로 붙이는 작업. 백엔드 Post/Comment가
아직 없어서(`community` 패키지에는 `Like` 엔티티만 존재) 모바일 UI + in-memory mock 서비스로만
구현했다.

## 변경 내용

- `docs/api-contract.md`의 `GET /posts`에 `attachedCourseId` 필터 쿼리 파라미터를 추가했다
  (코스별 게시판 조회용).
- `postApi.ts`/`commentApi.ts`/`likeApi.ts` mock 서비스를 추가했다. `docs/api-contract.md` §Post/
  Comment/Like APIs 계약과 같은 모양으로 응답하지만 실제 `fetch` 없이 in-memory 배열로 동작한다.
  파일 상단에 "백엔드 구현되면 courseApi.ts 패턴으로 교체" 주석을 남겼다. 앱을 재시작하면 시드
  데이터(2개)로 초기화된다.
- `PostCreateScreen`(작성, 코스 첨부 시 `[후기] 코스명 - 날짜` 기본 제목), `PostDetailScreen`
  (본문/좋아요 토글/댓글), `CourseBoardScreen`(코스별 게시글 목록)을 신규 Stack 화면으로 추가했다.
- `BoardScreen`(게시판 탭)을 placeholder에서 전역 게시글 목록/작성 화면으로 교체했다.
- `MapScreen` 우측 FAB 스택에 "게시글 작성"/"게시판" 원형 버튼을 추가했다. 코스 탐색 시트가 열려
  있는 동안만 보이고, 코스를 선택해야 활성화된다. 좌측 "코스 조회" 버튼과 같은
  `searchButtonBottom` 애니메이션 값을 재사용해 시트가 펼쳐진 상태에서도 가려지지 않는다.

## 이번 범위에서 하지 않은 것

- 백엔드 Post/Comment/Like 실제 구현 — 모바일만 다룬다.
- 게시글/댓글 수정·삭제, 태그 입력/필터 UI, 페이지네이션.

## 검증

- `npx tsc --noEmit` 통과.
- `npx expo start` 번들 200 확인.
- 시뮬레이터에서 코스 선택 → 버튼 활성화 → 게시글 작성(기본 제목 확인) → 코스별/전역 게시판 노출 →
  좋아요 토글 → 댓글 작성까지 전체 흐름 확인.
```

- [ ] **Step 4: Commit**

```bash
git add mobile/docs/implementations/community-post-mobile-ui.md
git commit -m "docs(mobile): 커뮤니티 게시글 모바일 UI 구현 기록 추가"
```
