# 회원 탈퇴

구현일: 2026-07-18

## 요약

`DELETE /api/me`로 탈퇴를 신청하면 계정은 즉시 삭제되지 않고 30일 소프트 삭제 유예기간에
들어간다. 이 기간 안에 같은 카카오 계정으로 로그인하면 백엔드가 자동으로 계정을 복구하므로,
모바일에는 별도 "복구" 화면이 없다 — 재로그인이 곧 복구다.

## 핵심 결정

- **탈퇴 사유 모달이 곧 최종 확인**: `WithdrawalReasonModal`의 "탈퇴하기" 버튼을 누르는 것 자체가
  최종 확인이므로, 로그아웃처럼 별도 `Alert.alert` 확인창을 추가로 띄우지 않는다. 대신 모달 안에
  30일 유예기간과 재로그인 시 자동 복구된다는 안내 문구를 넣었다.
- **백엔드 성공이 전제 조건**: `AuthContext.withdraw()`는 `deleteMe` 실패 시 예외를 그대로 던지고
  `SecureStore` 삭제를 실행하지 않는다 — `logout()`과 동일한 원칙(`docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`).
- **버튼 위치**: 로그아웃 버튼보다 눈에 덜 띄는 텍스트 버튼으로 그 아래 배치해 실수로 누르기
  어렵게 했다.

## 관련 문서

- `docs/api-contract.md` §DELETE /me
- `docs/superpowers/specs/2026-07-18-account-withdrawal-design.md`
