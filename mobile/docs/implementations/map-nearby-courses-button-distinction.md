# 맵 화면 "주변 코스 찾기"/"검색" 버튼 구분

## 문제

`MapScreen`의 좌측 하단 두 버튼("주변 코스 찾기", "이름/태그 검색")이 동일한 `FAB` 컴포넌트를
재사용해 크기·모양·배경색이 완전히 같았고, 텍스트 라벨도 없이 아이콘 심볼(`search` vs
`search-outline`, 채움/윤곽선 차이)만으로 구분되어 있어 사용자가 두 버튼의 기능을 혼동하기 쉬웠다.

## 설계 결정: 형태로 구분

두 버튼은 서로 다른 동작이다.

- "주변 코스 찾기": 지도 bounds로 `getCourses()`를 호출해 코스 목록 시트를 여는 주요 동작.
- "검색": API 호출 없이 이름/태그 검색창(`CourseSearchBar`)을 여는 보조 동작.

아이콘 미세 차이 대신 형태 자체를 다르게 해서 구분한다.

- "주변 코스 찾기": 아이콘(`navigate`) + 텍스트 라벨이 있는 pill 버튼, `Colors.primary` 배경.
- "검색": 기존 아이콘 전용 원형 FAB 유지, 아이콘은 `search`로 통일.

## 구현 위치

- `screens/MapScreen.tsx` — `bottomLeftButtons` 안 버튼 두 개, `styles.nearbyCoursesButton*` 스타일 추가.

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200
