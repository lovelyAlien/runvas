# 커뮤니티 게시글 백엔드 연동 (mock 제거)

## 배경

`mobile/docs/implementations/community-post-mobile-ui.md`에서 mock으로 구현했던 Post/Comment/Like
서비스를 실제 백엔드(`backend` community 패키지)와 연동했다.

## 변경 내용

- `postApi.ts`/`commentApi.ts`/`likeApi.ts`를 `courseApi.ts`와 동일한 실제 `fetch` 패턴으로 교체.
  in-memory 시드 데이터, `incrementCommentCount`/`updateLikeState` 헬퍼를 제거했다 (실제 서버가
  매 응답에 최신 값을 내려주므로 클라이언트 쪽 동기화가 필요 없다).
- `createPost`/`createComment`에서 `author` 파라미터를 제거했다 — 백엔드가 JWT로 작성자를
  판별해 응답에 채워준다. `PostCreateScreen.tsx`, `PostDetailScreen.tsx`의 호출부도 함께 수정했다.

## 검증

- `npx tsc --noEmit` 통과
- 실기기/시뮬레이터에서 게시글 작성 → 상세 진입 → 좋아요 토글 → 댓글 작성 → 앱 재시작 후에도
  데이터가 유지되는지 확인 (mock 시절과 달리 재시작해도 사라지지 않아야 함).

## 참고

- 관련 스펙: `docs/superpowers/specs/2026-07-05-community-post-backend-design.md`
