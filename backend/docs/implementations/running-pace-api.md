# 달리기 페이스 API

## 배경

모바일 앱에서 사용자별 달리기 페이스를 저장하고, 경로의 예상 소요 시간 계산에 반영하기 위해
유저 프로필에 `runningPaceSecPerKm` 필드를 추가하고 `PATCH /api/me` 엔드포인트를 구현하였습니다.

## 설계 결정

### 저장 방식
- `users` 테이블에 `running_pace_sec_per_km INT NOT NULL DEFAULT 360` 컬럼을 추가합니다.
- 단위는 **초/km**. 표시 변환은 모바일 클라이언트 책임입니다.
- 기본값 360 = 6:00/km. 신규 가입 유저도 별도 설정 없이 바로 경로 예상 시간을 볼 수 있습니다.

### 계산 책임
- `distanceMeters`와 `estimatedDurationSeconds`는 기존과 동일하게 클라이언트가 계산해 전송합니다.
- 백엔드는 `runningPaceSecPerKm`을 저장하고 반환할 뿐, 이를 이용해 코스의 예상 시간을 재계산하지 않습니다.
- 이 결정은 `docs/api-contract.md`의 기존 원칙(백엔드는 클라이언트 전송값 저장)을 유지합니다.

### PATCH /api/me 부분 업데이트
- 요청 본문의 필드가 `null`이면 해당 필드를 수정하지 않습니다 (누락 = 변경 없음).
- `bio`를 실제 `null`로 초기화하려면 빈 문자열(`""`)을 전송합니다 (현재 MVP 범위).

## 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `db/migration/V3__add_running_pace_to_users.sql` | 신규 — `running_pace_sec_per_km` 컬럼 추가, DEFAULT 360 |
| `user/domain/User.java` | `runningPaceSecPerKm` 필드, getter, `updateProfile()` 메서드 추가 |
| `user/dto/UserResponse.java` | `runningPaceSecPerKm` 필드 추가 |
| `user/dto/UpdateMeRequest.java` | 신규 — `nickname`, `profileImageUrl`, `bio`, `runningPaceSecPerKm` (모두 선택) |
| `user/controller/MeController.java` | `PATCH /api/me` 엔드포인트 추가 |

## API

### PATCH /api/me

```
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "runningPaceSecPerKm": 300
}
```

**응답**: GET /api/me와 동일한 `{ user: { ..., runningPaceSecPerKm: 300 } }`

**유효성 검사**
- `nickname`: 2-30자
- `bio`: 0-160자
- `runningPaceSecPerKm`: 120-900 (2:00/km ~ 15:00/km)

## 검증

- `./gradlew build` 로 컴파일 및 기존 테스트 통과 확인
- `GET /api/me` 응답에 `runningPaceSecPerKm` 포함 확인
- `PATCH /api/me { "runningPaceSecPerKm": 300 }` 후 재조회 시 값 반영 확인
- 범위 초과 값(`"runningPaceSecPerKm": 1000`) 전송 시 `400 VALIDATION_ERROR` 반환 확인
