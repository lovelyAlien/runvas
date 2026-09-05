# 닉네임 편집 기능 Design

## 배경

Apple 로그인 시 Apple이 `fullName`을 항상 주지 않는다(최초 1회, 사용자가 비공개하지 않은 경우에만
제공). 그 결과 다수 사용자의 닉네임이 서버 기본값 `"Runvas Runner"`로 고정된다. 사용자가 직접
닉네임을 바꿀 수 있는 화면이 앱에 없어서, 로그인 제공자가 준 이름이 없으면 영구히
`"Runvas Runner"`로 남는다.

## Goal

- 사용자가 언제든 프로필 화면에서 닉네임을 수정할 수 있다.
- 신규 가입 직후(카카오/Apple 공통) 닉네임을 확인하고 원하면 바로 수정할 수 있는 기회를 준다(건너뛰기 가능).
- 닉네임 중복 시 서버가 거부한다 — `docs/api-contract.md`가 이미 문서화한 `PATCH /me`의
  `409 CONFLICT: 이미 사용 중인 닉네임` 동작을 실제로 구현한다.

## Non-goals

- 신규 가입 시 기본 닉네임 생성 로직(`User.createAppleUser`/`createKakaoUser`의 fallback) 변경.
  가입 시점 기본값 중복 허용 여부는 별도 논의 대상.
- DB에 `nickname` UNIQUE 제약(스키마 마이그레이션) 추가. 운영 DB에 이미 중복 닉네임이 존재할
  가능성이 높아(다수 사용자가 `"Runvas Runner"` 기본값을 공유), 스키마 마이그레이션은 기존
  데이터 정리 없이는 배포 시 Flyway 마이그레이션 실패로 이어질 위험이 있다. 이번 작업은
  애플리케이션 레벨 검사로 대체한다.
- 닉네임 대소문자/공백 정규화 규칙. 기존 회원가입 로직과 동일하게 있는 그대로 저장한다.

## 발견한 기존 상태 (설계 근거)

- `PATCH /api/me`가 이미 `nickname`(2~30자, `UpdateMeRequest`)을 받아 `User.updateProfile`로
  반영하고, `ObjectionableContentFilter`로 금칙어 검사를 이미 수행한다 — API 계약/필드 변경 없음.
- `users` 테이블(`V1__create_users.sql`)에는 `nickname`에 UNIQUE 제약이 없다 —
  `(provider, provider_user_id)`만 유니크.
- 모바일 `AuthContext`는 로그인 성공 시(`isNewUser`) `pendingNewUserRedirect`를 세팅하고,
  `App.tsx`의 `NewUserRedirectWatcher`가 이를 소비해 게시판 탭으로 1회 자동 이동시키는 로직이
  이미 있다 — 이 지점에 닉네임 설정 모달을 끼워 넣는다.
- `ProfileScreen.tsx`의 달리기 페이스 수정(`PaceSelector` 모달 + `patchMe` 호출) 패턴을 그대로
  재사용한다.

## 아키텍처

### 백엔드: PATCH /me 닉네임 중복 검사 (애플리케이션 레벨)

- `UserRepository`에 쿼리 메서드 추가:
  ```java
  boolean existsByNicknameAndIdNot(String nickname, UUID id);
  ```
- `MeController.updateMe`에서 `objectionableContentFilter.validate(...)` 직후,
  `user.updateProfile(...)` 호출 전에:
  ```java
  if (request.nickname() != null
          && userRepository.existsByNicknameAndIdNot(request.nickname(), principal.userId())) {
      throw new RunvasException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다");
  }
  ```
  자기 자신이 현재 쓰는 닉네임으로 "변경"(사실상 무변경) 요청해도 충돌로 처리되지 않도록
  `AndIdNot`으로 요청자 본인은 제외한다.
- `docs/api-contract.md`의 `PATCH /me` 계약은 이미 이 동작을 문서화하고 있어 문서 변경 불필요.

### 모바일: `NicknameEditModal` 공용 컴포넌트

- 신규 파일 `mobile/src/components/NicknameEditModal.tsx`, `PaceSelector.tsx`와 동일한 구조
  (Modal + TextInput + 확인/취소 버튼 + `isSaving` 로딩 상태).
- Props:
  ```ts
  type Props = {
    visible: boolean;
    initialNickname: string;
    cancelLabel?: string;      // 취소 버튼 라벨 커스터마이즈(예: 신규 가입 흐름의 "건너뛰기")
    onConfirm: (nickname: string) => void;
    onClose: () => void;
    isSaving: boolean;
  };
  ```
- 클라이언트 검증: trim 후 2~30자 범위 밖이면 확인 버튼을 눌렀을 때 안내 문구를 보여준다.
  서버 에러(409 등)는 상위 컴포넌트가 `Alert`로 표시(아래).

### 모바일: `ProfileScreen.tsx` 통합

- 닉네임 텍스트 옆에 편집 아이콘(`Ionicons "pencil" or "create-outline"`) 추가.
- 탭하면 `NicknameEditModal`을 `user.nickname`으로 프리필해 오픈.
- 확인 시: `patchMe({ nickname }, accessToken)` → 성공하면 `updateUser(result.user)`, 모달 닫기.
  실패 시(기존 `handlePaceConfirm`과 동일 패턴) `Alert.alert('저장 실패', e.message)` — 409면
  서버가 반환한 "이미 사용 중인 닉네임입니다"가 그대로 노출됨.

### 모바일: `App.tsx`의 `NewUserRedirectWatcher` 통합

- 신규 가입 감지(`user && consumeNewUserRedirect()`) 시 게시판으로 즉시 이동하는 대신, 먼저
  `NicknameEditModal`을 `user.nickname`(Apple/Kakao가 준 값 또는 `"Runvas Runner"`)으로 프리필해
  오픈한다.
- "확인"(저장) 또는 "건너뛰기" 둘 다 → 기존과 동일하게 게시판 탭으로 1회 자동 이동.
- 저장 실패(예: 우연히 중복) 시에도 모달을 닫지 않고 에러만 보여줘서 사용자가 다시 시도하거나
  건너뛸 수 있게 한다.

## 데이터 흐름

```
[Apple/Kakao 로그인 성공]
        │ isNewUser=true
        ▼
AuthContext.pendingNewUserRedirect = true
        │
        ▼
App.tsx NewUserRedirectWatcher
  → NicknameEditModal(prefill: user.nickname) 오픈
        │
   ┌────┴────┐
 [확인]     [건너뛰기]
   │           │
   ▼           │
PATCH /api/me  │
 (닉네임 검증  │
  + 중복 검사) │
   │           │
   └────┬──────┘
        ▼
  게시판 탭으로 이동 (기존 동작)
```

프로필 화면에서의 수정은 위와 별개 경로로, 언제든 동일한 `NicknameEditModal` + `patchMe`를 재사용한다.

## 에러 처리

| 상황 | 처리 |
| --- | --- |
| 닉네임 2~30자 범위 밖 | 클라이언트에서 확인 버튼을 눌렀을 때 안내 문구를 보여주고 API 호출 자체를 막음 |
| 서버 금칙어 필터 거부 | 기존 `parseApiErrorMessage`가 서버 메시지를 그대로 노출 (기존 동작 재사용, 변경 없음) |
| 닉네임 중복 (409) | 서버가 "이미 사용 중인 닉네임입니다" 반환 → `Alert`로 표시, 모달 유지(재입력 가능) |
| 네트워크 오류 | 기존 `patchMe`/`parseApiErrorMessage` 에러 처리 그대로 재사용 |

## 테스트 계획

- 백엔드: `MeControllerTest`에 "다른 사용자가 이미 쓰는 닉네임으로 PATCH 시 409" 케이스 추가.
  기존 정상 케이스(자기 자신 닉네임 유지 포함)가 여전히 통과하는지 확인.
- 모바일: 자동화 테스트 러너 미구성(`mobile/CLAUDE.md` 참고) — `npx tsc --noEmit` +
  시뮬레이터에서 수동 확인:
  1. 프로필 화면에서 닉네임 수정 성공
  2. 이미 존재하는 다른 사용자의 닉네임으로 수정 시도 → 에러 메시지 노출
  3. 신규 가입 직후 모달이 뜨는지, "확인"/"건너뛰기" 둘 다 게시판 탭으로 정상 이동하는지

## 범위 밖으로 남기는 것 (참고, 이번 작업에서 처리하지 않음)

- 신규 가입 시 기본 닉네임(`Runvas Runner`) 자체의 중복 허용 여부.
- DB `nickname` UNIQUE 제약 마이그레이션 및 기존 중복 데이터 정리.
