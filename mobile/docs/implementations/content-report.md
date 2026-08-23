# 콘텐츠 신고 기능

## 개요

게시글/댓글/코스댓글을 신고할 수 있는 기능을 추가했습니다. Apple App Review Guideline 2.1
재제출 요청에서 UGC 신고/차단 메커니즘을 언급했고, Guideline 1.2 대응을 위해 최소 범위로
구현했습니다.

## 구현 일자

2026-08-23

## 변경 파일

### Backend
- `backend/.../community/Report.java`, `ReportRepository.java`, `ReportService.java`,
  `ReportController.java` — 신고 생성 API
- `backend/.../community/{PostService,CommentService,CourseCommentService}.java` —
  `deleteAsAdmin` 추가
- `backend/.../admin/AdminReport{View,QueryService,ActionService,Controller}.java` —
  관리자 신고 조회/처리
- `backend/src/main/resources/templates/admin/reports.html` — 관리자 신고 화면
- `docs/api-contract.md`, `docs/data-model.md`, `docs/admin-dashboard.md` — 계약 문서

### Mobile
- `mobile/src/services/reportApi.ts` — 신고 API 클라이언트
- `mobile/src/components/ReportReasonModal.tsx` — 신고 사유 선택 모달
- `mobile/src/screens/PostDetailScreen.tsx` — 게시글/댓글 신고 버튼
- `mobile/src/components/CourseCommentItem.tsx`, `mobile/src/screens/CourseDetailScreen.tsx` —
  코스 댓글 신고 버튼

## 설계 문서

`docs/superpowers/specs/2026-08-23-content-report-design.md`
`docs/superpowers/plans/2026-08-23-content-report.md`

## 사용한 스킬

superpowers:brainstorming → superpowers:writing-plans →
superpowers:subagent-driven-development(또는 executing-plans)
