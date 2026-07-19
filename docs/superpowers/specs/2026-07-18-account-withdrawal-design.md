# 회원 탈퇴 설계

작성일: 2026-07-18
관련 문서: `docs/api-contract.md` §Auth APIs, §User, `docs/data-model.md` §User,
`docs/superpowers/specs/2026-07-01-logout-token-invalidation-design.md`(토큰 블랙리스트 재사용)

## 배경

Runvas는 카카오 소셜 로그인만 지원하며, 현재 계정을 완전히 정리할 수 있는 방법이 없다.
이 설계는 `DELETE /api/me`로 대표되는 회원 탈퇴 기능을 정의한다.

## 정책 결정 (브레인스토밍 확정 사항)

- **콘텐츠 처리**: 탈퇴해도 사용자가 만든 코스·게시글·댓글·코스댓글은 삭제하지 않고 그대로
  유지한다. 대신 계정이 실제로 사라진 시점부터 작성자 표시만 고정 문구 `"탈퇴한 사용자"`로
  바뀐다(`profileImageUrl`/`bio`는 `null`).
- **소프트 삭제 + 30일 유예기간**: 탈퇴 신청 즉시 계정을 지우지 않는다. `users.deleted_at`만
  채우고, 30일 뒤 배치가 실제로 하드 삭제한다. 유예기간 동안 콘텐츠·좋아요·북마크·카카오 연동은
  전부 그대로 둔다(복구했을 때 아무것도 사라져 있지 않아야 하므로).
- **재가입 제한 = 로그인 시 자동 복구**: 유예기간 중 같은 카카오 계정으로 다시 로그인하면 새
  계정이 생기는 게 아니라 기존 계정의 `deleted_at`이 지워지며 정상 로그인된다. 별도 "복구 신청"
  화면은 만들지 않는다. `provider`+`provider_user_id` unique 제약이 이미 있어 애초에 중복 계정
  생성은 불가능하다.
- **탈퇴 사유 설문**: 고정 보기 5개 중 단일 선택(필수). `기타` 선택 시 1-200자 직접입력 필수.
  사용자 식별자와 무관한 별도 익명 테이블에 즉시 기록하고, 이 기록은 계정이 나중에 하드
  삭제되어도 함께 사라지지 않는다.
- **카카오 unlink**: 계정이 하드 삭제되는 시점(유예기간 종료)에 백엔드가 카카오 unlink API를
  best-effort로 호출한다. 실패해도 계정 하드 삭제는 그대로 진행하고 로그만 남긴다. 유예기간 중에는
  unlink를 호출하지 않는다(복구 가능성을 살려두기 위해 — 미리 끊어도 로그인 자체는 카카오
  재동의로 다시 되므로 굳이 먼저 끊을 이유가 없다).

## 데이터 모델 변경

### `users` 테이블

```sql
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
```

`deleted_at`이 `NULL`이 아니면 탈퇴 유예기간 중이라는 뜻이다. `docs/data-model.md`의 User 필드
표에 `deletedAt`을 추가하되, **API 응답(`User`/`MeResponse`)에는 노출하지 않는다** — 탈퇴 처리
자체가 `GET /me`를 더 이상 호출할 수 없는 세션에서 일어나므로 노출할 필요가 없다.

### `withdrawal_feedback` 테이블 (신규)

사용자 식별자를 전혀 포함하지 않는 완전 익명 통계 테이블. 계정이 하드 삭제되어도 영향받지 않는다.

```sql
CREATE TABLE withdrawal_feedback (
    id VARCHAR(36) PRIMARY KEY,
    reason_code VARCHAR(30) NOT NULL,
    reason_detail VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### `WithdrawalReason` (신규 열거형, 백엔드 전용 — API 계약에도 그대로 노출)

| 코드 | 문구 |
| --- | --- |
| `NOT_USING` | 자주 사용하지 않아요 |
| `MISSING_FEATURES` | 원하는 코스·기능이 없어요 |
| `BUGS_OR_ERRORS` | 오류·버그가 많아요 |
| `PRIVACY_CONCERN` | 개인정보가 걱정돼요 |
| `OTHER` | 기타 (이 경우 `reasonDetail` 1-200자 필수) |

## API 계약 변경

`docs/api-contract.md` §Auth APIs 아래 `GET /me`, `PATCH /me` 사이 또는 뒤에 다음 엔드포인트를
추가한다.

### DELETE /me

회원 탈퇴를 신청한다. 계정을 즉시 삭제하지 않고 30일 유예기간을 둔다. 유예기간 중 같은 카카오
계정으로 다시 로그인하면 자동으로 복구된다. 유예기간이 지나면 계정은 하드 삭제되고, 이 사용자가
작성한 코스·게시글·댓글은 삭제되지 않고 작성자 표시만 `"탈퇴한 사용자"`로 바뀐다.

#### Auth

`Required`

#### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | Y | `NOT_USING`, `MISSING_FEATURES`, `BUGS_OR_ERRORS`, `PRIVACY_CONCERN`, `OTHER` 중 하나 |
| `reasonDetail` | string \| null | N | `reason`이 `OTHER`일 때만 필수 (1-200자) |

```json
{
  "reason": "MISSING_FEATURES",
  "reasonDetail": null
}
```

#### Response: 204 No Content

응답 본문이 없습니다. 요청에 사용된 `accessToken`은 `POST /auth/logout`과 동일하게 즉시
블랙리스트 처리됩니다(다른 기기의 세션은 유예기간 동안 계속 유효합니다).

#### Errors

- `400 VALIDATION_ERROR`: `reason` 누락/미지원 값, `reason`이 `OTHER`인데 `reasonDetail` 누락
- `401 UNAUTHORIZED`: 로그인하지 않음

이미 탈퇴 신청되어 유예기간 중인 계정이 다시 `DELETE /me`를 호출하면 사유를 다시 기록하거나
유예기간을 연장하지 않고 `204`만 반환합니다(멱등 처리).

## 백엔드 구현

### `User` (`com.runvas.user.domain.User`)

- `deletedAt` 필드 추가 (nullable `Instant`).
- `isDeleted()`, `markWithdrawn()`(현재 시각으로 `deletedAt` 설정), `restore()`(`deletedAt = null`) 추가.

### `WithdrawalReason` (신규, `com.runvas.user.domain`)

위 표의 5개 값을 갖는 열거형.

### `WithdrawalFeedback` (신규 엔티티, `com.runvas.user.domain`) / `WithdrawalFeedbackRepository` (신규)

`id`(UUID 문자열), `reasonCode`(`WithdrawalReason`, `@Enumerated(STRING)`), `reasonDetail`,
`createdAt`만 갖는다. `userId`/`authorId` 등 사용자 참조 필드는 절대 추가하지 않는다.

### `WithdrawRequest` DTO (신규, `com.runvas.user.dto`)

```java
public record WithdrawRequest(@NotNull WithdrawalReason reason, String reasonDetail) {}
```

`reasonDetail` 길이(1-200자)와 `OTHER`일 때 필수 여부는 Bean Validation이 아니라
`AccountWithdrawalService`에서 직접 검증한다(조건부 필수라 `@Valid` 애너테이션만으로 표현하기
번거로움).

### `AccountWithdrawalService` (신규, `com.runvas.user.service`)

`AuthLogoutService` 패턴을 그대로 따른다.

```java
@Transactional
public void withdraw(UUID userId, String token, WithdrawalReason reason, String reasonDetail) {
    if (reason == WithdrawalReason.OTHER && (reasonDetail == null || reasonDetail.isBlank())) {
        throw new RunvasException(ErrorCode.VALIDATION_ERROR, "reasonDetail is required when reason is OTHER");
    }
    if (reasonDetail != null && reasonDetail.length() > 200) {
        throw new RunvasException(ErrorCode.VALIDATION_ERROR, "reasonDetail must be at most 200 characters");
    }

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));

    if (!user.isDeleted()) {
        user.markWithdrawn();
        userRepository.save(user);
        withdrawalFeedbackRepository.save(WithdrawalFeedback.of(reason, reasonDetail));
    }

    tokenBlacklistService.blacklist(token);
}
```

이미 `deletedAt`이 채워진 계정이면 사유 기록과 `markWithdrawn()`을 건너뛰고 토큰만 블랙리스트
처리한다(멱등 처리, 유예기간 타이머를 리셋하지 않음).

### `MeController` 수정

`GET /me`, `PATCH /me`와 같은 클래스에 추가한다.

```java
@DeleteMapping("/me")
ResponseEntity<Void> withdraw(
        @AuthenticationPrincipal RunvasPrincipal principal,
        Authentication authentication,
        @RequestBody @Valid WithdrawRequest request
) {
    if (principal == null) {
        throw new RunvasException(ErrorCode.UNAUTHORIZED);
    }
    String token = (String) authentication.getCredentials();
    accountWithdrawalService.withdraw(principal.userId(), token, request.reason(), request.reasonDetail());
    return ResponseEntity.noContent().build();
}
```

`AuthController.logout`이 이미 `authentication.getCredentials()`에서 원본 토큰 문자열을 꺼내는
동일한 패턴을 쓰고 있으므로 그대로 재사용한다.

### 로그인 시 자동 복구 — `KakaoAuthService.login`

기존 사용자를 찾은 뒤, `deletedAt`이 채워져 있으면 복구한다.

```java
Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
        AuthProvider.KAKAO, kakaoUserInfo.providerUserId());

existingUser.ifPresent(user -> {
    if (user.isDeleted()) {
        user.restore();
        userRepository.save(user);
    }
});
```

`findByProviderAndProviderUserId`는 `deleted_at` 여부와 무관하게 이미 행을 찾아오므로 쿼리
자체는 바꿀 필요가 없다. `login()`이 `@Transactional`이 아니므로 dirty checking에 기대지 않고
`userRepository.save(user)`를 명시적으로 호출해야 한다.

### 작성자 조회 실패 시 "탈퇴한 사용자" placeholder

현재 `PostService.toResponse`, `CommentService.toResponse`,
`CourseCommentService.resolveAuthor`는 작성자를 못 찾으면 `500 INTERNAL_ERROR`를 던진다
(`backend/src/main/java/com/runvas/backend/community/{PostService,CommentService,CourseCommentService}.java`).
30일 후 계정이 실제로 하드 삭제되면 이 경로가 정상적으로 발생하므로 세 곳 모두 고쳐야 한다.

`com.runvas.backend.community.dto.PublicProfile`과 `com.runvas.user.dto.PublicProfileResponse`
양쪽에 다음 정적 팩토리를 추가한다.

```java
private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

public static PublicProfile withdrawn(String authorId) {
    return new PublicProfile(authorId, WITHDRAWN_NICKNAME, null, null);
}
```

`id` 필드는 원래 `authorId`를 그대로 넣는다(별도 placeholder id를 만들지 않는다 — 클릭 대상이
안 되는 건 프론트 표시 로직의 몫이고, 여기서는 단순히 유효한 문자열을 유지하는 것으로 충분).

세 서비스는 `userRepository.findById(...).orElseThrow(INTERNAL_ERROR)` 대신
`.map(PublicProfile[Response]::from).orElseGet(() -> PublicProfile[Response].withdrawn(authorId))`
형태로 바꾼다.

### 카카오 unlink

#### 설정

`application.yml`의 `runvas.kakao`에 admin key를 추가한다.

```yaml
runvas:
  kakao:
    admin-key: ${KAKAO_ADMIN_KEY:}
```

#### `KakaoUnlinkClient` (신규 인터페이스, `com.runvas.auth.service`)

```java
public interface KakaoUnlinkClient {
    void unlink(String providerUserId);
}
```

#### `KakaoHttpUnlinkClient` (신규 구현체)

`POST https://kapi.kakao.com/v1/user/unlink`를 `Authorization: KakaoAK {adminKey}` 헤더,
form body `target_id_type=user_id&target_id={providerUserId}`로 호출한다. 실패(4xx/5xx, 타임아웃)
시 예외를 던지되, 호출부(`AccountPurgeService`)가 이를 잡아 로그만 남기고 삭제를 계속 진행한다.
`admin-key`가 비어있으면 호출 자체를 건너뛰고 경고 로그만 남긴다(로컬/테스트 환경 배려).

### 배치: 유예기간 만료 계정 하드 삭제

이 저장소에 `@Scheduled`가 처음 도입된다. `RunvasApplication`에 `@EnableScheduling`을 추가한다.

`UserRepository`에 조회 메서드를 추가한다.

```java
List<User> findByDeletedAtLessThanEqual(Instant threshold);
```

#### `AccountPurgeScheduler` (신규, `com.runvas.user.service`)

매일 새벽 한 번(`@Scheduled(cron = "0 0 4 * * *")`, KST 기준은 배포 환경 타임존 설정을 따름)
`AccountPurgeService.purgeExpiredAccounts()`를 호출한다.

#### `AccountPurgeService` (신규)

```java
@Transactional
public void purgeExpiredAccounts() {
    Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
    List<User> expired = userRepository.findByDeletedAtLessThanEqual(threshold);
    for (User user : expired) {
        purgeOne(user);
    }
}

private void purgeOne(User user) {
    if (user.getProvider() == AuthProvider.KAKAO) {
        try {
            kakaoUnlinkClient.unlink(user.getProviderUserId());
        } catch (Exception e) {
            log.warn("Kakao unlink failed for user {}, proceeding with deletion", user.getId(), e);
        }
    }
    likeRepository.deleteAllByIdUserId(user.getId().toString());
    bookmarkRepository.deleteAllByIdUserId(user.getId().toString());
    userRepository.delete(user);
}
```

`Like`/`Bookmark`는 둘 다 `@EmbeddedId`를 쓰므로(`Like.LikeId`, `Bookmark.BookmarkId`), 파생
쿼리 메서드 이름은 이 저장소에 이미 있는 `BookmarkRepository.findByIdUserIdOrderByCreatedAtDesc`와
같은 규칙으로 `deleteAllByIdUserId`를 쓴다. `LikeRepository`에는 아직 이런 메서드가 없으므로
새로 추가해야 한다. 코스/게시글/댓글/코스댓글 행은 건드리지 않는다 — `author_id`는 이제 존재하지
않는 사용자를 가리키게 되고, 위 placeholder 로직이 이를 처리한다.

## 모바일 구현

### `src/types/index.ts`

```ts
export type WithdrawalReason =
  | 'NOT_USING'
  | 'MISSING_FEATURES'
  | 'BUGS_OR_ERRORS'
  | 'PRIVACY_CONCERN'
  | 'OTHER';
```

### `src/services/authApi.ts`

```ts
export async function deleteMe(
  reason: WithdrawalReason,
  reasonDetail: string | null,
  accessToken: string,
): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }
  const response = await fetch(`${API_BASE_URL}/api/me`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ reason, reasonDetail }),
  });
  if (!isLogoutStatusAccepted(response.status)) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
```

`isLogoutStatusAccepted`(204 확인용, `src/utils/authSession.ts`)를 그대로 재사용한다 — 이름은
로그아웃 전용처럼 보이지만 "204면 성공"이라는 순수 상태코드 체크라 탈퇴에도 그대로 맞는다.

### `src/contexts/AuthContext.tsx`

- `withdraw: (reason: WithdrawalReason, reasonDetail: string | null) => Promise<void>` 추가.
- 구현은 `logout()`과 거의 동일하다: `deleteMe` 호출이 **성공한 경우에만** `SecureStore`
  (`TOKEN_KEY`, `USER_KEY`) 삭제 + `user`/`accessToken` state 초기화. 실패 시 로컬 상태 유지하고
  예외를 그대로 전파(호출부 `try/catch`).

### `WithdrawalReasonModal` (신규 컴포넌트, `src/components/`)

`PaceSelector.tsx`의 모달 구조를 참고해 만든다.

- 사유 5개를 라디오 형태 리스트로 보여준다. `기타` 선택 시 바로 아래 `TextInput`(1-200자)이
  나타나고, 비어 있으면 제출 버튼을 비활성화한다.
- 안내 문구: "탈퇴 후 30일 동안은 같은 카카오 계정으로 로그인하면 계정이 복구돼요. 30일이 지나면
  작성한 코스/게시글은 남지만 작성자는 '탈퇴한 사용자'로 표시돼요."
- 제출 버튼(`탈퇴하기`, destructive 스타일)을 누르면 곧바로 `onConfirm(reason, reasonDetail)`을
  호출한다 — 이 버튼 자체가 최종 확인이므로 별도 `Alert.alert` 확인창은 두지 않는다.
- `isSubmitting` prop으로 진행 중 버튼 비활성화(로그아웃 버튼과 동일 패턴).

### `ProfileScreen.tsx`

- 로그아웃 버튼 아래에 "회원 탈퇴" 텍스트 버튼(작고 눈에 덜 띄는 스타일)을 추가한다.
- 탭하면 `WithdrawalReasonModal`을 연다. `onConfirm`에서 `await withdraw(reason, reasonDetail)`
  호출 → 성공 시 모달 닫힘(자동, 로그인 화면으로 전환) → 실패 시 모달은 열린 채 `Alert.alert`로
  에러 안내.

## 에러 처리 / 엣지 케이스

| 상황 | 처리 |
| --- | --- |
| `reason`이 `OTHER`인데 `reasonDetail` 미전송 | `400 VALIDATION_ERROR` |
| 유예기간 중인 계정이 `DELETE /me`를 다시 호출 | 사유 재기록·타이머 연장 없이 `204` (멱등) |
| 유예기간 중 같은 카카오 계정으로 로그인 | `deletedAt` 초기화 후 정상 로그인, `isNewUser: false` |
| 유예기간 중 다른 기기의 기존 세션 | 그대로 정상 동작(탈퇴 신청에 쓰인 토큰만 블랙리스트됨) |
| 카카오 unlink 호출 실패(배치 중) | 로그만 남기고 계정 하드 삭제는 계속 진행 |
| 30일 경과 후 그 사용자가 작성한 게시글/코스댓글 조회 | 작성자가 `"탈퇴한 사용자"`, `profileImageUrl`/`bio`는 `null`로 표시 |
| 하드 삭제된 계정의 코스/게시글/댓글 자체 | 삭제되지 않고 그대로 조회/좋아요/북마크 가능 |
| DEV 프로바이더 계정 탈퇴 | 카카오 unlink 호출을 건너뜀(`provider == KAKAO`일 때만 호출) |

## MVP 제외 범위

- 탈퇴 사유 통계를 확인할 관리자 대시보드/API — 이번 범위는 `withdrawal_feedback` 테이블 적재까지만.
- 유예기간 중임을 알리는 별도 "탈퇴 예정" 배너나 이메일/푸시 알림.
- 유예기간 길이를 사용자가 즉시 확인할 수 있는 "남은 일수" 표시 API.
- 관리자에 의한 강제 탈퇴/차단.
- 다른 소셜 로그인 제공자(Apple, Google 등) 지원 — 현재 `KAKAO`/`DEV`만 존재.

## 테스트

- **백엔드**:
  - `AccountWithdrawalServiceTest`(신규): 정상 탈퇴(`deletedAt` 설정 + 토큰 블랙리스트 + 사유 저장),
    `OTHER` + `reasonDetail` 누락 시 `400`, 이미 탈퇴 신청된 계정 재호출 시 사유 재기록 없이 `204`.
  - `KakaoAuthServiceTest`: 탈퇴 유예기간 중인 사용자가 로그인하면 `deletedAt`이 `null`로
    복구되고 `isNewUser: false`인 케이스 추가.
  - `AccountPurgeServiceTest`(신규): `deletedAt`이 30일 지난 사용자만 하드 삭제, unlink 실패해도
    삭제는 진행, 좋아요/북마크 삭제 확인.
  - `PostServiceTest`/`CommentServiceTest`/`CourseCommentServiceTest`: 작성자 행이 없을 때
    `"탈퇴한 사용자"` placeholder를 반환하는 케이스 추가(기존 `500` 기대 동작이 있다면 함께 수정).
  - `MeControllerTest`: `DELETE /me` 인증 필요, 정상 케이스 204.
- **모바일**: jest 미설정 상태라 자동 테스트는 추가하지 않음. `npx tsc --noEmit` 통과 +
  실기기/시뮬레이터에서 탈퇴 사유 선택 → 탈퇴 → 로그인 화면 전환 → 같은 카카오 계정으로 재로그인 시
  정상 로그인(복구) 확인 (`mobile/CLAUDE.md` 검증 규칙).

## 검증 기준

- `DELETE /api/me` 호출 후 같은 토큰으로 `GET /api/me` 호출 시 `401 UNAUTHORIZED`.
- 탈퇴 직후 사용자가 쓴 게시글/코스는 여전히 원래 닉네임으로 조회됨(즉시 익명화되지 않음).
- 같은 카카오 계정으로 재로그인 시 새 계정이 아니라 기존 계정으로 로그인됨.
- (배치 수동 실행 또는 `deletedAt`을 과거로 설정한 테스트 데이터로 검증) 30일 경과 계정은
  하드 삭제되고, 그 사용자가 쓴 게시글의 작성자가 `"탈퇴한 사용자"`로 표시됨.
- `withdrawal_feedback` 테이블에 `user_id` 컬럼이 없고, 계정 하드 삭제와 무관하게 레코드가
  남아있음.
- `docs/api-contract.md`의 `DELETE /me` 예시와 실제 구현 동작 일치.
