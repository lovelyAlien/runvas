# 코스 상세 화면 후기 작성/목록 버튼

## 목표

"내 코스"/"북마크된 코스" 상세 화면(`CourseDetailScreen`)에서 바로 후기를 작성하거나 해당
코스의 후기 목록을 볼 수 있게 한다. `MapScreen`의 코스 탐색 시트에 있는 동일한 버튼 패턴을
재사용한다.

## 변경

### CourseDetailScreen (`mobile/src/screens/CourseDetailScreen.tsx`)
- 이미 있던 `useAuthGate()`의 `requireAuth`를 후기 작성 버튼의 인증 게이트로 재사용
- 지도 우측에 원형 FAB 버튼 2개 추가 (`MapScreen.tsx`의 FAB 스타일 복제, 공용 컴포넌트로
  추출하지는 않음)
  - `create-outline` → `requireAuth()` 통과 후 `PostCreate`로 이동 (`attachedCourseId`,
    `attachedCourseTitle` = 현재 코스)
  - `list-outline` → 인증 없이 `CourseBoard`로 이동 (`courseId`, `courseTitle` = 현재 코스)
- 이 화면은 코스 로드 실패 시 이미 `goBack()`하므로, 버튼이 렌더링되는 시점엔 `course`가 항상
  존재 — `MapScreen`과 달리 버튼에 `disabled` 상태가 없다 (항상 활성)

## API/데이터 모델 변경

없음. 기존 `PostCreate`/`CourseBoard` 라우트와 파라미터를 그대로 재사용.

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200 확인
- 실기기/시뮬레이터: 저장한 코스 → 상세 화면 진입 시 버튼 즉시 활성 → 후기 작성(로그인 게이트,
  제목 prefill) → 후기 목록(코스별 필터링) 확인
