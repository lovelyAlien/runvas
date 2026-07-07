-- 검색 기능 테스트용 공개 코스 샘플 데이터
-- author_id는 테스트용 고정 UUID (courses 테이블에 users FK 제약 없음)
INSERT INTO courses (id, author_id, title, description, path, waypoints, distance_meters, estimated_duration_seconds, sw_lat, sw_lng, ne_lat, ne_lng, visibility, like_count, start_address, created_at, updated_at) VALUES
(
  'test-course-01',
  'test-user-00000000-0000-0000-0001',
  '한강 반포 달리기 코스',
  '반포한강공원을 따라 달리는 강변 코스입니다.',
  '[{"latitude":37.5120,"longitude":126.9960,"sequence":0},{"latitude":37.5125,"longitude":126.9980,"sequence":1},{"latitude":37.5130,"longitude":127.0010,"sequence":2},{"latitude":37.5125,"longitude":127.0040,"sequence":3}]',
  '[{"latitude":37.5120,"longitude":126.9960,"sequence":0},{"latitude":37.5125,"longitude":126.9980,"sequence":1},{"latitude":37.5130,"longitude":127.0010,"sequence":2},{"latitude":37.5125,"longitude":127.0040,"sequence":3}]',
  3200, 1200,
  37.5120, 126.9960, 37.5130, 127.0040,
  'PUBLIC', 5,
  '서울특별시 서초구 반포동',
  NOW(), NOW()
),
(
  'test-course-02',
  'test-user-00000000-0000-0000-0001',
  '남산 둘레길 조깅',
  '남산 순환도로를 한 바퀴 도는 업힐 코스입니다.',
  '[{"latitude":37.5500,"longitude":126.9820,"sequence":0},{"latitude":37.5520,"longitude":126.9840,"sequence":1},{"latitude":37.5540,"longitude":126.9850,"sequence":2},{"latitude":37.5530,"longitude":126.9870,"sequence":3},{"latitude":37.5510,"longitude":126.9860,"sequence":4},{"latitude":37.5500,"longitude":126.9820,"sequence":5}]',
  '[{"latitude":37.5500,"longitude":126.9820,"sequence":0},{"latitude":37.5540,"longitude":126.9850,"sequence":1},{"latitude":37.5500,"longitude":126.9820,"sequence":2}]',
  5800, 2100,
  37.5500, 126.9820, 37.5540, 126.9870,
  'PUBLIC', 12,
  '서울특별시 중구 남산동',
  NOW(), NOW()
),
(
  'test-course-03',
  'test-user-00000000-0000-0000-0002',
  '홍대 골목 탐방 러닝',
  '홍대 앞 골목을 누비는 도심 러닝 코스입니다.',
  '[{"latitude":37.5540,"longitude":126.9230,"sequence":0},{"latitude":37.5555,"longitude":126.9240,"sequence":1},{"latitude":37.5565,"longitude":126.9255,"sequence":2},{"latitude":37.5550,"longitude":126.9270,"sequence":3}]',
  '[{"latitude":37.5540,"longitude":126.9230,"sequence":0},{"latitude":37.5565,"longitude":126.9255,"sequence":1},{"latitude":37.5550,"longitude":126.9270,"sequence":2}]',
  2100, 780,
  37.5540, 126.9230, 37.5565, 126.9270,
  'PUBLIC', 8,
  '서울특별시 마포구 홍대입구역',
  NOW(), NOW()
),
(
  'test-course-04',
  'test-user-00000000-0000-0000-0002',
  '경복궁 주변 역사 코스',
  '경복궁과 북촌 한옥마을을 연결하는 문화 러닝 코스.',
  '[{"latitude":37.5760,"longitude":126.9760,"sequence":0},{"latitude":37.5780,"longitude":126.9780,"sequence":1},{"latitude":37.5800,"longitude":126.9800,"sequence":2},{"latitude":37.5820,"longitude":126.9820,"sequence":3},{"latitude":37.5810,"longitude":126.9840,"sequence":4}]',
  '[{"latitude":37.5760,"longitude":126.9760,"sequence":0},{"latitude":37.5800,"longitude":126.9800,"sequence":1},{"latitude":37.5810,"longitude":126.9840,"sequence":2}]',
  4500, 1680,
  37.5760, 126.9760, 37.5820, 126.9840,
  'PUBLIC', 20,
  '서울특별시 종로구 경복궁',
  NOW(), NOW()
),
(
  'test-course-05',
  'test-user-00000000-0000-0000-0003',
  '올림픽공원 산책 달리기',
  '올림픽공원 내부 산책로를 이용한 평탄한 러닝 코스.',
  '[{"latitude":37.5200,"longitude":127.1210,"sequence":0},{"latitude":37.5210,"longitude":127.1230,"sequence":1},{"latitude":37.5225,"longitude":127.1250,"sequence":2},{"latitude":37.5215,"longitude":127.1270,"sequence":3},{"latitude":37.5200,"longitude":127.1250,"sequence":4}]',
  '[{"latitude":37.5200,"longitude":127.1210,"sequence":0},{"latitude":37.5225,"longitude":127.1250,"sequence":1},{"latitude":37.5200,"longitude":127.1250,"sequence":2}]',
  3800, 1400,
  37.5200, 127.1210, 37.5225, 127.1270,
  'PUBLIC', 15,
  '서울특별시 송파구 올림픽공원',
  NOW(), NOW()
),
(
  'test-course-06',
  'test-user-00000000-0000-0000-0003',
  '북한산 둘레길 초급 코스',
  '북한산 둘레길 초급 구간 — 경사가 완만해 초보 러너에게 적합합니다.',
  '[{"latitude":37.6500,"longitude":126.9800,"sequence":0},{"latitude":37.6520,"longitude":126.9810,"sequence":1},{"latitude":37.6540,"longitude":126.9820,"sequence":2},{"latitude":37.6560,"longitude":126.9830,"sequence":3}]',
  '[{"latitude":37.6500,"longitude":126.9800,"sequence":0},{"latitude":37.6560,"longitude":126.9830,"sequence":1}]',
  4200, 1560,
  37.6500, 126.9800, 37.6560, 126.9830,
  'PUBLIC', 3,
  '서울특별시 강북구 북한산',
  NOW(), NOW()
);

-- course_tags 샘플
INSERT INTO course_tags (course_id, tag) VALUES
  ('test-course-01', '한강'),
  ('test-course-01', '초보'),
  ('test-course-02', '남산'),
  ('test-course-02', '업힐'),
  ('test-course-03', '홍대'),
  ('test-course-03', '도심'),
  ('test-course-04', '경복궁'),
  ('test-course-04', '문화'),
  ('test-course-05', '올림픽공원'),
  ('test-course-05', '평탄'),
  ('test-course-06', '북한산'),
  ('test-course-06', '초급');
