-- V6에서 검색 기능 테스트용으로 넣었던 목 공개 코스 6건을 제거한다.
-- Flyway 마이그레이션에 테스트 데이터를 하드코딩한 것 자체가 실수였다 — 모든 환경에
-- 영구 반영되어 실제 사용자 데이터와 섞여 보이는 문제가 있었다.
-- course_tags/bookmarks/course_comments는 courses FK에 ON DELETE CASCADE가 걸려 있어
-- courses 삭제 시 함께 정리되지만, likes는 FK가 없는 범용 target_id 구조라 별도로 지운다.
DELETE FROM likes WHERE target_type = 'COURSE' AND target_id IN (
  'test-course-01', 'test-course-02', 'test-course-03',
  'test-course-04', 'test-course-05', 'test-course-06'
);

DELETE FROM courses WHERE id IN (
  'test-course-01', 'test-course-02', 'test-course-03',
  'test-course-04', 'test-course-05', 'test-course-06'
);
