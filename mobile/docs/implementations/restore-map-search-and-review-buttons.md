# 병합 회귀로 소실된 기능 복구 (주변 코스 검색 · 후기 버튼)

## 문제

병합 커밋 `5e9c849`(`Merge branch 'refs/heads/codex/mobile-map'`, 2026-07-08)가 PR 리뷰 없이
main에 직접 push되면서, `MapScreen.tsx`와 `CourseDetailScreen.tsx`에서 main 쪽 코드가 22개
충돌 파일 중 하나로 처리되며 조용히 `codex/mobile-map` 구버전으로 대체됐다. 그 결과 "주변 코스
찾기" 바텀시트와 코스별 후기 작성/목록 버튼이 화면에서 사라졌다. 자세한 원인 분석은
`docs/superpowers/specs/2026-07-11-merge-regression-recovery-design.md` 참고.

## 복구 내용

- `KakaoMapView.tsx`: `getBounds`/`previewCourse`/`showCourseWaypoints`/`clearCoursePreview`
  4개 ref 메서드와 대응 WebView 메시지 핸들러를 병합 전 main(`498c410`) 구현 그대로 복구
- `MapScreen.tsx`: "주변 코스 찾기" pill 버튼, `CourseSearchSheet` 연결, 코스 선택 시
  뜨는 글쓰기/목록 FAB 복구. `getCourses(bounds)`는 되살리지 않고 기존 `getPublicCourses`를
  재사용
- `CourseDetailScreen.tsx`: `handlePressWriteReview`/`handlePressReviewBoard` 핸들러와
  우측 FAB 2개 복구 (원래 설계 PR#27 그대로)

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200
- 실기기/시뮬레이터: Map 화면 주변 코스 찾기 → 바텀시트 → 코스 선택 → 미리보기 → 상세 보기
  → 글쓰기/목록 진입 확인. CourseDetail 화면 후기 작성/목록 버튼 즉시 활성 확인. 기존 기능
  (이름/태그 검색, 보행로 토글, 내 경로 그리기, 좋아요/북마크/댓글) 회귀 없음 확인
