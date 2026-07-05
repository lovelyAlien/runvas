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

- `npx tsc --noEmit` 통과 (에러 0건, 재확인 완료).
- `npx expo start`를 백그라운드로 띄운 뒤
  `curl ".../index.bundle?platform=ios&dev=true"` → `HTTP_STATUS:200` 확인. 응답 본문도 실제
  Metro 번들(약 16만 바이트, `__BUNDLE_START_TIME__` 등 정상 헤더 포함)임을 확인했다.
- **이 환경에는 iOS 시뮬레이터/Android 에뮬레이터/실기기가 없어서, 아래 11개 항목은 사람이
  기기에서 직접 탭해보는 대화형 확인(Step 2)을 거치지 못했다.** 대신 각 항목이 요구하는 동작을
  만드는 코드 배선이 실제로 커밋되어 있는지 정적 코드 확인으로 대체했다. "코드가 이 동작을 만드는
  배선을 갖고 있음"과 "기기에서 실제로 그렇게 동작하는 걸 확인함"은 다른 주장이며, 아래는 전자만
  확인한 것이다. 사람이 기기에서 한 번 더 눌러보는 패스가 필요하다.

  | # | 항목 | 상태 | 근거 |
  |---|------|------|------|
  | 1 | 코스 선택 시 "게시글 작성"/"게시판" 버튼이 (비활성 상태로) 나타나고 선택 즉시 활성화 | 배선 확인 | `mobile/src/screens/MapScreen.tsx:370-383` — `isCourseSheetOpen`이면 버튼 컨테이너가 렌더링되고, 각 `FAB`은 `disabled={!selectedCourseDetail}`. `handleSelectCourse`(`MapScreen.tsx:192-203`)가 코스 선택 시 `selectedCourseDetail`을 채우므로 선택 즉시 `disabled`가 `false`로 바뀌는 배선은 존재 |
  | 2 | 코스를 선택하지 않은 상태(시트만 연 상태)에서는 두 버튼이 비활성 | 배선 확인 | 위와 동일 — `handleOpenCourseSearch`(`MapScreen.tsx:161-181`)가 `setSelectedCourseDetail(null)`로 시트를 열므로, 선택 전에는 `disabled=true` |
  | 3 | 비로그인 상태에서 "게시글 작성" → 로그인 유도 모달 → 로그인 후 작성 화면 이동 | 배선 확인 | `handlePressWritePost`(`MapScreen.tsx:212-218`)가 `requireAuth()`를 먼저 호출하고 실패 시(비로그인) 조기 리턴. `requireAuth`(`AuthContext.tsx:123-127`)는 `user`가 없으면 `setIsLoginModalVisible(true)`만 하고 `false`를 반환 — 로그인 모달 표출과 이동 차단 배선은 존재. 로그인 성공 후 실제로 같은 동작을 재시도해 작성 화면으로 넘어가는지는 대화형 확인 필요 |
  | 4 | "게시글 작성" 진입 시 제목이 `[후기] {코스명} - {오늘 날짜}` 형식으로 미리 채워지고 수정 가능 | 배선 확인 | `PostCreateScreen.tsx:23-26` `buildDefaultTitle` — `` `[후기] ${courseTitle} - ${formatDateYYYYMMDD(new Date())}` ``. `formatDateYYYYMMDD`(`mobile/src/utils/format.ts:24-29`)는 `YYYY.MM.DD` 형식 반환. `TextInput`(`PostCreateScreen.tsx:90-96`)의 `value`/`onChangeText`가 `title` state에 바인딩되어 수정 가능 |
  | 5 | 본문 입력 + 완료 → 게시글 상세 화면 이동, 첨부 코스 chip 노출 | 배선 확인 | `handleSubmit`(`PostCreateScreen.tsx:35-63`)이 `createPost` 성공 시 `navigation.replace('PostDetail', { postId: post.id })` 호출. `PostDetailScreen.tsx:126-137`이 `post.attachedCourseId`가 있으면 "첨부된 코스 보기" chip을 렌더링 |
  | 6 | "게시판" 버튼(로그인 무관)을 누르면 방금 쓴 글이 코스별 게시판 목록에 보임 | 배선 확인 | `handlePressCourseBoard`(`MapScreen.tsx:220-226`)는 `requireAuth()` 호출 없이 `selectedCourseDetail`만 확인 후 이동 — 로그인 여부 무관 배선 맞음. `CourseBoardScreen.tsx:30-44`가 `getPosts({ attachedCourseId: courseId })` 호출, `postApi.ts:48-59`의 `getPosts`가 `attachedCourseId`로 필터링하고 방금 `createPost`로 배열 앞에 추가된(`postApi.ts:86` `posts = [post, ...posts]`) 글을 포함해 반환 |
  | 7 | 하단 탭바 "게시판" 탭에서도 같은 글이 전역 목록에 보임 | 배선 확인 | `BoardScreen.tsx:26-40`이 `getPosts({})` 호출(필터 없음) → `postApi.ts`의 전역 `posts` 배열 전체 반환. 같은 in-memory 배열을 공유하므로 코스별 게시판에서 본 글과 동일 항목이 포함되는 배선 확인 |
  | 8 | 좋아요 버튼 → 하트 채워짐 + 카운트 +1, 다시 누르면 원복 | 배선 확인 | `handleToggleLike`(`PostDetailScreen.tsx:60-70`)가 `post.likedByMe`에 따라 `putLike`/`deleteLike` 분기 호출 후 `setPost`로 반영. `likeApi.ts:12-26`의 `putLike`/`deleteLike`가 각각 `likeCount`+1/-1과 `liked` true/false 반환. 하트 아이콘은 `post.likedByMe ? 'heart' : 'heart-outline'`(`PostDetailScreen.tsx:141`)로 즉시 바뀌는 배선 |
  | 9 | 댓글 작성 → 목록 즉시 반영, "댓글 N" 카운트 증가 | 배선 확인 | `handleSubmitComment`(`PostDetailScreen.tsx:72-95`)가 `createComment` 성공 시 `setComments((prev) => [...prev, comment])`와 `setPost` 콜백으로 `commentCount + 1` 반영. 헤더는 `댓글 {comments.length}`(`PostDetailScreen.tsx:147`)로 렌더링되어 배열 갱신과 동시에 카운트도 갱신되는 배선 |
  | 10 | 비로그인 상태로 상세 진입 시 댓글 입력창 대신 "로그인하고 댓글 남기기" 버튼 | 배선 확인 | `PostDetailScreen.tsx:159-180` — `user ? <댓글입력행> : <로그인 유도 버튼>` 조건부 렌더링. 버튼 텍스트가 정확히 "로그인하고 댓글 남기기"(`PostDetailScreen.tsx:178`) |
  | 11 | 앱 재시작 시 mock 시드(2개)만 남고 방금 쓴 글은 사라짐 | 배선 확인 | `postApi.ts:1-4` 상단 주석: "MOCK 구현... 앱을 재시작하면 아래 시드 데이터로 초기화된다 (의도된 동작)". `posts` 배열이 모듈 top-level `let` 변수(`postApi.ts:14`)로 선언되어 있어 앱(JS 런타임) 재시작 시 시드 2건으로 리셋되는 배선이 맞음 |

  **아직 사람이 실기기/시뮬레이터에서 대화형으로 눌러봐야 확인되는 것**: 위 11개 항목 전체가
  실제 런타임에서 시각적으로/제스처로 기대대로 동작하는지 (애니메이션 타이밍, 모달 표출 후 로그인
  플로우 복귀, 키보드 겹침, 실제 하트 채워짐 색상 등 눈으로만 확인 가능한 부분 포함). 이 문서
  작성 시점에는 코드 배선만 확인했고, 브랜치를 완전히 검증됐다고 보려면 이 대화형 패스가 반드시
  필요하다.
