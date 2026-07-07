# 게시글 댓글 수정/삭제 기능

## 배경

`docs/api-contract.md`/`backend/`에는 댓글 PATCH/DELETE가 이미 구현돼 있었으나 모바일에
수정/삭제 UI가 없었다. 설계 문서: `docs/superpowers/specs/2026-07-07-mobile-comment-edit-delete-design.md`,
플랜: `docs/superpowers/plans/2026-07-07-mobile-comment-edit-delete.md`.

## 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| 버튼 노출 | 본인 댓글에 항상 보이는 텍스트 버튼 | 케밥 메뉴/롱프레스보다 발견성이 높음 |
| 수정 UX | 인라인 편집 (제자리 TextInput 전환) | 화면 이동 없이 빠르게 수정 |
| 삭제 UX | Alert 확인 후 하드 삭제 | 백엔드가 하드 삭제(204)이므로 모바일도 동일하게 목록에서 제거 |

## 구현 내용

- `mobile/src/types/index.ts`: `UpdateCommentRequestBody` 추가 (`202-204`).
- `mobile/src/services/commentApi.ts`: `updateComment`(`48-71`, `PATCH /api/comments/{commentId}`),
  `deleteComment`(`74-`, `DELETE /api/comments/{commentId}`) 추가. 둘 다 기존 `createComment`와
  같은 패턴으로 `Authorization: Bearer` 헤더와 `parseApiErrorMessage`를 재사용한다.
- `mobile/src/screens/PostDetailScreen.tsx`: `isMine` 체크(`197`), 인라인 편집 state
  (`editingCommentId`, `editingBody`, `isSavingEdit` — `41-43`)와 핸들러
  (`handleStartEdit`/`handleCancelEdit`/`handleSaveEdit`/`handleDeleteComment` — `95-137`),
  `renderItem` 편집 모드 분기(`199-228`), 스타일(`commentEditInput`, `commentActionsRow`,
  `commentActionLabel` 등) 추가.

## 테스트 결과

### TypeScript 검사

```
$ npx tsc --noEmit
(출력 없음, exit code 0 — 에러 0건)
```

Task 1, 2 커밋(`cc242ef`)까지 반영된 최종 상태 기준으로 이 문서 작성 시점에 재실행해 확인했다.

### Expo 번들 확인

```
$ npx expo start &
$ curl -s -o /dev/null -w "HTTP_STATUS:%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"
HTTP_STATUS:200
```

응답 본문도 실제 Metro 번들(약 740만 바이트, `__BUNDLE_START_TIME__` 등 정상 헤더 포함)임을
확인했다. 확인 후 `expo start` 프로세스는 종료했다.

### 수동 테스트 체크리스트

**이 환경에는 iOS 시뮬레이터/Android 에뮬레이터/실기기가 없어서, 아래 10개 항목은 사람이
기기에서 직접 탭해보는 대화형 확인을 거치지 못했다.** 대신 각 항목이 요구하는 동작을 만드는
코드 배선이 실제로 커밋되어 있는지 정적 코드 확인(코드 레벨 cross-check)으로 대체했다.
"코드가 이 동작을 만드는 배선을 갖고 있음"과 "기기에서 실제로 그렇게 동작하는 걸 확인함"은
다른 주장이며, 아래는 전자만 확인한 것이다.

| # | 항목 | 상태 | 근거 |
|---|------|------|------|
| 1 | 본인이 작성한 댓글에는 본문 아래 "수정"/"삭제" 텍스트 버튼이 보인다 | 배선 확인 | `mobile/src/screens/PostDetailScreen.tsx:197` `const isMine = user != null && user.id === item.author.id;`. `234-243`에서 `{isMine && (<View style={styles.commentActionsRow}>...수정...삭제...</View>)}`로 `isMine`이 true일 때만 두 버튼을 렌더링 |
| 2 | 타인이 작성한 댓글에는 "수정"/"삭제" 버튼이 보이지 않는다 | 배선 확인 | 위와 동일 조건의 반대 경로 — `item.author.id !== user.id`(또는 비로그인 `user == null`)이면 `isMine`이 `false`가 되어 `234-243`의 `View` 자체가 렌더링되지 않는다 |
| 3 | "수정" 탭 → 해당 댓글만 그 자리에서 `TextInput`으로 바뀌고 기존 내용이 채워져 있다 | 배선 확인 | `handleStartEdit`(`95-98`)이 `setEditingCommentId(comment.id)`와 `setEditingBody(comment.body)`를 호출. `renderItem`(`199-228`)은 `editingCommentId === item.id`일 때만 `TextInput`(`203-209`, `value={editingBody}`)을 렌더링하므로 다른 댓글은 영향받지 않고, 편집 대상 댓글만 기존 `body`가 채워진 입력창으로 바뀐다 |
| 4 | 내용을 비우면 "저장" 버튼이 비활성화된다 | 배선 확인 | `217` `disabled={isSavingEdit || !editingBody.trim()}` — `editingBody`가 빈 문자열(공백만 있어도 `trim()` 후 falsy)이면 `disabled`가 `true` |
| 5 | 내용을 바꾸고 "저장" → 저장 중 스피너 표시 → 완료 후 목록에 반영되고 편집 모드가 종료된다 | 배선 확인 | `handleSaveEdit`(`105-117`)이 `setIsSavingEdit(true)` 후 `updateComment` 호출, 성공하면 `setComments((prev) => prev.map((c) => (c.id === commentId ? updated : c)))`(`110`)로 목록 갱신, `handleCancelEdit()`(`111`)로 편집 모드 종료, `finally`에서 `setIsSavingEdit(false)`(`115`). 저장 버튼은 `isSavingEdit`이 `true`인 동안 `ActivityIndicator`를(`219-220`), 아니면 "저장" 텍스트를(`222`) 렌더링 |
| 6 | "취소" 탭 → 편집 모드가 종료되고 원래 내용이 그대로 남는다 (서버 호출 없음) | 배선 확인 | `handleCancelEdit`(`100-103`)은 `setEditingCommentId(null)`/`setEditingBody('')`만 호출하고 `updateComment`나 다른 API 호출이 없다. `comments` state 자체를 건드리지 않으므로 원래 `item.body`가 그대로 렌더링된다 |
| 7 | "삭제" 탭 → "댓글 삭제" 확인 다이얼로그가 뜬다 | 배선 확인 | `handleDeleteComment`(`119-137`)가 `Alert.alert('댓글 삭제', '이 댓글을 삭제하시겠습니까?', [...])`(`120`)를 호출 |
| 8 | 다이얼로그에서 "삭제" 선택 → 댓글이 목록에서 사라지고 상단 "댓글 N" 카운트가 1 감소한다 | 배선 확인 | Alert의 두 번째 버튼(`122-134`, `style: 'destructive'`) `onPress`가 `deleteComment` 성공 시 `setComments((prev) => prev.filter((c) => c.id !== commentId))`(`129`)로 목록에서 제거. 헤더는 `댓글 {comments.length}`(`193`)로 렌더링되므로 배열이 줄면 카운트도 즉시 1 감소한다. (`setPost`로 `commentCount`도 함께 갱신하지만(`130`), 실제 헤더 표시는 `comments.length` 기준이라 이 배선이 카운트 감소를 보장한다) |
| 9 | 다이얼로그에서 "취소" 선택 → 아무 변화 없음 | 배선 확인 | Alert의 첫 번째 버튼(`121`, `{ text: '취소', style: 'cancel' }`)은 `onPress`가 없어 아무 상태 변경도 발생하지 않는다 |
| 10 | 네트워크를 끊거나 만료된 토큰 상태에서 수정/삭제 시도 → 실패 Alert가 뜨고 목록은 그대로 유지된다 | 배선 확인 | `handleSaveEdit`의 `catch`(`112-114`)는 `Alert.alert('수정 실패', ...)`만 호출하고 `setComments`는 `try` 블록 안에서 성공했을 때만 실행되므로(`109-111`) 실패 시 목록은 그대로다. `handleDeleteComment`의 `onPress` `catch`(`131-133`)도 동일하게 `Alert.alert('삭제 실패', ...)`만 호출하고 `setComments` 필터는 실행되지 않는다. `commentApi.ts`의 `updateComment`/`deleteComment`(`48-`, `74-`)는 `response.ok`가 아니면 `parseApiErrorMessage`로 만든 `Error`를 던지고, 네트워크 자체가 끊긴 경우 `fetch`가 던지는 예외도 같은 `catch (e: unknown)`에서 `e instanceof Error ? e.message : ...`로 처리되어 동일하게 실패 Alert 경로를 탄다 |

**아직 사람이 실기기/시뮬레이터에서 대화형으로 눌러봐야 확인되는 것 (pending)**: 위 10개 항목
전체가 실제 런타임에서 시각적으로/제스처로 기대대로 동작하는지 (키보드 포커스 이동, 스피너
타이밍, Alert 다이얼로그의 실제 표시 문구/버튼 배치, 실기기에서 네트워크를 실제로 끊거나 토큰을
만료시켰을 때의 동작 등 눈으로만 확인 가능한 부분 포함). 이 문서 작성 시점에는 코드 배선만
확인했고, 브랜치를 완전히 검증됐다고 보려면 이 대화형 패스가 반드시 필요하다.

## 변경 파일 요약

| 파일 | 변경 내용 |
|------|----------|
| mobile/src/types/index.ts | UpdateCommentRequestBody 추가 |
| mobile/src/services/commentApi.ts | updateComment, deleteComment 추가 |
| mobile/src/screens/PostDetailScreen.tsx | 댓글 인라인 수정/삭제 UI 추가 |
