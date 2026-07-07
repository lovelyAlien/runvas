# 게시글 댓글 수정/삭제 (모바일) 설계

작성일: 2026-07-07
관련 문서: `docs/api-contract.md` §Comment APIs (`PATCH /comments/{commentId}`, `DELETE /comments/{commentId}`),
`docs/data-model.md` §Comment, `mobile/CLAUDE.md`, `mobile/AGENTS.md`

## 배경

`docs/api-contract.md`에는 댓글 수정(`PATCH /comments/{commentId}`)/삭제(`DELETE
/comments/{commentId}`) 계약이 이미 정의돼 있고, `backend/`의 `CommentController`/`CommentService`도
작성자 본인 검증까지 포함해 이미 구현돼 있다 (`docs/`, `backend/` 변경 없음).

빠진 것은 모바일뿐이다. `mobile/src/services/commentApi.ts`에는 `getComments`/`createComment`만
있고, `PostDetailScreen.tsx`의 댓글 렌더링에는 수정/삭제 UI가 없다. 이번 작업은 **모바일 전용**
변경이다.

## 목표

1. 게시글 상세 화면에서 본인이 작성한 댓글에 "수정"/"삭제" 텍스트 버튼이 항상 보인다.
2. "수정" 클릭 시 해당 댓글이 그 자리에서 인라인으로 편집 가능한 입력창으로 바뀐다 (다른 화면
   이동 없음).
3. "삭제" 클릭 시 확인 다이얼로그 후 댓글이 삭제되고 목록/게시글 댓글 수가 갱신된다.

## 범위 밖

- 백엔드/문서 변경 — 이미 계약과 구현이 완료돼 있음.
- 대댓글(reply) — Post 댓글에는 대댓글 개념이 없다 (Course 댓글과 다른 모델).
- 댓글 삭제 시 소프트 삭제("삭제된 댓글입니다" 표기) — 백엔드가 하드 삭제(204, row 제거)이므로
  모바일도 목록에서 완전히 제거한다.

## 타입 추가 (`mobile/src/types/index.ts`)

`docs/api-contract.md` PATCH 요청 본문과 1:1 대응, 기존 `CreateCommentRequestBody` 바로 아래에 추가:

```ts
export interface UpdateCommentRequestBody {
  body: string;
}
```

## `mobile/src/services/commentApi.ts` 함수 추가

기존 `createComment`와 동일한 스타일(`API_BASE_URL` 체크, `Authorization` 헤더,
`parseApiErrorMessage`로 에러 처리)로 두 함수를 추가한다:

```ts
export async function updateComment(
  commentId: string,
  body: UpdateCommentRequestBody,
  accessToken: string
): Promise<Comment> {
  // PATCH `${API_BASE_URL}/api/comments/${commentId}`
  // 응답: { comment: Comment } → comment 반환
}

export async function deleteComment(commentId: string, accessToken: string): Promise<void> {
  // DELETE `${API_BASE_URL}/api/comments/${commentId}`
  // 204 No Content — response.ok만 확인, 본문 파싱 없음
}
```

## `PostDetailScreen.tsx` 변경

### State 추가

```ts
const [editingCommentId, setEditingCommentId] = useState<string | null>(null);
const [editingBody, setEditingBody] = useState('');
const [isSavingEdit, setIsSavingEdit] = useState(false);
```

### 댓글 행 렌더링 (`renderItem`)

- `isMine = user != null && user.id === item.author.id` (기존 `CourseCommentItem.tsx`의
  `isMine` 판정 로직과 동일한 패턴).
- `editingCommentId === item.id`일 때: 닉네임 아래에 `TextInput`(`editingBody`, autoFocus) +
  "저장"/"취소" 텍스트 버튼. "저장"은 `editingBody.trim()`이 빈 문자열이거나 `isSavingEdit`이 true면
  비활성화되고, `isSavingEdit`이 true인 동안은 기존 `isSubmittingComment`와 동일하게 텍스트 대신
  `ActivityIndicator`를 보여준다.
- 그 외: 기존 닉네임/본문 표시 그대로, `isMine`이면 그 아래에 "수정"/"삭제" 텍스트 버튼 행 추가
  (본문 아래, 항상 노출 — 브레인스토밍에서 확정된 방식).

### 핸들러

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

(패턴은 `CourseDetailScreen.tsx`의 `handleDeleteComment` Alert 확인 구조를 그대로 따른다.)

## 에러 처리

- 새로운 에러 UI 패턴을 도입하지 않는다 — 화면에 이미 있는 `Alert.alert(title, message)` 컨벤션
  그대로 사용.
- 403(작성자 아님)/404(댓글 없음)도 별도 코드 분기 없이 `parseApiErrorMessage`가 반환하는 메시지를
  그대로 표시한다 (다른 API 실패 처리와 동일한 수준).

## 검증 계획

- `npx tsc --noEmit`
- `npx expo start` 백그라운드 기동 후 `curl` bundle 200 확인
- 실기기/시뮬레이터: 본인 댓글에서 수정 → 인라인 편집 → 저장 → 반영 확인, 취소 → 원래 텍스트로
  복귀 확인, 삭제 → 확인 다이얼로그 → 목록에서 제거 + 댓글 수 감소 확인. 타인 댓글에는 수정/삭제
  버튼이 보이지 않는지 확인.
- 완료 후 `mobile/docs/implementations/comment-edit-delete.md`에 설계 요약 기록
