# 코스 수정 진입점 구현

## 목표

저장된 코스의 메타데이터(제목, 설명, 공개 설정, 태그)를 수정할 수 있는 진입점과 편집 화면을 추가한다. 경로(path) 수정은 지도 인터랙션 복잡도로 인해 이번 범위에서 제외한다.

## 진입점 두 곳

### SavedRoutesScreen (내 코스 목록)
- `getMyCourses`는 본인 코스만 반환하므로 author 체크 없이 모든 행에 연필 아이콘 표시
- 삭제 버튼 왼쪽에 `pencil-outline` 아이콘 배치
- `navigation.navigate('CourseEdit', { courseId: item.id })` 로 진입

### CourseDetailScreen (코스 상세)
- `user?.id === course.authorId` 조건을 만족할 때만 헤더 오른쪽에 연필 아이콘 표시
- 타인 코스 열람 시 기존 headerSpacer(빈 공간)로 폴백
- `useAuth()`에서 `user`를 추가 destructure (`accessToken`은 이미 사용 중)

## CourseEditScreen

- `useFocusEffect` + `getCourse()` 로 진입 시 기존 값 로드
- `isActive` 플래그로 언마운트 후 setState 방지
- 저장: `patchCourse()` → `evictCourse(courseId)` → `navigation.goBack()`
- `goBack()` 후 이전 화면의 `useFocusEffect`가 자동으로 리프레시하므로 별도 콜백 불필요

### 입력 검증 (클라이언트, docs/api-contract.md 제한 준수)

| 필드 | 제한 |
|---|---|
| title | 1-60자 (빈 문자열 저장 불가) |
| description | 0-500자, 빈 문자열은 null로 변환 |
| visibility | PUBLIC / PRIVATE 토글 |
| tags | 최대 10개, 개별 최대 20자, 중복 자동 무시 |

## API 추가

```typescript
// src/services/courseApi.ts
patchCourse(courseId, body: UpdateCourseRequest, accessToken): Promise<Course>
// PATCH /api/courses/{courseId}
// Content-Type: application/json, Authorization: Bearer <token>
```

## 네비게이션 등록

```typescript
// navigation/types.ts
CourseEdit: { courseId: string }  // RootStackParamList에 추가

// App.tsx
<Stack.Screen name="CourseEdit" component={CourseEditScreen} />
```

## 캐시 무효화

저장 성공 후 `evictCourse(courseId)`를 호출해 인메모리 SVG 경로 캐시를 무효화한다. 이후 SavedRoutesScreen/CourseDetailScreen 재진입 시 최신 데이터를 반영한다.

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200 확인 (기존 실행 서버 응답)
