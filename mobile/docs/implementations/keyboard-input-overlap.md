# 텍스트 입력 시 키보드가 입력란·버튼을 가리는 문제 수정

## 배경

글을 쓸 때 입력란과 키보드가 겹쳐 불편하다는 사용 경험. 입력란이 있는 12곳을 점검한 결과,
작은 화면(iPhone SE, iPad의 iPhone 호환 모드 — 앱이 `supportsTablet: false`라 iPad에서도
iPhone 크기로 뜬다)에서 키보드에 입력란이나 확인 버튼이 가려질 수 있는 곳이 7곳이었다.

## 원인

| 위치 | 원인 |
| --- | --- |
| 가운데 뜨는 Modal 5개 (`ReportReasonModal`, `WithdrawalReasonModal`, `NicknameEditModal`, `PaceSelector`, `MapScreen` 코스 저장) | `KeyboardAvoidingView` 없이 카드를 화면 정중앙에 고정 — 키보드가 올라와도 카드가 움직이지 않아 카드 아래쪽(입력란·버튼)이 덮인다 |
| `PostCreateScreen` | `KeyboardAvoidingView`가 없고 본문 입력란이 `flex: 1`로 화면 아래 끝까지 늘어나 있어, 몇 줄만 써도 입력 중인 줄이 키보드 뒤로 간다 |
| `CourseDetailScreen` | `KeyboardAvoidingView`는 있지만 지도가 260pt로 고정이라, 작은 화면에서는 키보드 높이만큼 밀어 올리면 댓글 입력 영역이 남지 않는다 |

## 해결 방법

1. **`KeyboardAvoidingModalContainer` 공통 컴포넌트 추가** — 오버레이를 `KeyboardAvoidingView`
   (iOS `padding`)로 감싸 카드를 키보드 위 남은 영역 가운데로 올리고, 안쪽에 `ScrollView`를 둬서
   카드가 남은 영역보다 크면 스크롤할 수 있게 했다. 탈퇴 모달처럼 사유 목록 + 입력란 + 안내 문구 +
   버튼이 있는 카드는 작은 화면에서 키보드 위 영역보다 커지기 때문이다. `keyboardShouldPersistTaps="handled"`로
   스크롤하거나 버튼을 눌러도 키보드가 먼저 닫히지 않는다.
   다섯 모달은 기존 `overlay` 스타일을 배경(`flex`, `backgroundColor`)과 정렬(`overlayContent`:
   가운데 정렬·여백)로 나눠 각각 `style`/`contentContainerStyle`에 넘긴다.
2. **`PostCreateScreen`** — 헤더 아래 입력 영역을 `KeyboardAvoidingView`로 감쌌다. 본문 입력란이
   키보드 위에서 끝나므로 multiline 입력란이 스스로 커서 위치까지 스크롤한다.
3. **`CourseDetailScreen`** — 새 훅 `useKeyboardVisible`로 키보드가 떠 있는 동안 지도 영역을 접는다.
   카카오 지도는 크기가 바뀌면 `relayout()`이 필요하므로 지도(WebView) 크기는 260pt 그대로 두고,
   바깥 컨테이너만 `height: 0` + `overflow: 'hidden'`으로 잘라낸다.

Android는 건드리지 않았다. 기존 화면들과 같은 규칙(`behavior`는 iOS만)을 따랐다 — Expo 기본
`softwareKeyboardLayoutMode`가 `resize`이고, RN `Modal` 창도 adjustResize로 동작한다.

`PostDetailScreen`, `CourseEditScreen`, `CourseSearchBar`, `SavedRoutesScreen`은 이미
`KeyboardAvoidingView`가 있거나 입력란이 화면 위쪽에 있어 그대로 두었다.

## 검증

- `npx tsc --noEmit` 통과
- `npx expo start` 후 iOS 번들 요청 HTTP 200 확인
- iPad Air 11형(M4) 시뮬레이터(iPhone 호환 모드)에서 직접 확인 — 백엔드 없이 열 수 있도록 로그인
  게이트를 임시로 우회한 로컬 코드로 확인하고 되돌렸다(커밋에 포함하지 않음)
  - 페이스 설정 모달: 키보드가 올라오면 카드가 위로 이동, 입력란과 취소/저장 버튼이 모두 키보드 위에 보임
  - 탈퇴 모달(기타 사유 입력): 입력란이 키보드 위에 보이고, 카드를 스크롤하면 취소/탈퇴하기 버튼이 보이며
    스크롤해도 키보드가 닫히지 않음
- 게시글 작성·코스 상세 댓글은 로그인과 서버 데이터가 필요해 시뮬레이터에서 확인하지 못했다 — 실기기
  확인 필요

## 수정 파일

| 파일 | 변경 내용 |
| --- | --- |
| `src/components/KeyboardAvoidingModalContainer.tsx` | 신규 — 가운데 Modal용 키보드 회피 + 스크롤 오버레이 |
| `src/hooks/useKeyboardVisible.ts` | 신규 — 키보드 표시 여부 훅 (iOS `will*`, Android `did*` 이벤트) |
| `src/components/ReportReasonModal.tsx` | 오버레이를 공통 컴포넌트로 교체 |
| `src/components/WithdrawalReasonModal.tsx` | 오버레이를 공통 컴포넌트로 교체 |
| `src/components/NicknameEditModal.tsx` | 오버레이를 공통 컴포넌트로 교체 |
| `src/components/PaceSelector.tsx` | 오버레이를 공통 컴포넌트로 교체 |
| `src/screens/MapScreen.tsx` | 코스 저장 모달 오버레이를 공통 컴포넌트로 교체 |
| `src/screens/PostCreateScreen.tsx` | 입력 영역을 `KeyboardAvoidingView`로 감쌈 |
| `src/screens/CourseDetailScreen.tsx` | 키보드가 떠 있는 동안 지도 영역 접기 |
