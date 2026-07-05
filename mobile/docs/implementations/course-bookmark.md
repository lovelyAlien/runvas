# 공개 코스 북마크 기능

## 배경

공개 코스 탐색 화면에서 마음에 드는 코스를 북마크해 나중에 다시 볼 수 있도록 하는 기능.
좋아요 기능과 동일한 Optimistic UI 패턴을 적용해 반응성을 높인다.

구현 계획: `.omc/plans/autopilot-impl.md`
기능 스펙: `.omc/autopilot/spec.md`

## 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| UI 위치 | CourseDetailScreen likeBar | 좋아요와 같은 영역에 두어 인터랙션 위치를 일관되게 유지 |
| 작성자 표시 | 본인 코스에는 북마크 버튼 미노출 | 자신의 코스를 북마크할 필요 없음 |
| Optimistic UI | 서버 응답 전 UI 먼저 반영, 실패 시 롤백 | 좋아요와 동일한 패턴 (course-like.md 참고) |
| 탭 위치 | SavedRoutesScreen 탭 바 | 기존 "내 코스" 화면에 탭으로 통합해 진입점 최소화 |
| 북마크 탭 수정/삭제 | 미노출 | 타인 코스이므로 편집 권한 없음 |

## 구현 내용

### 1. docs/api-contract.md 수정

`GET /api/courses/{courseId}` 응답 예시에 `bookmarkedByMe` 필드 추가.
미인증 요청에서는 `false` 반환.

### 2. mobile/src/types/index.ts

```ts
// Course 인터페이스에 추가
bookmarkedByMe?: boolean;

// 신규 인터페이스 (CourseSummary 앞에 선언)
export interface BookmarkedCourseSummary extends CourseSummary {
  bookmarkedAt: string;
}
```

### 3. mobile/src/services/bookmarkApi.ts (신규)

`likeApi.ts` 패턴과 동일하게 구현.

```ts
postBookmark(courseId, accessToken)   // POST /api/courses/{id}/bookmarks
deleteBookmark(courseId, accessToken) // DELETE /api/courses/{id}/bookmarks
getBookmarkedCourses(accessToken, params?) // GET /api/me/bookmarked-courses
```

응답 형식:
- `postBookmark` → `{ bookmark: { courseId, createdAt } }`
- `deleteBookmark` → 204 No Content (void)
- `getBookmarkedCourses` → `{ courses: BookmarkedCourseSummary[]; nextCursor: string | null }`

### 4. mobile/src/screens/CourseDetailScreen.tsx

```tsx
// 상태
const [bookmarkedByMe, setBookmarkedByMe] = useState(false);

// 초기화 (useFocusEffect 내)
setBookmarkedByMe(result.bookmarkedByMe ?? false);

// 핸들러 (Optimistic UI)
const handleBookmark = async () => {
  if (!requireAuth()) return;
  const wasBookmarked = bookmarkedByMe;
  setBookmarkedByMe(!wasBookmarked);
  try {
    wasBookmarked
      ? await deleteBookmark(courseId, accessToken)
      : await postBookmark(courseId, accessToken);
  } catch {
    setBookmarkedByMe(wasBookmarked); // 롤백
  }
};

// likeBar — 작성자 본인은 미표시
{user?.id !== course.authorId && (
  <TouchableOpacity onPress={handleBookmark}>
    <Ionicons name={bookmarkedByMe ? 'bookmark' : 'bookmark-outline'} />
  </TouchableOpacity>
)}
```

### 5. mobile/src/screens/SavedRoutesScreen.tsx

- `activeTab: 'mine' | 'bookmarked'` 상태 추가
- `bookmarkedRoutes: BookmarkedCourseSummary[]` 상태 추가
- `useFocusEffect`에서 `getMyCourses`와 `getBookmarkedCourses`를 함께 호출
- 탭 바 UI: "내 코스" / "북마크" (active 탭은 `Colors.primary` 언더라인)
- 북마크 탭 renderItem: 수정·삭제 버튼 없음, 날짜는 `bookmarkedAt` 기준
- 날짜 표시는 "내 코스" 탭과 동일하게 접미사 없이 통일 (기존 "YYYY. MM. DD. 저장" → "YYYY. MM. DD.")

### 6. 북마크 취소 기능 (후속 추가)

북마크 탭에서 코스 편집 권한과 별개로, 자신이 북마크한 항목을 목록에서 바로 취소할 수 있어야 함
(편집 권한과 북마크 취소는 서로 다른 동작 — 위 "북마크 탭 수정·삭제 없음" 결정과 충돌하지 않음).

```tsx
const handleRemoveBookmark = (route: BookmarkedCourseSummary) => {
  Alert.alert('북마크 취소', `"${route.title}"의 북마크를 취소할까요?`, [
    { text: '취소', style: 'cancel' },
    {
      text: '확인',
      style: 'destructive',
      onPress: async () => {
        if (!accessToken) return;
        const previous = bookmarkedRoutes;
        setBookmarkedRoutes((prev) => prev.filter((r) => r.id !== route.id)); // Optimistic UI
        try {
          await deleteBookmark(route.id, accessToken);
        } catch (e: unknown) {
          setBookmarkedRoutes(previous); // 롤백
          Alert.alert('오류', e instanceof Error ? e.message : '북마크 취소 중 오류가 발생했습니다.');
        }
      },
    },
  ]);
};
```

- 북마크 탭 각 행 우측에 채워진 `bookmark` 아이콘 버튼 추가 → 탭하면 확인 Alert 후 취소
- 삭제 확인 다이얼로그 패턴은 `handleDelete`(코스 삭제)와 동일하게 유지

## 테스트 결과

### TypeScript 검사

```
npx tsc --noEmit → TypeScript: No errors found
```

### 수동 테스트 체크리스트

#### CourseDetailScreen

- [ ] 타인 코스 — 북마크 버튼 표시됨
- [ ] 북마크 버튼 탭 → `bookmark` 아이콘으로 즉시 변경 (Optimistic UI)
- [ ] 다시 탭 → `bookmark-outline`으로 복귀
- [ ] 네트워크 실패 시 → 이전 상태로 롤백, Alert 표시
- [ ] 비로그인 상태 → 탭 시 로그인 유도 모달 표시
- [ ] 내 코스 상세 — 북마크 버튼 미표시

#### SavedRoutesScreen

- [ ] "내 코스" 탭 — 기존 목록 정상 표시, 수정·삭제 버튼 있음
- [ ] "북마크" 탭 — 북마크한 코스 목록 표시, 수정·삭제 버튼 없음
- [ ] 북마크 탭 날짜 — `bookmarkedAt` 기준 "YYYY. MM. DD. 저장"
- [ ] 검색어 입력 — 양쪽 탭 모두 필터링 동작
- [ ] 북마크가 없을 때 — "북마크한 코스가 없습니다." 표시
- [ ] 북마크 탭 항목의 북마크 아이콘 버튼 탭 → 확인 Alert 표시
- [ ] Alert에서 "확인" 선택 → 목록에서 즉시 제거 (Optimistic UI)
- [ ] 네트워크 실패 시 → 목록에 다시 표시(롤백), Alert 표시

## 변경 파일 요약

| 파일 | 변경 내용 |
|------|----------|
| docs/api-contract.md | GET /courses/{courseId} 응답에 bookmarkedByMe 추가 |
| mobile/src/types/index.ts | Course.bookmarkedByMe 추가, BookmarkedCourseSummary 추가 |
| mobile/src/services/bookmarkApi.ts | 신규 — postBookmark, deleteBookmark, getBookmarkedCourses |
| mobile/src/screens/CourseDetailScreen.tsx | 북마크 상태·핸들러·버튼 추가 |
| mobile/src/screens/SavedRoutesScreen.tsx | 탭 UI, 북마크 목록 추가 |
