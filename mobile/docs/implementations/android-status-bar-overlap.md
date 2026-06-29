# 안드로이드 상단 바(Header)가 상태 바와 겹치는 문제 수정

## 배경

안드로이드 기기에서 앱 상단 `Header`("Runvas" 타이틀 바)가 시스템 상태 바(시간·배터리·알림 아이콘)와
겹쳐서 보이는 버그 리포트.

## 원인

[App.tsx](../../App.tsx)에서 `SafeAreaView`를 React Native 코어 패키지(`react-native`)에서
가져와 쓰고 있었음. 코어 `SafeAreaView`는 iOS에서만 동작하고 안드로이드에서는 아무 효과가
없다. `react-native-safe-area-context` 패키지도 설치되어 있지 않았다.

## 해결 방법

`~/dev-dnd/running-app`이 Expo SDK 54(동일 버전)에서 이미 같은 문제를 두 번 겪고 해결한 기록
(`docs/implementations/android-status-bar.md`, `android-safe-area.md`)을 그대로 적용:

1. `npx expo install react-native-safe-area-context` 설치.
2. `SafeAreaView`/`SafeAreaProvider`를 `react-native-safe-area-context`에서 가져오고, 루트를
   `SafeAreaProvider`로 한 번 감싼다.
3. `<StatusBar>`에 `translucent={false}`와 `backgroundColor={Colors.white}`를 명시.
   `SafeAreaProvider`만 추가하는 것으로는 해결되지 않는다 — RN이 안드로이드 edge-to-edge를
   기본값으로 쓰고 `StatusBar`가 기본 `translucent`라서, 상태 바를 불투명하게 만들어야 콘텐츠가
   상태 바 아래부터 시작된다.

```tsx
// Before
import { SafeAreaView } from 'react-native';
...
<SafeAreaView style={styles.container}>
  <StatusBar style="dark" />

// After
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
...
<SafeAreaProvider>
  <SafeAreaView style={styles.container}>
    <StatusBar style="dark" translucent={false} backgroundColor={Colors.white} />
```

`Header.tsx`/`RouteStatsBar.tsx`는 수정하지 않았다 — 루트 `SafeAreaView`(context 버전)가 모든
가장자리 inset을 자동으로 처리한다.

## 검증

- `npx tsc --noEmit` 통과
- `npx expo start` 기동 후 안드로이드 번들 요청 HTTP 200 확인 (네이티브 렌더링 자체는 JS 검증으로
  알 수 없으므로, 사용자가 실기기에서 직접 확인 — 해결됨)

## 수정 파일

| 파일 | 변경 내용 |
| --- | --- |
| `package.json` | `react-native-safe-area-context` 추가 |
| `App.tsx` | `SafeAreaView`/`SafeAreaProvider` import 출처 변경, 루트를 `SafeAreaProvider`로 래핑, `StatusBar`에 `translucent={false}`/`backgroundColor` 추가 |

## 참고

- 같은 문제와 해결 기록: `~/dev-dnd/running-app/docs/implementations/android-status-bar.md`,
  `android-safe-area.md`
