# 코스 제목 검색 기능 구현

## 목표

`SavedRoutesScreen`(내 코스 목록)에 제목 검색 입력창을 추가한다.
이미 로드된 코스 목록을 클라이언트에서 필터링한다.

## 설계 결정: 클라이언트 사이드 필터링

`GET /courses/mine` API는 `q` 검색 파라미터를 지원하지 않는다.
내 코스 목록은 일반적으로 소규모이므로 이미 로드된 배열을 메모리에서 필터링하는 것이 적절하다.

공개 코스 전체 대상 제목 검색(`GET /courses?q=...`)은 별도의 탐색 화면이 필요하며 이번 범위 외다.

## 구현 위치

- `screens/SavedRoutesScreen.tsx` — 검색 입력창 + 필터 로직 추가

## 상태 관리

```typescript
const [searchQuery, setSearchQuery] = useState('');
// FlatList data:
routes.filter(r =>
  searchQuery.trim()
    ? r.title.toLowerCase().includes(searchQuery.trim().toLowerCase())
    : true
)
```

## UI

헤더 하단, FlatList 위에 검색바 배치:
- `search-outline` 아이콘 + TextInput
- iOS: `clearButtonMode="while-editing"` 기본 지원
- Android: 별도 `close-circle` 버튼으로 지우기

검색어가 있을 때 일치하는 결과가 없으면 기존 emptyText("아직 저장한 코스가 없습니다.")가 표시된다.

## 확인 사항

- `npx tsc --noEmit` — 에러 없음
- Expo 번들 HTTP 200
