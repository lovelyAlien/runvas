# 코스 상세 화면 후기 작성/목록 버튼 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `CourseDetailScreen`(내 코스/북마크된 코스 상세 화면)에 "후기 작성"/"후기 목록" 원형
버튼을 추가해 코스별 후기 작성·조회로 바로 이동할 수 있게 한다.

**Architecture:** API/데이터 모델 변경 없이, 이미 존재하는 `PostCreate`/`CourseBoard` 라우트로
이동하는 버튼 2개를 `CourseDetailScreen.tsx` 한 파일에 추가한다. 버튼 스타일과 로컬 `FAB`
컴포넌트는 `MapScreen.tsx`에 이미 있는 패턴을 그대로 복제한다 (공용 컴포넌트 추출은 이번 범위
밖).

**Tech Stack:** React Native (Expo SDK 54), `@react-navigation/native-stack`, `@expo/vector-icons`
(Ionicons).

## Global Constraints

- 좌표/거리/시간 등 데이터 모델 변경 없음 — 이번 작업은 순수 UI/네비게이션 변경.
- API 요청·응답 필드는 하나도 바뀌지 않는다 (`docs/api-contract.md` 변경 없음).
- `EXPO_PUBLIC_*` 환경변수·네이티브 모듈 추가 없음.
- 함수형 컴포넌트만 사용, 외부 상태 라이브러리 도입 금지 (기존 `useState`로 충분).
- 이 프로젝트에는 jest 등 테스트 러너가 설정되어 있지 않다 (`mobile/CLAUDE.md` "테스트" 절) —
  검증은 `tsc --noEmit` + Expo 번들 200 확인 + 실기기/시뮬레이터 수동 확인으로 한다. 새로
  테스트 러너를 도입하지 않는다.
- 커밋 메시지에 도구/저작자 표시(`Co-Authored-By`, `codex`, `claude` 등)를 넣지 않는다 — 로컬
  `commit-msg` 훅이 실패하면 CI에서도 실패한다.

---

### Task 1: `CourseDetailScreen`에 후기 작성/목록 버튼 추가

**Files:**
- Modify: `mobile/src/screens/CourseDetailScreen.tsx`

**Interfaces:**
- Consumes: `useAuthGate()` → `{ requireAuth: () => boolean }` (`mobile/src/hooks/useAuthGate.ts`,
  기존 파일, 변경 없음). `navigation.navigate('PostCreate', { attachedCourseId, attachedCourseTitle })`
  와 `navigation.navigate('CourseBoard', { courseId, courseTitle })`는 `mobile/src/navigation/types.ts`
  의 `RootStackParamList`에 이미 정의되어 있음 (변경 없음). `course.id`, `course.title`은 기존
  `Course` 타입 필드 (`mobile/src/types/index.ts`, 변경 없음).
- Produces: 이 화면 안에서만 쓰는 로컬 `handlePressWriteReview`, `handlePressReviewBoard`, 로컬
  `FAB` 컴포넌트, `styles.floatingButtons`/`styles.fab`. 다른 파일에서 참조하지 않음.

- [ ] **Step 1: import 추가**

`mobile/src/screens/CourseDetailScreen.tsx`의 기존 import 블록 (파일 최상단, `useAuth` import
바로 아래)에 한 줄 추가:

```ts
import { useAuth } from '../contexts/AuthContext';
import { useAuthGate } from '../hooks/useAuthGate';
```

- [ ] **Step 2: `requireAuth` 가져오기**

기존:
```ts
  const { courseId } = route.params;
  const { accessToken } = useAuth();
```

변경 후:
```ts
  const { courseId } = route.params;
  const { accessToken } = useAuth();
  const { requireAuth } = useAuthGate();
```

- [ ] **Step 3: 핸들러 2개 추가**

기존 `handleExport` 함수 바로 아래에 추가 (`handleMapReady`와 `handleExport` 사이든 아래든
상관없지만, 여기서는 `handleExport` 다음에 둔다):

```ts
  const handleExport = async () => {
    if (!course) return;
    setIsExporting(true);
    try {
      await exportGpx(course.path, course.title);
    } catch (e: unknown) {
      Alert.alert('내보내기 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsExporting(false);
    }
  };

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

(`!course` 체크는 이 시점엔 항상 거짓이 되는 방어 코드다 — 컴포넌트는 `isLoading || !course`일
때 이미 로딩 화면을 반환하고 `return`하므로, 이 핸들러들이 정의되는 시점의 렌더 분기에서는
`course`가 항상 존재한다. TypeScript 내로잉을 위해 남겨둔다.)

- [ ] **Step 4: 지도 위에 플로팅 버튼 렌더링**

기존:
```tsx
      <View style={styles.mapContainer}>
        <KakaoMapView ref={mapRef} onMapPress={() => {}} onMapReady={handleMapReady} />
      </View>
```

변경 후:
```tsx
      <View style={styles.mapContainer}>
        <KakaoMapView ref={mapRef} onMapPress={() => {}} onMapReady={handleMapReady} />

        <View style={styles.floatingButtons}>
          <FAB icon="create-outline" onPress={handlePressWriteReview} />
          <FAB icon="list-outline" onPress={handlePressReviewBoard} />
        </View>
      </View>
```

- [ ] **Step 5: 로컬 `FAB` 컴포넌트 추가**

파일 마지막의 `export default function CourseDetailScreen` 함수 닫는 `}` 바로 아래,
`const styles = StyleSheet.create({` 위에 추가:

```tsx
interface FABProps {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
}

function FAB({ icon, onPress }: FABProps) {
  return (
    <TouchableOpacity style={styles.fab} onPress={onPress} activeOpacity={0.8}>
      <Ionicons name={icon} size={20} color={Colors.primary} />
    </TouchableOpacity>
  );
}
```

- [ ] **Step 6: 스타일 추가**

기존 `mapContainer` 스타일 바로 아래에 추가:

```ts
  mapContainer: {
    flex: 1,
  },
  floatingButtons: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    gap: 10,
  },
  fab: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    elevation: 4,
  },
```

- [ ] **Step 7: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음 (exit code 0). `FABProps`의 `icon` 타입이 `Ionicons`의 `name` prop과
동일한 유니온이므로 `'create-outline'`/`'list-outline'` 리터럴은 그대로 통과해야 한다.

- [ ] **Step 8: Expo 번들 기동 확인**

Run:
```bash
cd mobile && npx expo start --non-interactive &
sleep 8
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8081/index.bundle?platform=ios&dev=true"
```
Expected: `200` 출력. 이후 백그라운드 expo 프로세스를 종료한다.

- [ ] **Step 9: 실기기/시뮬레이터 수동 확인**

1. 로그인 후 "저장한 코스" 탭 → 코스 하나 선택해 상세 화면 진입.
2. 지도 우측에 원형 버튼 2개(연필 모양 `create-outline`, 목록 모양 `list-outline`)가 처음부터
   활성(회색 아님) 상태로 보이는지 확인.
3. "후기 작성" 버튼 탭 → 로그인 상태면 `PostCreate` 화면으로 이동하고 제목 입력란에
   `[후기] {코스명} - {오늘 날짜}`가 prefill되어 있는지 확인. (비로그인 상태에서 같은 버튼을
   탭하면 로그인 모달이 뜨는지도 별도로 확인.)
4. 뒤로가기 → "후기 목록" 버튼 탭 → `CourseBoard` 화면으로 이동, 헤더에 코스명이 보이고 해당
   코스로 작성된 게시글만 필터링되어 보이는지 확인 (또는 목록이 비어있으면 "이 코스로 작성된
   게시글이 없습니다" 문구 확인).

- [ ] **Step 10: 커밋**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas
git add mobile/src/screens/CourseDetailScreen.tsx
git commit -m "feat(mobile): 코스 상세 화면에 후기 작성/목록 버튼 추가"
```

---

### Task 2: 구현 요약 기록

**Files:**
- Create: `mobile/docs/implementations/course-detail-review-buttons.md`

**Interfaces:**
- Consumes: Task 1에서 실제로 반영된 파일/코드 (Task 1 완료 후에만 진행).
- Produces: 없음 (문서 전용, 다른 코드가 이 파일을 참조하지 않음).

- [ ] **Step 1: 요약 문서 작성**

`mobile/docs/implementations/course-edit-entry-point.md`와 같은 형식으로 아래 내용을 담아
`mobile/docs/implementations/course-detail-review-buttons.md` 파일을 새로 만든다:

```markdown
# 코스 상세 화면 후기 작성/목록 버튼

## 목표

"내 코스"/"북마크된 코스" 상세 화면(`CourseDetailScreen`)에서 바로 후기를 작성하거나 해당
코스의 후기 목록을 볼 수 있게 한다. `MapScreen`의 코스 탐색 시트에 있는 동일한 버튼 패턴을
재사용한다.

## 변경

### CourseDetailScreen (`mobile/src/screens/CourseDetailScreen.tsx`)
- `useAuthGate()`에서 `requireAuth` 추가로 사용
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
```

- [ ] **Step 2: 커밋**

```bash
cd /Users/lovelyalien/Documents/workspace/runvas
git add mobile/docs/implementations/course-detail-review-buttons.md
git commit -m "docs(mobile): 코스 상세 화면 후기 버튼 구현 요약 기록"
```
