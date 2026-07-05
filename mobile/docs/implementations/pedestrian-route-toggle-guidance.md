# 보행로 토글 버튼 사용자 안내 (Toast)

## 배경

MapScreen에 보행로 API on/off 토글 버튼을 추가한 후, 사용자가 walk 아이콘만으로 버튼의 의미를
파악하기 어렵다는 문제가 제기됐다. 두 가지 해결 방향(버튼 의미 전달 / 상태 표시) 중
**버튼 의미 전달**만 이번 범위로 확정하고 구현했다.

요구사항 도출: `/deep-interview` 6라운드 진행
스펙: `.omc/specs/deep-interview-pedestrian-route-guidance.md`

## 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| 안내 시점 | 토글 버튼을 누르는 순간 | 사전 설명보다 행동 직후 피드백이 자연스러움 |
| UI 패턴 | Toast (react-native-toast-message) | 모달·Alert는 조작 방해, 자동 소멸 필요 |
| 문구 방향 | 행동 중심 서술형 | 상태명보다 다음에 무슨 일이 일어날지 전달 |
| 표시 위치 | 하단 (position: bottom) | 상단 FAB 버튼과 겹침 방지 |
| 노출 시간 | 2.5초 | 읽기에 충분하고 지도 조작 방해 최소화 |

## 구현 내용

### 1. 패키지 설치

```bash
npx expo install react-native-toast-message
```

Expo SDK 54 환경에서 추가 config plugin 없이 동작. app.json 수정 불필요.

### 2. App.tsx — Toast 루트 등록

SafeAreaProvider 마지막 자식으로 Toast를 추가해 모든 화면 위에 렌더링되게 한다.

```tsx
import Toast from 'react-native-toast-message';

<SafeAreaProvider>
  ...
  <LoginPromptModal />
  <KakaoLoginWebView />
  <Toast />
</SafeAreaProvider>
```

### 3. MapScreen.tsx — 토글 시 Toast 표시

setIsPedestrianRouteEnabled의 함수형 업데이터 내부에서 Toast를 호출해
다음 상태(next)를 기준으로 문구를 결정한다.

```ts
import Toast from 'react-native-toast-message';

const togglePedestrianRoute = useCallback(() => {
  setIsPedestrianRouteEnabled((prev) => {
    const next = !prev;
    Toast.show({
      type: 'info',
      text1: next ? '보행로 경로를 사용합니다' : '직선으로 연결합니다',
      visibilityTime: 2500,
      position: 'bottom',
    });
    return next;
  });
}, []);
```

## 테스트 결과

### TypeScript 검사

```
npx tsc --noEmit → TypeScript: No errors found
```

### 수동 테스트 체크리스트

- [ ] 보행로 ON 상태에서 버튼 누름 → "직선으로 연결합니다" Toast 표시
- [ ] 보행로 OFF 상태에서 버튼 누름 → "보행로 경로를 사용합니다" Toast 표시
- [ ] Toast는 약 2.5초 후 자동 소멸
- [ ] Toast가 표시된 상태에서 지도 탭 가능 (인터랙션 차단 없음)
- [ ] 빠르게 연속으로 토글해도 Toast가 겹치지 않음

## 변경 파일 요약

| 파일 | 변경 내용 |
|------|----------|
| App.tsx | react-native-toast-message import, Toast 루트 등록 |
| src/screens/MapScreen.tsx | react-native-toast-message import, togglePedestrianRoute에 Toast 호출 추가 |
| package.json | react-native-toast-message 의존성 추가 |
