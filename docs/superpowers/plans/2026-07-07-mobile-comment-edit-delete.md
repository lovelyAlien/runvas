# 게시글 댓글 수정/삭제 (모바일) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 게시글 상세 화면(`PostDetailScreen`)에서 본인이 작성한 댓글에 "수정"/"삭제" 버튼을 노출하고, 인라인 편집과 삭제 확인 다이얼로그를 통해 댓글을 수정·삭제할 수 있게 한다.

**Architecture:** 백엔드(`CommentController`/`CommentService`)와 `docs/api-contract.md`는 이미 `PATCH /comments/{commentId}`, `DELETE /comments/{commentId}`를 작성자 검증까지 포함해 구현·정의해두었다. 모바일에는 이 API를 호출하는 클라이언트 함수가 없고, 화면에도 수정/삭제 UI가 없다. `mobile/src/services/commentApi.ts`에 `updateComment`/`deleteComment` 두 함수를 기존 `createComment` 패턴으로 추가하고, `PostDetailScreen.tsx`의 댓글 `renderItem`에 작성자 본인 여부 체크와 인라인 편집 상태를 추가한다.

**Tech Stack:** React Native (Expo SDK 54), TypeScript, 기존 `AuthContext` (`user`, `accessToken`), 기존 `Alert.alert` 확인 다이얼로그 패턴 (`CourseDetailScreen.tsx`의 댓글 삭제 패턴과 동일).

## Global Constraints

- 이번 작업은 **모바일 전용**이다. `docs/`, `backend/`는 이미 계약과 구현이 완료돼 있으므로 변경하지 않는다 (`docs/superpowers/specs/2026-07-07-mobile-comment-edit-delete-design.md` §배경).
- 수정/삭제 버튼은 본인 댓글에 **항상 보이는 텍스트 버튼**으로 노출한다 (케밥 메뉴나 길게 누르기 방식 아님 — 브레인스토밍에서 확정).
- 수정은 **인라인 편집**이다. 다른 화면으로 이동하거나 모달을 띄우지 않는다 — 댓글이 그 자리에서 `TextInput`으로 바뀐다.
- 대댓글(reply) 개념은 없다. Post 댓글은 평면 목록이며, Course 댓글(`CourseCommentItem.tsx`)과는 다른 모델이다 — 그 파일의 패턴을 참고는 하되 reply 관련 prop은 가져오지 않는다.
- 삭제는 하드 삭제다. 백엔드가 204로 실제로 row를 제거하므로, 모바일도 "삭제된 댓글입니다" 같은 placeholder 없이 목록에서 완전히 제거한다.
- 새로운 에러 UI 패턴을 만들지 않는다. 기존 화면에 이미 있는 `Alert.alert(title, message)` 컨벤션을 그대로 쓴다.
- 이 저장소에는 jest 등 테스트 러너가 없다 (`mobile/CLAUDE.md` §테스트). 각 태스크 검증은 `npx tsc --noEmit` + 코드 리뷰로 하고, 실제 동작 확인은 마지막 태스크에서 시뮬레이터/실기기로 한다.
- 커밋 메시지는 Conventional Commits 형식, `git add`는 파일을 명시해서 add한다 (`git add -A`/`git add .` 금지). 커밋 메시지에 도구/저작자 표시(`Co-Authored-By` 등)를 넣지 않는다.
- 현재 브랜치(`docs/comment-edit-delete-design`)에서 계속 작업한다. 이 브랜치 이름은 스펙 커밋 때 임시로 붙인 이름이므로, Task 1 커밋 전에 `feat/comment-edit-delete`로 리네임한다.
- `core.hooksPath=.githooks`가 이미 이 워킹 디렉토리에 설정되어 있다 (확인 완료 — commit-msg 훅 존재). 새 워크트리가 아니므로 `scripts/setup-git-hooks.sh` 재실행은 불필요하다.

---

### Task 1: 브랜치 리네임 + 타입/API 클라이언트 함수 추가

**Files:**
- Modify: `mobile/src/types/index.ts`
- Modify: `mobile/src/services/commentApi.ts`

**Interfaces:**
- Consumes: 기존 `Comment` 타입 (`../types`), `parseApiErrorMessage` (`../utils/apiError`), 기존 `API_BASE_URL` 상수
- Produces: `UpdateCommentRequestBody` 타입, `updateComment(commentId: string, body: UpdateCommentRequestBody, accessToken: string): Promise<Comment>`, `deleteComment(commentId: string, accessToken: string): Promise<void>` — Task 2가 이 두 함수를 사용

- [ ] **Step 1: 브랜치 리네임**

```bash
git branch -m docs/comment-edit-delete-design feat/comment-edit-delete
```

- [ ] **Step 2: `UpdateCommentRequestBody` 타입 추가**

`mobile/src/types/index.ts` 파일 끝(`CreateCommentRequestBody` 인터페이스 바로 뒤)에 추가:

```ts

// docs/api-contract.md PATCH /comments/{commentId} 요청 본문과 1:1 대응.
export interface UpdateCommentRequestBody {
  body: string;
}
```

- [ ] **Step 3: `updateComment`/`deleteComment` 함수 추가**

`mobile/src/services/commentApi.ts` 상단 import를 아래로 교체 (타입 추가):

```ts
// 댓글 API (GET/POST/PATCH/DELETE) — runvas/backend의 CommentController와 연동됨.
import { Comment, CreateCommentRequestBody, UpdateCommentRequestBody } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';
```

파일 끝(`createComment` 함수 뒤)에 추가:

```ts

export async function updateComment(
  commentId: string,
  body: UpdateCommentRequestBody,
  accessToken: string
): Promise<Comment> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/comments/${commentId}`, {
    method: 'PATCH',
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

export async function deleteComment(commentId: string, accessToken: string): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/comments/${commentId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
```

- [ ] **Step 4: 타입 검사**

Run: `cd mobile && npx tsc --noEmit`
Expected: `TypeScript: No errors found` (에러가 있다면 import 경로나 타입 이름 오타를 확인한다)

- [ ] **Step 5: Commit**

```bash
git add mobile/src/types/index.ts mobile/src/services/commentApi.ts
git commit -m "feat(mobile): 댓글 수정/삭제 API 클라이언트 함수 추가"
```

---

### Task 2: `PostDetailScreen`에 댓글 인라인 수정/삭제 UI 추가

**Files:**
- Modify: `mobile/src/screens/PostDetailScreen.tsx`

**Interfaces:**
- Consumes: Task 1의 `updateComment`, `deleteComment` (`../services/commentApi`); 기존 `user`, `accessToken` (`useAuth()`), 기존 `comments`/`post` state, 기존 `Comment` 타입
- Produces: 없음 (이 플랜의 마지막 코드 태스크 — Task 3에서 수동 검증)

- [ ] **Step 1: import에 `updateComment`, `deleteComment` 추가**

`mobile/src/screens/PostDetailScreen.tsx:21`의:

```ts
import { getComments, createComment } from '../services/commentApi';
```

를 아래로 교체:

```ts
import { getComments, createComment, updateComment, deleteComment } from '../services/commentApi';
```

- [ ] **Step 2: 편집 상태 추가**

`mobile/src/screens/PostDetailScreen.tsx:39-40`의:

```ts
  const [commentBody, setCommentBody] = useState('');
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);
```

를 아래로 교체:

```ts
  const [commentBody, setCommentBody] = useState('');
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);
  const [editingCommentId, setEditingCommentId] = useState<string | null>(null);
  const [editingBody, setEditingBody] = useState('');
  const [isSavingEdit, setIsSavingEdit] = useState(false);
```

- [ ] **Step 3: 편집/삭제 핸들러 추가**

`mobile/src/screens/PostDetailScreen.tsx:90`의 `handleSubmitComment` 함수가 끝나는 `};` 바로 뒤에 추가:

```ts

  const handleStartEdit = (comment: Comment) => {
    setEditingCommentId(comment.id);
    setEditingBody(comment.body);
  };

  const handleCancelEdit = () => {
    setEditingCommentId(null);
    setEditingBody('');
  };

  const handleSaveEdit = async (commentId: string) => {
    if (!accessToken || !editingBody.trim()) return;
    setIsSavingEdit(true);
    try {
      const updated = await updateComment(commentId, { body: editingBody.trim() }, accessToken);
      setComments((prev) => prev.map((c) => (c.id === commentId ? updated : c)));
      handleCancelEdit();
    } catch (e: unknown) {
      Alert.alert('수정 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSavingEdit(false);
    }
  };

  const handleDeleteComment = (commentId: string) => {
    Alert.alert('댓글 삭제', '이 댓글을 삭제하시겠습니까?', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          if (!accessToken) return;
          try {
            await deleteComment(commentId, accessToken);
            setComments((prev) => prev.filter((c) => c.id !== commentId));
            setPost((prev) => (prev ? { ...prev, commentCount: Math.max(0, prev.commentCount - 1) } : prev));
          } catch (e: unknown) {
            Alert.alert('삭제 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
          }
        },
      },
    ]);
  };
```

- [ ] **Step 4: `renderItem`을 인라인 편집/작성자 전용 버튼 포함하도록 교체**

`mobile/src/screens/PostDetailScreen.tsx:149-154`의:

```tsx
          renderItem={({ item }) => (
            <View style={styles.commentRow}>
              <Text style={styles.commentAuthor}>{item.author.nickname}</Text>
              <Text style={styles.commentBody}>{item.body}</Text>
            </View>
          )}
```

를 아래로 교체:

```tsx
          renderItem={({ item }) => {
            const isMine = user != null && user.id === item.author.id;

            if (editingCommentId === item.id) {
              return (
                <View style={styles.commentRow}>
                  <Text style={styles.commentAuthor}>{item.author.nickname}</Text>
                  <TextInput
                    style={styles.commentEditInput}
                    value={editingBody}
                    onChangeText={setEditingBody}
                    multiline
                    autoFocus
                  />
                  <View style={styles.commentActionsRow}>
                    <TouchableOpacity onPress={handleCancelEdit} activeOpacity={0.7} disabled={isSavingEdit}>
                      <Text style={styles.commentActionLabel}>취소</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      onPress={() => handleSaveEdit(item.id)}
                      activeOpacity={0.7}
                      disabled={isSavingEdit || !editingBody.trim()}
                    >
                      {isSavingEdit ? (
                        <ActivityIndicator size="small" color={Colors.primary} />
                      ) : (
                        <Text style={styles.commentActionLabel}>저장</Text>
                      )}
                    </TouchableOpacity>
                  </View>
                </View>
              );
            }

            return (
              <View style={styles.commentRow}>
                <Text style={styles.commentAuthor}>{item.author.nickname}</Text>
                <Text style={styles.commentBody}>{item.body}</Text>
                {isMine && (
                  <View style={styles.commentActionsRow}>
                    <TouchableOpacity onPress={() => handleStartEdit(item)} activeOpacity={0.7}>
                      <Text style={styles.commentActionLabel}>수정</Text>
                    </TouchableOpacity>
                    <TouchableOpacity onPress={() => handleDeleteComment(item.id)} activeOpacity={0.7}>
                      <Text style={styles.commentActionLabel}>삭제</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            );
          }}
```

- [ ] **Step 5: 스타일 추가**

`mobile/src/screens/PostDetailScreen.tsx`의 `commentBody` 스타일 정의(`marginTop: 2,` 다음 `},`) 바로 뒤에 추가:

```ts
  commentEditInput: {
    fontSize: 13,
    color: Colors.gray900,
    marginTop: 4,
    borderWidth: 1,
    borderColor: Colors.gray100,
    borderRadius: 6,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  commentActionsRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 6,
  },
  commentActionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.gray500,
  },
```

- [ ] **Step 6: 타입 검사**

Run: `cd mobile && npx tsc --noEmit`
Expected: `TypeScript: No errors found`

- [ ] **Step 7: Commit**

```bash
git add mobile/src/screens/PostDetailScreen.tsx
git commit -m "feat(mobile): 게시글 상세에 댓글 인라인 수정/삭제 UI 추가"
```

---

### Task 3: 수동 검증 + 구현 기록 문서

**Files:**
- Create: `mobile/docs/implementations/comment-edit-delete.md`

**Interfaces:**
- Consumes: Task 1, Task 2에서 만든 전체 기능
- Produces: 없음 (플랜의 마지막 태스크)

- [ ] **Step 1: 개발 서버 기동 및 번들 확인**

```bash
cd mobile && npx expo start &
sleep 5 && curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"
```

Expected: `200`

- [ ] **Step 2: 시뮬레이터/실기기에서 수동 테스트**

아래 체크리스트를 실제로 확인한다:

- [ ] 본인이 작성한 댓글에는 본문 아래 "수정"/"삭제" 텍스트 버튼이 보인다
- [ ] 타인이 작성한 댓글에는 "수정"/"삭제" 버튼이 보이지 않는다
- [ ] "수정" 탭 → 해당 댓글만 그 자리에서 `TextInput`으로 바뀌고 기존 내용이 채워져 있다
- [ ] 내용을 비우면 "저장" 버튼이 비활성화된다
- [ ] 내용을 바꾸고 "저장" → 저장 중 스피너 표시 → 완료 후 목록에 반영되고 편집 모드가 종료된다
- [ ] "취소" 탭 → 편집 모드가 종료되고 원래 내용이 그대로 남는다 (서버 호출 없음)
- [ ] "삭제" 탭 → "댓글 삭제" 확인 다이얼로그가 뜬다
- [ ] 다이얼로그에서 "삭제" 선택 → 댓글이 목록에서 사라지고 상단 "댓글 N" 카운트가 1 감소한다
- [ ] 다이얼로그에서 "취소" 선택 → 아무 변화 없음
- [ ] 네트워크를 끊거나 만료된 토큰 상태에서 수정/삭제 시도 → 실패 Alert가 뜨고 목록은 그대로 유지된다

- [ ] **Step 3: 구현 기록 문서 작성**

`mobile/docs/implementations/comment-edit-delete.md` 신규 생성 (Step 1~2 결과를 실제로 채워서):

```markdown
# 게시글 댓글 수정/삭제 기능

## 배경

`docs/api-contract.md`/`backend/`에는 댓글 PATCH/DELETE가 이미 구현돼 있었으나 모바일에
수정/삭제 UI가 없었다. 설계 문서: `docs/superpowers/specs/2026-07-07-mobile-comment-edit-delete-design.md`

## 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| 버튼 노출 | 본인 댓글에 항상 보이는 텍스트 버튼 | 케밥 메뉴/롱프레스보다 발견성이 높음 |
| 수정 UX | 인라인 편집 (제자리 TextInput 전환) | 화면 이동 없이 빠르게 수정 |
| 삭제 UX | Alert 확인 후 하드 삭제 | 백엔드가 하드 삭제(204)이므로 모바일도 동일하게 목록에서 제거 |

## 구현 내용

- `mobile/src/types/index.ts`: `UpdateCommentRequestBody` 추가
- `mobile/src/services/commentApi.ts`: `updateComment`, `deleteComment` 추가
- `mobile/src/screens/PostDetailScreen.tsx`: `isMine` 체크, 인라인 편집 state/핸들러,
  `renderItem` 분기, 스타일 추가

## 테스트 결과

### TypeScript 검사

```
npx tsc --noEmit → (결과 채우기)
```

### 수동 테스트 체크리스트

(Task 3 Step 2 체크리스트 결과를 그대로 옮겨 적는다)

## 변경 파일 요약

| 파일 | 변경 내용 |
|------|----------|
| mobile/src/types/index.ts | UpdateCommentRequestBody 추가 |
| mobile/src/services/commentApi.ts | updateComment, deleteComment 추가 |
| mobile/src/screens/PostDetailScreen.tsx | 댓글 인라인 수정/삭제 UI 추가 |
```

- [ ] **Step 4: Commit**

```bash
git add mobile/docs/implementations/comment-edit-delete.md
git commit -m "docs(mobile): 댓글 수정/삭제 기능 구현 기록 추가"
```
