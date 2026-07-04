# 코스 좋아요 기능 구현

## 목표

`CourseDetailScreen`에서 코스에 좋아요/취소를 할 수 있는 하트 버튼을 추가한다.
비로그인 사용자는 버튼을 볼 수 있지만 누르면 로그인 요청 모달이 뜬다.

## API

```
PUT  /api/likes/courses/{courseId}   → 좋아요 등록 (중복 무시)
DELETE /api/likes/courses/{courseId} → 좋아요 취소 (없을 때도 성공)
```

응답: `{ targetType, targetId, liked, likeCount }`

## 구현 위치

- `services/likeApi.ts` — `putLike(targetType, targetId, accessToken)` / `deleteLike(...)` 신규 생성
- `types/index.ts` — `LikeResponse` 타입 추가
- `screens/CourseDetailScreen.tsx` — 좋아요 버튼 + likeBar UI 추가

## 상태 관리

`course.likedByMe`, `course.likeCount`를 각각 별도 `useState`로 분리:
- `useFocusEffect`에서 course 로드 시 초기화
- 낙관적 업데이트(optimistic update): 버튼 누르면 즉시 UI 토글 → API 호출 → 서버 응답으로 동기화
- API 실패 시 원래 상태로 롤백

## 인증

`useAuthGate().requireAuth()` 호출 — 미로그인이면 로그인 모달 표시 후 `false` 반환.

## UI

RouteStatsBar 하단에 `likeBar` 행 추가:
- `heart` (채워진, danger 색) ↔ `heart-outline` (회색) 토글
- 숫자 카운트 표시

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200
