# 커뮤니티 게시글(Post) 모바일 UI 설계

작성일: 2026-07-05
관련 문서: `docs/api-contract.md` §Post APIs/Comment APIs/Like APIs, `docs/data-model.md` §Post/§Comment/§LikeTargetType,
`docs/product-scope.md` (게시글 작성/조회, 댓글 작성/조회, 코스와 게시글 좋아요), `mobile/CLAUDE.md`, `mobile/AGENTS.md`

## 배경

`docs/api-contract.md`, `docs/data-model.md`에는 Post/Comment/Like API 계약이 이미 상세히
정의되어 있다. 하지만 `backend/`에는 아직 Post/Comment가 구현되어 있지 않다 (`community` 패키지에는
`Like` 엔티티만 존재하고, Post/Comment 엔티티·컨트롤러·마이그레이션이 없다).

이번 작업은 **모바일 UI만** 먼저 만든다. 백엔드가 없는 동안에는 `docs/api-contract.md`의 응답
모양을 그대로 따르는 명시적 in-memory mock 서비스로 화면을 동작시키고, 백엔드가 준비되면
`courseApi.ts` 패턴(실제 `fetch`)으로 교체한다. 이 mock은 임시 구현임을 코드 주석으로 명시한다.

## 목표

1. Map 화면에서 주변 코스 목록 중 코스 카드를 선택하면, 그 코스에 대한 "게시글 작성"/"게시판"
   원형 버튼이 활성화된다.
2. "게시글 작성" 버튼 → 해당 코스가 첨부된 후기 게시글 작성 화면으로 이동. 제목은
   `[후기] {코스명} - {YYYY.MM.DD}` 형식으로 기본 입력되어 있으나 수정 가능하다.
3. "게시판" 버튼 → 해당 코스로 작성된 게시글 목록 화면으로 이동.
4. 하단 탭바의 "게시판" 탭 → 전체 게시글 목록 조회 및 작성이 가능한 화면으로 이동 (기존
   placeholder를 교체).
5. 게시글 상세에서 좋아요 토글, 댓글 목록 조회·작성이 가능하다.

## 범위 밖 (명시적 트림)

- 백엔드 Post/Comment/Like 실제 구현 — 이번 작업은 모바일 UI + mock만 다룬다.
- 게시글/댓글 수정·삭제 — 작성·조회만 다룬다.
- 게시글 태그 입력 UI, 태그/검색어 필터 UI — 목록은 정렬(`createdAtDesc`)만 지원.
- 페이지네이션("더 보기") — 첫 페이지(`limit` 기본값)만 사용.
- 코스 좋아요(`LikeTargetType.COURSE`) UI — 이번 범위는 게시글 좋아요만 다룬다.

## 문서 변경: `docs/api-contract.md`, `docs/data-model.md`

`GET /posts`에 코스별 게시글 필터용 쿼리 파라미터 `attachedCourseId`를 추가한다. 지금 계약에는
`q`/`tag`/`sort`/`limit`/`cursor`만 있고 코스 단위 필터가 없어서, "코스로 작성된 게시글 목록"을
만들려면 계약에 먼저 추가해야 한다 (`CLAUDE.md` 문서 우선 원칙).

- `GET /posts?attachedCourseId=course_123` → 해당 코스가 첨부된 게시글만 반환, 기존 `q`/`tag`/`sort`와
  함께 전달되면 모든 조건을 만족하는 게시글만 반환 (`GET /courses`의 필터 조합 규칙과 동일하게 서술)
- 나머지 Post/Comment/Like 필드·엔드포인트·에러 케이스는 변경 없음

이 문서 변경은 이번 모바일 mock 구현의 계약 기준이 되고, 추후 실제 백엔드 구현 시 그대로 따라간다.
Post/Comment 백엔드가 아직 없으므로 이 변경은 "미구현 계약"이라는 점을 데이터모델 문서 어딘가에
남기지 않는다 — `docs/data-model.md`/`api-contract.md`는 항상 "현재 합의된 계약"만 서술하고
구현 여부는 코드/커밋 기록으로 판단한다는 기존 원칙을 따른다.

## 타입 추가 (`mobile/src/types/index.ts`)

`docs/data-model.md`와 1:1 대응:

```ts
export interface PublicProfile {
  id: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
}

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

export interface Comment {
  id: string;
  postId: string;
  author: PublicProfile;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePostRequestBody {
  title: string;
  body: string;
  attachedCourseId?: string | null;
  tags?: string[];
}

export interface CreateCommentRequestBody {
  body: string;
}
```

## Mock 서비스 레이어

기존 `courseApi.ts`는 실제 `fetch` 호출이지만, Post/Comment/Like는 백엔드가 없으므로 파일 최상단에
"백엔드 Post API 구현되면 이 mock을 courseApi.ts 패턴의 실제 fetch로 교체" 주석을 남기고
in-memory 배열로 상태를 흉내낸다. 앱을 재시작하면 시드 데이터로 초기화된다 (의도된 동작).

### `mobile/src/services/postApi.ts`

```ts
export async function getPosts(params: {
  attachedCourseId?: string;
  sort?: 'createdAtDesc' | 'popularDesc';
}): Promise<Post[]>

export async function getPost(postId: string): Promise<Post>

export async function createPost(
  body: CreatePostRequestBody,
  accessToken: string,
  authorContext: PublicProfile // 로그인 사용자 정보를 author로 채우기 위해 호출부에서 전달
): Promise<Post>
```

- 시드 데이터 2~3개 (닉네임/코스 첨부 예시 다양화)
- `createPost`는 배열 맨 앞에 새 글을 추가하고 반환
- `getPosts`는 `attachedCourseId`가 있으면 해당 필드로 필터링, 기본 정렬은 `createdAtDesc`
  (`createdAt` 내림차순)

### `mobile/src/services/commentApi.ts`

```ts
export async function getComments(postId: string): Promise<Comment[]>
export async function createComment(
  postId: string,
  body: CreateCommentRequestBody,
  accessToken: string,
  authorContext: PublicProfile
): Promise<Comment>
```

- 댓글 생성 시 대상 게시글의 `commentCount`도 함께 갱신 (mock 저장소 내부에서 일관성 유지)

### `mobile/src/services/likeApi.ts`

```ts
export async function putLike(postId: string, accessToken: string): Promise<{ liked: boolean; likeCount: number }>
export async function deleteLike(postId: string, accessToken: string): Promise<{ liked: boolean; likeCount: number }>
```

- `LikeTargetType`은 이번 범위에서 `'posts'` 고정이므로 target 타입 파라미터는 받지 않는다
  (코스 좋아요는 범위 밖)
- mock 저장소의 해당 post `likeCount`/`likedByMe`를 갱신

## 네비게이션

`mobile/src/navigation/types.ts`의 `RootStackParamList`에 3개 화면 추가:

```ts
export type RootStackParamList = {
  Tabs: NavigatorScreenParams<RootTabParamList> | undefined;
  CourseDetail: { courseId: string };
  PostCreate: { attachedCourseId?: string; attachedCourseTitle?: string };
  PostDetail: { postId: string };
  CourseBoard: { courseId: string; courseTitle: string };
};
```

`App.tsx`의 `Stack.Navigator`에 세 `Stack.Screen`을 `CourseDetail`과 같은 방식으로 등록한다.

## 화면

### `PostCreateScreen.tsx` (신규)

- params: `attachedCourseId?`, `attachedCourseTitle?`
- 제목 입력 필드: `attachedCourseTitle`이 있으면
  `` `[후기] ${attachedCourseTitle} - ${formatDateYYYYMMDD(new Date())}` `` 로 prefill, 없으면 빈 문자열
  (전역 게시판에서 바로 쓰는 경우)
- 본문 입력 필드 (필수, `docs` 제한값 1-5000자)
- 첨부 코스가 있으면 읽기 전용 chip으로 코스명 표시 (수정 불가 — 코스를 바꾸려면 다시 선택해서
  글쓰기 진입)
- 제출: `requireAuth()` 통과 후(이미 화면 진입 자체가 인증 필요 지점이므로 이중 방어) `createPost` 호출,
  성공 시 `navigation.replace('PostDetail', { postId: created.id })`
- 실패 시 기존 패턴대로 `Alert.alert`

`formatDateYYYYMMDD`는 `mobile/src/utils/format.ts`에 추가하는 순수 함수 (`2026.07.05` 형식).

### `PostDetailScreen.tsx` (신규)

- params: `postId`
- 상단: 뒤로가기 + 제목 (기존 `CourseDetailScreen` 헤더 패턴 재사용)
- 본문: 작성자 닉네임, 작성일, 첨부 코스가 있으면 코스명 표시(탭하면 `CourseDetail`로 이동),
  본문 텍스트, 태그
- 좋아요 버튼: `likedByMe` 여부에 따라 아이콘 채움/비움 토글, `requireAuth()` 게이트
- 댓글 목록 (`getComments`) + 댓글 입력창 (`requireAuth()` 게이트, 비로그인은 입력창 대신 로그인 유도)

### `CourseBoardScreen.tsx` (신규)

- params: `courseId`, `courseTitle`
- 상단 헤더에 `courseTitle` 표시
- `getPosts({ attachedCourseId: courseId })` 목록, `BoardScreen`과 같은 행 컴포넌트 재사용
- 우측 하단 FAB(글쓰기) → `requireAuth()` 후
  `navigation.navigate('PostCreate', { attachedCourseId: courseId, attachedCourseTitle: courseTitle })`
- 행 탭 → `navigation.navigate('PostDetail', { postId })`

### `BoardScreen.tsx` (기존 placeholder 교체)

- "백엔드 Post API가 아직 없다"는 기존 Non-Goal 주석 제거, 실제 mock 연동으로 교체
- `getPosts({})` 전체 목록 (필터 없음), 정렬은 `createdAtDesc` 고정
- 우측 하단 FAB(글쓰기) → `requireAuth()` 후 `navigation.navigate('PostCreate', {})`
  (첨부 코스 없음)
- 행 탭 → `navigation.navigate('PostDetail', { postId })`
- 빈 목록: 기존 "아직 게시글이 없습니다" 문구 유지

### 목록 행 공용 컴포넌트

`BoardScreen`과 `CourseBoardScreen`이 같은 모양의 게시글 행을 쓰므로, `PostListItem`
프레젠테이션 컴포넌트를 하나 만들어 재사용한다 (제목, 작성자 닉네임, 좋아요/댓글 수, 작성일,
첨부 코스 여부 뱃지).

## Map 화면 연동

`CourseSearchSheet`에서 코스를 선택하면(`selectedCourseId`/`selectedCourseDetail`) `MapScreen`
우측 FAB 스택에 원형 버튼 2개를 추가한다 (기존 46×46 `FAB` 컴포넌트 재사용):

- "게시글 작성" (`icon="create-outline"`)
- "게시판" (`icon="list-outline"`, 탭바의 `chatbubbles-outline`과 아이콘을 구분)

표시 조건: 기존 "내 경로" FAB 그룹과 반대로, `isCourseSheetOpen`일 때만 보인다 (펼침/접힘 상관없이).
활성화(선택 가능) 조건: `selectedCourseId && selectedCourseDetail` (기존 "상세보기" 버튼과 같은 패턴).

```
게시글 작성 onPress:
  if (!requireAuth()) return;
  navigation.navigate('PostCreate', {
    attachedCourseId: selectedCourseDetail.id,
    attachedCourseTitle: selectedCourseDetail.title,
  });

게시판 onPress:
  navigation.navigate('CourseBoard', {
    courseId: selectedCourseDetail.id,
    courseTitle: selectedCourseDetail.title,
  });
```

"게시판" 조회는 `GET /posts`가 `Auth: Optional`이므로 로그인 게이트를 걸지 않는다.

## 에러 처리

- mock 서비스 함수도 `courseApi.ts`와 동일한 형태로 실패를 던질 수 있게 만들되(존재하지 않는
  `postId` 조회 시 `Error` throw), 화면에서는 기존 패턴대로 `Alert.alert`로 표시한다.
- `accessToken` 없이 글쓰기/댓글/좋아요 mock 함수가 호출되는 경로는 없다 (화면 진입 전에
  `requireAuth()`로 막는다).

## 검증 계획

- `npx tsc --noEmit`
- `npx expo start` 백그라운드 기동 후 bundle 200 확인
- 실기기/시뮬레이터에서: Map → 코스 탐색 → 코스 선택 → 게시글 작성/게시판 버튼 활성화 확인 →
  게시글 작성(기본 제목 확인) → 저장 후 상세 화면 진입 확인 → 코스별 게시판에 노출 확인 →
  좋아요 토글 → 댓글 작성 → 전역 게시판 탭에서도 같은 글이 보이는지 확인
- 완료 후 `mobile/docs/implementations/community-post-mobile-ui.md`에 설계 요약 기록
