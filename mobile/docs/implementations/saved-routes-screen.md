# 저장한 경로 목록 화면 (로컬 전용 임시 구현)

## 배경

"저장한 경로를 보여주는 화면"을 추가해달라는 요청. 백엔드(`runvas/backend`)는 아직 README만
있는 상태라 `docs/api-contract.md`의 `POST /courses`, `GET /me/bookmarked-courses` 등을
호출할 수 없다. 그래서 이번 구현은 **기기 로컬 저장소(AsyncStorage)에만** 코스를 저장하고,
백엔드가 준비되면 교체하는 것을 전제로 한다.

## 결정 사항

- 화면 전환: 화면이 늘어날 것을 고려해 `@react-navigation/native` +
  `@react-navigation/native-stack`을 도입했다 (기존에는 네비게이션 라이브러리가 전혀 없었음).
- 저장 위치: `docs/data-model.md`의 `Course`와 다른 임시 타입 `LocalSavedRoute`를
  `src/types/index.ts`에 추가했다. `Course`는 `authorId`/`likeCount`/서버 발급 `id` 등
  백엔드가 채우는 필드를 포함하므로, 로컬 전용 모델에 그대로 끌어쓰면 항상 존재하지 않는
  필드를 다뤄야 하는 문제가 생긴다. `RoutePoint`/`GeoBounds`는 그대로 재사용한다.

## 변경 파일

| 파일 | 변경 내용 |
| --- | --- |
| `package.json` | `@react-navigation/native`, `@react-navigation/native-stack`, `react-native-screens`, `@react-native-async-storage/async-storage` 추가 |
| `src/types/index.ts` | `LocalSavedRoute` 타입 추가 |
| `src/services/localRoutesStorage.ts` | (신규) AsyncStorage 기반 CRUD: `getLocalRoutes`/`saveLocalRoute`/`deleteLocalRoute` |
| `src/navigation/types.ts` | (신규) `RootStackParamList` (`Map`, `SavedRoutes`) |
| `src/screens/MapScreen.tsx` | (신규, `App.tsx`에서 이동) 기존 지도 화면 로직 + "저장" FAB·제목 입력 모달 추가 |
| `src/screens/SavedRoutesScreen.tsx` | (신규) 로컬 저장 코스 목록, 삭제 |
| `src/utils/format.ts` | (신규) `formatDistance`/`formatDuration` — `RouteStatsBar.tsx`와 `SavedRoutesScreen.tsx`가 같은 포맷 함수를 쓰도록 추출 |
| `src/components/Header.tsx` | `onPressSavedRoutes` prop 추가 (저장한 코스 화면으로 이동하는 북마크 아이콘) |
| `src/components/RouteStatsBar.tsx` | 내부 포맷 함수를 `utils/format.ts`로 교체 |
| `App.tsx` | `NavigationContainer` + `Stack.Navigator`로 재구성 (지도 화면 로직은 `MapScreen.tsx`로 이동) |

## 백엔드 연동 시 해야 할 일 (TODO)

백엔드 `POST /courses`, `GET /me/bookmarked-courses` (또는 내 코스 목록 API)가 준비되면:

1. `src/types/index.ts`의 `LocalSavedRoute` 삭제.
2. `src/services/localRoutesStorage.ts` 삭제하고, `src/services/courseApi.ts`의
   `postCourse`/`buildCreateCourseRequest`를 `MapScreen.tsx`의 저장 흐름에 연결.
3. `SavedRoutesScreen.tsx`의 `getLocalRoutes()` 호출을 코스 목록 조회 API 호출로 교체하고,
   응답 `Course[]`를 그대로 렌더링하도록 수정 (현재 로컬 모델과 필드가 거의 동일해서 교체
   범위가 크지 않음).
4. 인증(`Authorization: Bearer <accessToken>`)이 아직 없으므로, 로그인 기능 구현이 선행되어야
   `Required` 인증이 걸린 API들을 호출할 수 있다.

## 검증

- `npx tsc --noEmit` 통과
- `npx expo start` 백그라운드 기동 후 `curl ".../index.bundle?platform=ios&dev=true"` HTTP 200 확인
- 실기기/시뮬레이터에서 저장 모달 입력, 저장 후 목록 화면 진입, 삭제 동작은 코드 리뷰만
  완료했고 직접 실행 확인은 아직 하지 않았다 — 다음 작업 시 실기기 확인 필요.
