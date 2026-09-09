# 닉네임 편집 기능 구현

## 목표

프로필 화면에서 언제든, 그리고 신규 가입 직후 한 번(건너뛰기 가능) 닉네임을 직접 설정할 수 있게 한다.
Apple 로그인은 최초 1회만 프로필 이름을 제공하고 이후엔 제공하지 않아 기본값(`Runvas Runner`)이
고정되는 문제를 완화한다.

## API

```
PATCH /api/me  { "nickname": "..." }
```

기존 계약 그대로 재사용. 다른 사용자가 이미 쓰는 닉네임이면 `409 CONFLICT`
(`이미 사용 중인 닉네임입니다`)를 반환하도록 백엔드에 애플리케이션 레벨 검사를 추가했다
(`UserRepository.existsByNicknameAndIdNot`, DB 스키마 변경 없음).

## 구현 위치

- `components/NicknameEditModal.tsx` — `PaceSelector.tsx`와 동일한 모달 패턴, 신규 생성
- `screens/ProfileScreen.tsx` — 닉네임 옆 연필 아이콘 → 모달 오픈 → `patchMe` 호출
- `App.tsx`의 `NewUserRedirectWatcher` — 신규 가입 시 게시판 이동 전에 같은 모달을 먼저 보여줌

## 설계 문서

`docs/superpowers/specs/2026-09-05-nickname-editing-design.md`,
`docs/superpowers/plans/2026-09-05-nickname-editing.md` 참고.

## 범위 밖으로 남긴 것

- 가입 시 기본 닉네임(`Runvas Runner`) 자체의 중복 허용 여부
- DB `nickname` UNIQUE 제약(스키마 마이그레이션) — 운영 DB에 이미 중복 데이터가 있을 가능성이
  높아 이번에는 애플리케이션 레벨 검사로 대체
