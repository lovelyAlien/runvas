# 태그 검색 기능

## 개요

지도 화면의 코스 검색에 태그 검색 모드를 추가했습니다.
기존 이름 검색(부분 일치)과 함께 태그 정확 일치 검색을 지원합니다.

## 구현 일자

2026-07-05

## 변경 파일

### Backend
- `backend/.../course/CourseRepository.java` — `findPublicCoursesByTag()` JPQL 쿼리 추가
- `backend/.../course/CourseService.java` — tag 단독 검색 허용 (validation 수정 + 분기 추가)
- `docs/api-contract.md` — `GET /courses` tag 파라미터 단독 사용 문서화

### Mobile
- `mobile/src/services/courseApi.ts` — `searchPublicCoursesByTag(tag, accessToken?, signal?)` 추가
- `mobile/src/components/CourseSearchBar.tsx` — 이름/태그 모드 토글 추가
- `mobile/src/screens/MapScreen.tsx` — `handleSearchCourseByTag` 추가 및 prop 전달

## API 변경

`GET /api/courses?tag=한강`
- bounds/q 없이 tag 단독 검색 허용
- 대소문자 구분 없는 정확 일치 (`lower(t) = lower(:tag)`)
- JPQL: `select distinct c from Course c join c.tags t where c.visibility = 'PUBLIC' and lower(t) = lower(:tag)`

## UI 설계

`CourseSearchBar` 좌측에 모드 토글 버튼 추가:
- `search` 아이콘 → 이름 검색 모드 (기본)
- `pricetag` 아이콘 → 태그 검색 모드 (활성 시 배경색 강조)
- 모드 전환 시 입력값·결과·에러 상태 초기화
- placeholder: "코스 이름으로 검색" / "태그로 검색 (예: 한강)"
- 빈 결과: `"#태그명" 태그의 코스가 없습니다` / `"이름"에 해당하는 코스가 없습니다`
- 태그 검색의 maxLength는 20자 (API 제한값과 동일)

## 테스트

### TypeScript 타입 체크
```
npx tsc --noEmit → No errors
```

### 백엔드 빌드
```
./gradlew compileJava → BUILD SUCCESSFUL
```

### 수동 검증 시나리오
1. 검색 버튼 탭 → 검색바 열림
2. 태그 아이콘 버튼 탭 → 태그 모드 전환, placeholder 변경 확인
3. 태그명 입력 → 300ms debounce 후 검색 실행
4. 결과 목록에서 코스 선택 → 코스 상세 화면 이동
5. 이름 아이콘 버튼 탭 → 이름 모드 복귀 확인
