# 코스 댓글 + 러닝 인증 이미지 첨부

## 배경

공개(`PUBLIC`) 코스 상세 화면(`CourseDetailScreen`)에서 사용자가 댓글을 작성하고, 러닝 인증
사진을 첨부할 수 있게 구현했습니다. 백엔드 API 계약은 `docs/api-contract.md`의
"Course Comment APIs" 섹션을 그대로 따릅니다.

## 설계 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| 노출 조건 | `course.visibility === 'PUBLIC'`일 때만 댓글 섹션 렌더링 | 문서상 PRIVATE 코스는 댓글 불가 |
| 이미지 선택 | `expo-image-picker`의 `launchImageLibraryAsync` (신규 설치) | 카메라 촬영이 아닌 갤러리 선택으로 MVP 범위를 최소화 (YAGNI) |
| 이미지 전송 방식 | `multipart/form-data` (`FormData`) | API 계약이 multipart를 요구 — JSON으로 base64 인코딩하지 않음 |
| 목록 컴포넌트 분리 | `CourseCommentItem.tsx` 별도 파일 | "많은 작은 파일" 원칙 — CourseDetailScreen 비대화 방지 |
| 삭제 UX | Optimistic UI + 실패 시 롤백 | 기존 좋아요/북마크 처리와 동일한 패턴 유지 |
| 레이아웃 변경 | 지도 아래 영역을 `ScrollView`로 감쌈, 지도 높이는 `flex: 1` → 고정 `260` | 댓글 목록이 늘어나도 스크롤 가능하게 하기 위해 화면 하단부 구조를 재구성 |

## 변경/신규 파일

| 파일 | 종류 | 내용 |
|------|------|------|
| `src/types/index.ts` | 수정 | `PublicProfile`, `CourseComment` 타입 추가 (`docs/data-model.md`와 1:1) |
| `src/services/courseCommentApi.ts` | 신규 | `getCourseComments`, `createCourseComment`, `updateCourseComment`, `deleteCourseComment` |
| `src/components/CourseCommentItem.tsx` | 신규 | 댓글 1건 표시(작성자, 본문, 이미지 썸네일, 본인 댓글이면 삭제 버튼) |
| `src/screens/CourseDetailScreen.tsx` | 수정 | 댓글 목록/작성 폼/이미지 첨부 UI 추가, 하단 영역 `ScrollView`화 |
| `app.json` | 수정 | `expo-image-picker` config plugin 등록, iOS `NSPhotoLibraryUsageDescription`, Android `READ_MEDIA_IMAGES` 권한 추가 |
| `package.json` / `package-lock.json` | 수정 | `expo-image-picker@17.0.11` 추가 (`npx expo install`로 설치, `mobile/AGENTS.md` 규칙 준수) |

## 구현 내용

### 타입 (`src/types/index.ts`)

```ts
export interface PublicProfile {
  id: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
}

export interface CourseComment {
  id: string;
  courseId: string;
  author: PublicProfile;
  body: string;
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}
```

### API 서비스 (`src/services/courseCommentApi.ts`)

- `getCourseComments(courseId, accessToken?, params?)` — GET, Optional 인증
- `createCourseComment(courseId, body, image, accessToken)` — POST multipart
  (`{ uri, name, type }` 형태의 이미지 객체를 `FormData`에 그대로 append — React Native
  fetch 폴리필이 이 형태를 파일 파트로 인식하는 표준 패턴)
- `updateCourseComment(courseId, commentId, { body?, image?, removeImage? }, accessToken)` — PATCH multipart
- `deleteCourseComment(courseId, commentId, accessToken)` — DELETE

기존 `bookmarkApi.ts`/`likeApi.ts`와 동일하게 `EXPO_PUBLIC_API_BASE_URL` 사용, 실패 시
`parseApiErrorMessage`로 에러 메시지를 파싱합니다.

### CourseDetailScreen.tsx

- `course.visibility === 'PUBLIC'`일 때만 `useFocusEffect`에서 `loadComments()` 호출.
- 작성 폼: `TextInput`(본문) + 이미지 첨부 버튼(`handlePickCommentImage` → 권한 요청 후
  `launchImageLibraryAsync`) + 전송 버튼(`handleSubmitComment`).
- 전송 성공 시 새 댓글을 목록 맨 앞에 추가(재조회 없이 즉시 반영).
- 삭제(`handleDeleteComment`)는 확인 `Alert` 후 Optimistic 제거, 실패 시 롤백 + 에러 Alert.
- 비로그인 사용자가 첨부/작성을 시도하면 `useAuthGate`의 `requireAuth()`로 로그인 유도.

## 테스트

### TypeScript 검사

```
cd mobile && npx tsc --noEmit
→ TypeScript: No errors found
```

### 수동 테스트 체크리스트 (백엔드 연동 후 실기기/시뮬레이터에서 확인 필요)

- [ ] PUBLIC 코스 상세 진입 시 댓글 섹션 노출, PRIVATE 코스에서는 미노출
- [ ] 텍스트만으로 댓글 작성 → 목록에 즉시 반영
- [ ] 이미지 첨부 버튼 → 갤러리 접근 권한 요청 다이얼로그 표시
- [ ] 이미지 선택 후 전송 → 댓글에 썸네일 표시
- [ ] 본인 댓글에만 삭제 버튼 노출, 삭제 확인 Alert → 목록에서 제거
- [ ] 네트워크 실패 시 삭제 롤백 + 에러 Alert 표시
- [ ] 비로그인 상태에서 작성/이미지 첨부 시도 → 로그인 유도 모달

## 알려진 한계 / 후속 과제

- 댓글 수정(PATCH) UI는 이번 범위에 포함하지 않았습니다 (API는 구현되어 있으나 모바일 화면에는
  삭제만 노출). 필요해지면 `updateCourseComment`를 그대로 재사용해 수정 폼을 추가할 수 있습니다.
- 댓글 목록 페이지네이션(다음 페이지 로드) UI는 아직 없고, 첫 페이지만 표시합니다.

## 추가 구현: 대댓글(2단계 답글)

백엔드는 이미 `parentCommentId`, `GET /comments/{commentId}/replies`, `replyCount`까지 구현되어
있었지만(`CourseComment.java`, `CourseCommentController.java`, `V8__add_parent_comment_id_to_course_comments.sql`),
모바일 쪽 `CourseDetailScreen`은 목록/작성 폼에 답글 상태를 전혀 연결하지 않고 있었습니다
(`CourseCommentItem`에 존재하지도 않는 `isMine` prop을 넘기는 버그 포함). 이번에 아래를 연결했습니다.

- `CourseDetailScreen`에 답글 관련 상태 추가: `replyTarget`(답글 대상 댓글), `repliesByParentId`
  (댓글 id별 답글 목록 캐시), `expandedReplyIds`, `loadingReplyIds`.
- `handleReply` — "답글 달기" 클릭 시 `replyTarget` 설정, 비로그인이면 `requireAuth()`로 로그인 유도.
- `handleToggleReplies` — 최초 펼칠 때만 `getCourseCommentReplies` 호출, 이후엔 캐시 재사용.
- `handleSubmitComment` — `replyTarget`이 있으면 `createCourseComment`에 `parentCommentId`를 넘기고,
  성공 시 해당 댓글의 `replyCount`를 낙관적으로 +1, 답글 목록을 자동으로 펼침.
- `handleDeleteComment(commentId, parentCommentId)` — 대댓글 삭제 시 `repliesByParentId`에서
  제거하고 부모 댓글의 `replyCount`를 -1 (실패 시 둘 다 롤백). 이를 위해
  `CourseCommentItem`의 `onDelete` 시그니처를 `(commentId, parentCommentId)`로 확장.
- 답글 작성 폼에 "OO님에게 답글 작성 중" 표시 바 + 취소 버튼 추가.
- 대댓글에는 다시 답글을 달 수 없음 (백엔드 검증과 동일하게 `isReply`일 때 "답글 달기" 버튼 미노출).

### 수동 테스트 체크리스트 (추가)

- [ ] 최상위 댓글에서 "답글 달기" → 입력창에 "OO님에게 답글 작성 중" 표시
- [ ] 답글 등록 → 부모 댓글의 "답글 N개 보기"에 반영, 목록 자동 펼침
- [ ] "답글 N개 보기" 클릭 → 서버에서 답글 목록 로드, 다시 클릭 시 캐시 재사용(재요청 없음)
- [ ] 본인 답글 삭제 → 답글 목록에서 제거되고 부모의 답글 개수 감소
- [ ] 대댓글(답글)에는 "답글 달기" 버튼이 보이지 않음

## 추가 수정: 코스 예상 시간이 접속 사용자 속도 기준으로 통일되지 않던 문제

`Course.estimatedDurationSeconds`는 코스 **생성 시점**에 계산되어 저장되는 값이라 코스
작성자의 페이스를 반영합니다. `SavedRoutesScreen`은 이미 `user.runningPaceSecPerKm`으로
클라이언트에서 다시 계산해서 보여주고 있었지만, `CourseDetailScreen`은 저장된
`course.estimatedDurationSeconds`를 그대로 `RouteStatsBar`에 넘기고 있어 두 화면의 표시 시간이
서로 달랐습니다(예: 3.2km 코스가 북마크 목록에서는 26분, 상세에서는 20분).

`CourseDetailScreen.tsx`에서 `RouteStatsBar`에 넘기는 `estimatedDurationSeconds`를
`Math.round((course.distanceMeters / 1000) * userPace)`로 변경해 `SavedRoutesScreen`과 동일하게
접속한 사용자의 페이스(`user?.runningPaceSecPerKm ?? DEFAULT_PACE_SEC_PER_KM`) 기준으로 계산하도록
통일했습니다. API 응답 필드나 데이터 모델은 바뀌지 않았고(`estimatedDurationSeconds`는 여전히
서버가 계산한 값 그대로 내려옴), 화면 표시 로직만 수정했으므로 `docs/api-contract.md`,
`docs/data-model.md` 변경은 필요 없습니다.
