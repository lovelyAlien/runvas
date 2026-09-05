# 닉네임 편집 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 프로필 화면에서 언제든, 그리고 신규 가입 직후 한 번(건너뛰기 가능) 닉네임을 직접 설정할 수 있게 하고, `PATCH /api/me`가 다른 사용자가 이미 쓰는 닉네임으로의 변경을 거부하게 한다.

**Architecture:** 백엔드는 `PATCH /api/me` 처리 시 애플리케이션 레벨(리포지토리 조회)로 닉네임 중복을 검사해 409를 반환한다(DB 스키마 변경 없음). 모바일은 `PaceSelector.tsx`와 동일한 모달 패턴의 `NicknameEditModal`을 새로 만들어 (1) `ProfileScreen`의 상시 편집 진입점과 (2) `App.tsx`의 신규 가입 1회 리다이렉트 지점, 두 곳에서 재사용한다.

**Tech Stack:** Spring Boot(Gradle, JUnit5, MockMvc, Testcontainers), React Native/Expo SDK 54, TypeScript.

## Global Constraints

- `docs/superpowers/specs/2026-09-05-nickname-editing-design.md`의 Non-goals를 그대로 따른다:
  가입 시 기본 닉네임 로직 변경 금지, DB `nickname` UNIQUE 제약(스키마 마이그레이션) 추가 금지.
- 닉네임 길이 제약은 기존 `UpdateMeRequest`의 `@Size(min = 2, max = 30)`을 그대로 따른다 (변경 금지).
- 커밋 메시지는 한글, Conventional Commits 형식, `Co-Authored-By` 등 AI 저작자 표시 금지
  (`~/.claude/CLAUDE.md`, 루트 `CLAUDE.md`).
- API 계약(`docs/api-contract.md`)은 이미 이번 동작(`PATCH /me`의 409 CONFLICT)을 문서화하고
  있으므로 이번 플랜에서는 docs 변경이 필요 없다.
- 모바일 변경 후 검증은 `mobile/CLAUDE.md`의 "변경 후 검증" 절차(`npx tsc --noEmit` +
  `expo start` 백그라운드 후 bundle curl 확인, 또는 시뮬레이터 직접 확인)를 그대로 따른다.

---

## Task 1: Backend — PATCH /me 닉네임 중복 검사

**Files:**
- Modify: `backend/src/main/java/com/runvas/user/repository/UserRepository.java`
- Modify: `backend/src/main/java/com/runvas/user/controller/MeController.java`
- Test: `backend/src/test/java/com/runvas/user/controller/MeControllerTest.java`

**Interfaces:**
- Consumes: 기존 `UserRepository`(JpaRepository), `User.createKakaoUser(...)`, `RunvasException`,
  `ErrorCode.CONFLICT`.
- Produces: `UserRepository.existsByNicknameAndIdNot(String nickname, UUID id): boolean` — 다른
  태스크는 이 메서드를 참조하지 않지만, 향후 유사 중복 검사 로직이 이 시그니처를 재사용할 수 있다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/user/controller/MeControllerTest.java`의 클래스 마지막
`}` 바로 앞(`withdrawWithoutAuthReturns401` 메서드 뒤)에 아래 두 테스트를 추가한다. 먼저
파일 상단 import 블록의 `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;`
바로 뒤에 아래 import를 추가한다:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
```

그리고 클래스 마지막에 아래 두 테스트를 추가한다:

```java
    @Test
    void updateMeWithNicknameAlreadyUsedByAnotherUserReturns409() throws Exception {
        userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-nickname-owner", "owner@example.com", "Seoul Runner", null
        ));
        User requester = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-nickname-requester", "requester@example.com", "Busan Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(requester.getId());

        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "Seoul Runner" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void updateMeWithSameNicknameAsCurrentSucceeds() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-nickname-self", "self@example.com", "Seoul Runner", null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(patch("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nickname": "Seoul Runner" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("Seoul Runner"));
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.controller.MeControllerTest"`
Expected: `updateMeWithNicknameAlreadyUsedByAnotherUserReturns409`가 FAIL — 현재는 중복 검사가
없어서 200이 반환되고 `status().isConflict()` 단언이 실패한다.
`updateMeWithSameNicknameAsCurrentSucceeds`는 이미 PASS(기존 동작으로 충분).

- [ ] **Step 3: `UserRepository`에 중복 조회 메서드 추가**

`backend/src/main/java/com/runvas/user/repository/UserRepository.java`의
`findByProviderAndProviderUserId(...)` 메서드 선언 바로 뒤에 추가:

```java
    boolean existsByNicknameAndIdNot(String nickname, UUID id);
```

- [ ] **Step 4: `MeController.updateMe`에 중복 검사 추가**

`backend/src/main/java/com/runvas/user/controller/MeController.java`의 `updateMe` 메서드를
아래로 교체한다 (`objectionableContentFilter.validate(...)` 호출과
`user.updateProfile(...)` 호출 사이에 중복 검사를 삽입):

```java
    @PatchMapping("/me")
    MeResponse updateMe(
            @AuthenticationPrincipal RunvasPrincipal principal,
            @RequestBody @Valid UpdateMeRequest request
    ) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));
        objectionableContentFilter.validate(request.nickname(), request.bio());
        if (request.nickname() != null
                && userRepository.existsByNicknameAndIdNot(request.nickname(), principal.userId())) {
            throw new RunvasException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다");
        }
        user.updateProfile(request.nickname(), request.profileImageUrl(), request.bio(), request.runningPaceSecPerKm());
        userRepository.save(user);
        return new MeResponse(UserResponse.from(user));
    }
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.controller.MeControllerTest"`
Expected: PASS (전체, 두 신규 테스트 포함)

- [ ] **Step 6: 전체 백엔드 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/runvas/user/repository/UserRepository.java backend/src/main/java/com/runvas/user/controller/MeController.java backend/src/test/java/com/runvas/user/controller/MeControllerTest.java
git commit -m "feat(user): PATCH /me 닉네임 중복 시 409 반환"
```

---

## Task 2: Mobile — `NicknameEditModal` 컴포넌트

**Files:**
- Create: `mobile/src/components/NicknameEditModal.tsx`

**Interfaces:**
- Consumes: `Colors`(`mobile/src/constants/theme.ts`).
- Produces: `NicknameEditModal` 컴포넌트, props
  `{ visible: boolean; initialNickname: string; cancelLabel?: string; onConfirm: (nickname: string) => void; onClose: () => void; isSaving: boolean }`
  — Task 3(`ProfileScreen`)과 Task 4(`App.tsx`)가 이 컴포넌트를 그대로 import해서 쓴다.
  `cancelLabel` 기본값은 `'취소'`이고, 신규 가입 흐름(Task 4)에서만 `'건너뛰기'`로 넘긴다 —
  두 흐름 모두 취소 버튼을 누르면 저장 없이 `onClose`만 호출되는 동일한 동작이라 별도
  `onSkip` prop을 두지 않는다.

- [ ] **Step 1: 컴포넌트 작성**

`mobile/src/components/NicknameEditModal.tsx`:

```tsx
import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { Colors } from '../constants/theme';

const MIN_NICKNAME_LENGTH = 2;
const MAX_NICKNAME_LENGTH = 30;

type Props = {
  visible: boolean;
  initialNickname: string;
  cancelLabel?: string;
  onConfirm: (nickname: string) => void;
  onClose: () => void;
  isSaving: boolean;
};

export default function NicknameEditModal({
  visible,
  initialNickname,
  cancelLabel = '취소',
  onConfirm,
  onClose,
  isSaving,
}: Props) {
  const [inputText, setInputText] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      setInputText(initialNickname);
      setValidationError(null);
    }
  }, [visible, initialNickname]);

  function handleConfirm() {
    const trimmed = inputText.trim();
    if (trimmed.length < MIN_NICKNAME_LENGTH || trimmed.length > MAX_NICKNAME_LENGTH) {
      setValidationError(`${MIN_NICKNAME_LENGTH}~${MAX_NICKNAME_LENGTH}자로 입력해 주세요.`);
      return;
    }
    onConfirm(trimmed);
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <Text style={styles.title}>닉네임 설정</Text>
          <Text style={styles.subtitle}>다른 사용자에게 공개되는 이름입니다</Text>

          <TextInput
            style={[styles.input, validationError ? styles.inputError : null]}
            value={inputText}
            onChangeText={(text) => {
              setInputText(text);
              setValidationError(null);
            }}
            placeholder="닉네임을 입력해 주세요"
            placeholderTextColor={Colors.gray400}
            maxLength={MAX_NICKNAME_LENGTH}
            returnKeyType="done"
            onSubmitEditing={handleConfirm}
            editable={!isSaving}
          />
          {validationError && <Text style={styles.errorText}>{validationError}</Text>}

          <View style={styles.actions}>
            <TouchableOpacity style={styles.cancelButton} onPress={onClose} disabled={isSaving}>
              <Text style={styles.cancelLabel}>{cancelLabel}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.confirmButton, isSaving && styles.confirmButtonDisabled]}
              onPress={handleConfirm}
              disabled={isSaving}
              activeOpacity={0.8}
            >
              {isSaving ? (
                <ActivityIndicator size="small" color={Colors.white} />
              ) : (
                <Text style={styles.confirmLabel}>저장</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    backgroundColor: Colors.white,
    borderRadius: 14,
    padding: 20,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.gray900,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 12,
    color: Colors.gray400,
    marginBottom: 16,
  },
  input: {
    borderWidth: 1,
    borderColor: Colors.gray100,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: Colors.gray900,
    fontWeight: '600',
  },
  inputError: {
    borderColor: Colors.danger,
  },
  errorText: {
    fontSize: 12,
    color: Colors.danger,
    marginTop: 4,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 16,
  },
  cancelButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  cancelLabel: {
    color: Colors.gray500,
    fontWeight: '600',
    fontSize: 14,
  },
  confirmButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    minWidth: 60,
    alignItems: 'center',
  },
  confirmButtonDisabled: {
    backgroundColor: Colors.gray300,
  },
  confirmLabel: {
    color: Colors.white,
    fontWeight: '700',
    fontSize: 14,
  },
});
```

- [ ] **Step 2: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: 커밋**

```bash
git add mobile/src/components/NicknameEditModal.tsx
git commit -m "feat(mobile): 닉네임 편집 모달 컴포넌트 추가"
```

---

## Task 3: Mobile — `ProfileScreen` 닉네임 편집 통합

**Files:**
- Modify: `mobile/src/screens/ProfileScreen.tsx`

**Interfaces:**
- Consumes: `NicknameEditModal`(Task 2), `patchMe`(기존, `mobile/src/services/authApi.ts`),
  `useAuth().updateUser`/`accessToken`(기존).

- [ ] **Step 1: import 및 state 추가**

`mobile/src/screens/ProfileScreen.tsx` 상단 import 블록의
`import PaceSelector from '../components/PaceSelector';` 바로 뒤에 추가:

```tsx
import NicknameEditModal from '../components/NicknameEditModal';
```

`ProfileScreen` 함수 본문의 `const [isSavingPace, setIsSavingPace] = useState(false);` 바로
뒤에 추가:

```tsx
  const [isNicknameModalOpen, setIsNicknameModalOpen] = useState(false);
  const [isSavingNickname, setIsSavingNickname] = useState(false);
```

- [ ] **Step 2: 저장 핸들러 추가**

`handlePaceConfirm` 함수 정의 바로 뒤에 추가:

```tsx
  const handleNicknameConfirm = async (nickname: string) => {
    if (!accessToken) return;
    setIsSavingNickname(true);
    try {
      const result = await patchMe({ nickname }, accessToken);
      await updateUser(result.user);
      setIsNicknameModalOpen(false);
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSavingNickname(false);
    }
  };
```

- [ ] **Step 3: 닉네임 옆 편집 버튼 추가**

`<Text style={styles.nickname}>{user.nickname}</Text>` 한 줄을 아래로 교체:

```tsx
            <View style={styles.nicknameRow}>
              <Text style={styles.nickname}>{user.nickname}</Text>
              <TouchableOpacity
                style={styles.nicknameEditButton}
                activeOpacity={0.6}
                onPress={() => setIsNicknameModalOpen(true)}
              >
                <Ionicons name="create-outline" size={16} color={Colors.gray400} />
              </TouchableOpacity>
            </View>
```

- [ ] **Step 4: 모달 렌더링 추가**

`<PaceSelector ... />` 컴포넌트 바로 뒤(`<WithdrawalReasonModal` 앞)에 추가:

```tsx
      <NicknameEditModal
        visible={isNicknameModalOpen}
        initialNickname={user?.nickname ?? ''}
        onConfirm={handleNicknameConfirm}
        onClose={() => setIsNicknameModalOpen(false)}
        isSaving={isSavingNickname}
      />
```

- [ ] **Step 5: 스타일 조정**

`styles.nickname`에서 `marginBottom: 24,`를 제거하고, `nicknameRow`/`nicknameEditButton`
스타일을 새로 추가한다. `nickname: { ... }` 정의를 아래로 교체:

```tsx
  nickname: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.gray900,
  },
  nicknameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 24,
  },
  nicknameEditButton: {
    padding: 4,
  },
```

- [ ] **Step 6: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 7: 커밋**

```bash
git add mobile/src/screens/ProfileScreen.tsx
git commit -m "feat(mobile): 프로필 화면에 닉네임 편집 기능 추가"
```

---

## Task 4: Mobile — 신규 가입 시 닉네임 설정 프롬프트

**Files:**
- Modify: `mobile/App.tsx`

**Interfaces:**
- Consumes: `NicknameEditModal`(Task 2), `patchMe`(기존), `useAuth().user`/`accessToken`/
  `consumeNewUserRedirect`/`updateUser`(기존).

- [ ] **Step 1: import 추가**

`mobile/App.tsx` 상단 import 블록에서 `import { ActivityIndicator, View } from 'react-native';`를
아래로 교체:

```tsx
import { ActivityIndicator, Alert, View } from 'react-native';
```

`import React, { useEffect } from 'react';`를 아래로 교체:

```tsx
import React, { useEffect, useState } from 'react';
```

`import LoginPromptModal from './src/components/LoginPromptModal';` 바로 뒤에 추가:

```tsx
import NicknameEditModal from './src/components/NicknameEditModal';
```

`import { AuthProvider, useAuth } from './src/contexts/AuthContext';` 바로 뒤에 추가:

```tsx
import { patchMe } from './src/services/authApi';
```

- [ ] **Step 2: `NewUserRedirectWatcher` 교체**

기존 `NewUserRedirectWatcher` 함수 전체를 아래로 교체:

```tsx
// 로그인 성공 후 "닉네임 확인 → 게시판 1회 자동 이동" 부수효과를 처리하는 단일 지점.
// consumeNewUserRedirect()를 여기 한 곳에서만 호출해 중복 소비를 막는다.
function NewUserRedirectWatcher() {
  const { user, consumeNewUserRedirect, accessToken, updateUser } = useAuth();
  const [isNicknamePromptVisible, setIsNicknamePromptVisible] = useState(false);
  const [isSavingNickname, setIsSavingNickname] = useState(false);

  useEffect(() => {
    if (user && consumeNewUserRedirect()) {
      setIsNicknamePromptVisible(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  function goToBoard() {
    setIsNicknamePromptVisible(false);
    if (navigationRef.isReady()) {
      navigationRef.navigate('Tabs', { screen: 'Board' });
    }
  }

  async function handleNicknameConfirm(nickname: string) {
    if (!accessToken) {
      goToBoard();
      return;
    }
    setIsSavingNickname(true);
    try {
      const result = await patchMe({ nickname }, accessToken);
      await updateUser(result.user);
      goToBoard();
    } catch (e: unknown) {
      Alert.alert('저장 실패', e instanceof Error ? e.message : '알 수 없는 오류가 발생했습니다.');
    } finally {
      setIsSavingNickname(false);
    }
  }

  return (
    <NicknameEditModal
      visible={isNicknamePromptVisible}
      initialNickname={user?.nickname ?? ''}
      cancelLabel="건너뛰기"
      onConfirm={handleNicknameConfirm}
      onClose={goToBoard}
      isSaving={isSavingNickname}
    />
  );
}
```

- [ ] **Step 3: 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 4: 커밋**

```bash
git add mobile/App.tsx
git commit -m "feat(mobile): 신규 가입 직후 닉네임 설정 프롬프트 추가"
```

---

## Task 5: 최종 검증 및 구현 기록

**Files:**
- Create: `mobile/docs/implementations/nickname-editing.md`

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 모바일 타입 체크**

Run: `cd mobile && npx tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: 모바일 번들링 확인**

Run: `cd mobile && npx expo start &` 후 `curl "http://localhost:8081/index.bundle?platform=ios&dev=true"`
Expected: HTTP 200

- [ ] **Step 4: 시뮬레이터/실기기 수동 확인**

아래 세 시나리오를 iOS 시뮬레이터(또는 실기기)에서 직접 확인한다:

1. 프로필 화면 → 닉네임 옆 연필 아이콘 탭 → 닉네임 수정 → 저장 → 화면에 즉시 반영되는지
2. 다른 사용자가 이미 쓰는 닉네임으로 수정 시도 → "이미 사용 중인 닉네임입니다" 에러가 뜨는지
3. (테스트 계정으로) 신규 가입 직후 닉네임 설정 모달이 뜨는지, "저장"과 "건너뛰기" 둘 다
   게시판 탭으로 정상 이동하는지

- [ ] **Step 5: 구현 기록 작성**

`mobile/docs/implementations/nickname-editing.md`:

```markdown
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
```

- [ ] **Step 6: 커밋**

```bash
git add mobile/docs/implementations/nickname-editing.md
git commit -m "docs(mobile): 닉네임 편집 기능 구현 기록 추가"
```
