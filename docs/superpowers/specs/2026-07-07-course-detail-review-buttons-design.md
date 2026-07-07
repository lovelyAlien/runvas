# 코스 상세 화면 후기 작성/목록 버튼 설계

작성일: 2026-07-07
관련 문서: `docs/superpowers/specs/2026-07-05-community-post-mobile-ui-design.md` (Map 화면의 동일 패턴),
`docs/api-contract.md` §Post APIs, `mobile/CLAUDE.md`, `mobile/AGENTS.md`

## 배경

"내 코스"/"북마크된 코스" 둘 다 `SavedRoutesScreen.tsx`의 같은 목록(`getMyCourses`)에서 나오고,
행을 탭하면 동일하게 `CourseDetailScreen.tsx`로 이동한다. 즉 진입 경로가 두 개여도 실제로 손대야
하는 화면은 `CourseDetailScreen` 하나뿐이다.

`MapScreen.tsx`는 코스 탐색 시트에서 코스를 선택하면 "후기 작성"(`create-outline`)/"후기
목록"(`list-outline`) FAB 버튼이 활성화되어 `PostCreate`/`CourseBoard`로 이동하는 기능이 이미
있다 (`2026-07-05-community-post-mobile-ui-design.md` 참고). 반면 `CourseDetailScreen`에는 GPX
내보내기 버튼만 있고 후기 관련 버튼이 전혀 없다. 이번 작업은 `CourseDetailScreen`에도 같은 기능을
추가한다.

## 목표

1. `CourseDetailScreen`에서 코스 로드가 끝나면(로딩 실패 시 이미 `goBack()`하므로 화면이 렌더링될
   때는 항상 `course`가 존재) 지도 우측에 원형 FAB 버튼 2개가 항상 활성 상태로 보인다.
2. "후기 작성" 버튼 → `requireAuth()` 통과 후 `PostCreate`로 이동, `attachedCourseId`/
   `attachedCourseTitle`을 현재 코스 값으로 채운다.
3. "후기 목록" 버튼 → 인증 없이 `CourseBoard`로 이동, `courseId`/`courseTitle`을 현재 코스 값으로
   채운다.

## 범위 밖

- API/데이터 모델 변경 없음 (`PostCreate`, `CourseBoard` 라우트와 파라미터는 이미 존재하는 것을
  그대로 재사용).
- `MapScreen`의 FAB 스타일/로직 리팩터링 — 공용 컴포넌트로 추출하지 않고, 이번에는
  `CourseDetailScreen`에 동일한 스타일을 로컬로 둔다 (사용처가 3번째로 늘어나면 그때 추출 고려).
- `SavedRoutesScreen`(목록 화면) 자체는 변경하지 않는다 — 이미 `CourseDetail`로 정상 이동한다.

## 화면 변경: `CourseDetailScreen.tsx`

- `useAuthGate`의 `requireAuth`를 새로 import한다 (`MapScreen`과 같은 훅).
- 지도(`mapContainer`) 위에 `MapScreen.tsx`의 `floatingButtons`/`fab`/`fabDisabled` 스타일과
  로컬 `FAB` 컴포넌트를 그대로 복제해 우측에 배치한다 (`right: 16`, 세로 `gap: 10`, 46×46
  원형, 흰 배경 + 그림자).
- 버튼 2개, disabled 없음 (컴포넌트가 렌더링되는 시점엔 `course`가 항상 존재하므로 `!course`
  가드가 필요 없다):

```
FAB icon="create-outline" onPress={handlePressWriteReview}
FAB icon="list-outline" onPress={handlePressReviewBoard}
```

- 핸들러:

```ts
const handlePressWriteReview = () => {
  if (!requireAuth() || !course) return;
  navigation.navigate('PostCreate', {
    attachedCourseId: course.id,
    attachedCourseTitle: course.title,
  });
};

const handlePressReviewBoard = () => {
  if (!course) return;
  navigation.navigate('CourseBoard', {
    courseId: course.id,
    courseTitle: course.title,
  });
};
```

(`!course` 체크는 타입 내로잉용 방어 코드이며, 실제로는 로딩 가드 때문에 항상 참이다.)

## 에러 처리

- 별도 에러 케이스 없음 — 기존 `PostCreate`/`CourseBoard` 화면의 에러 처리를 그대로 사용한다.

## 검증 계획

- `npx tsc --noEmit`
- `npx expo start` 백그라운드 기동 후 bundle 200 확인
- 실기기/시뮬레이터에서: 저장한 코스 목록 → 코스 탭 → 상세 화면에서 후기 작성/목록 버튼이 바로
  활성 상태인지 확인 → 후기 작성 버튼(비로그인 시 로그인 유도, 로그인 시 첨부 코스가 채워진
  작성 화면 진입) → 후기 목록 버튼(해당 코스 게시판 진입, 목록 정상 표시) 확인
- 완료 후 `mobile/docs/implementations/course-detail-review-buttons.md`에 설계 요약 기록
