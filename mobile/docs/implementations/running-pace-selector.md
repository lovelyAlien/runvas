# 달리기 페이스 선택 기능

## 배경

경로를 그릴 때 예상 소요 시간이 하드코딩된 6분/km 기준으로만 계산되었습니다.
사용자별 실제 달리기 속도를 반영해 경로의 예상 시간과 저장된 코스 목록의 예상 시간을 정확하게 표시하기 위해 구현하였습니다.

## 설계 결정

### 페이스 저장 위치
- 페이스는 유저 계정에 귀속되는 설정값이므로 백엔드 `users` 테이블에 `running_pace_sec_per_km` 컬럼으로 저장합니다.
- 기본값은 360초/km (6:00/km)이며, 모바일에서 `PATCH /api/me`로 변경합니다.

### 단위 선택
- 상태는 항상 **초/km(seconds per km)** 단위로 보관합니다 (docs/geo-conventions.md의 시간 단위 규칙 준수).
- 화면 표시 시에만 `formatPace()`로 `M:SS/km` 형태로 변환합니다.

### 예상 시간 계산
- `estimatedDurationSeconds = Math.round((distanceMeters / 1000) * paceSecPerKm)`
- `useRoute` 훅이 `paceSecPerKm` 파라미터를 받아 `useMemo` 내에서 재계산합니다.
- SavedRoutes 목록에서는 서버 저장값 대신 현재 유저 페이스 × 거리로 클라이언트에서 재계산합니다.

### UI 구조
- **ProfileScreen**: 페이스 설정 진입점. 닉네임 아래 탭 가능한 항목으로 현재 페이스 표시.
- **PaceSelector 모달**: 초보/중수/고수 프리셋 버튼 + 직접 입력(MM:SS 형식).
- **MapScreen RouteStatsBar**: 현재 적용 페이스 표시(읽기 전용).

## 추가/변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `src/types/index.ts` | `User`에 `runningPaceSecPerKm` 추가, `RunningPace` / `PaceOption` / `MeResponse` / `UpdateMeRequest` 타입 추가 |
| `src/utils/format.ts` | `formatPace(secondsPerKm): string` 추가 (`360` → `"6:00/km"`) |
| `src/services/authApi.ts` | `patchMe(body, token)` 함수 추가 |
| `src/contexts/AuthContext.tsx` | `updateUser(updatedUser)` 함수 추가 — 상태와 SecureStore를 동시에 갱신 |
| `src/hooks/useRoute.ts` | `DEFAULT_PACE_SEC_PER_KM = 360` export, `paceSecPerKm` 파라미터 수용, 계산식 수정 |
| `src/components/PaceSelector.tsx` | 신규 — 프리셋(초보 480/중수 360/고수 270 초/km) + 직접 입력 모달 |
| `src/components/RouteStatsBar.tsx` | `selectedPaceLabel?` / `onPacePress?` props 추가, 페이스 표시 항목 조건부 렌더링 |
| `src/screens/MapScreen.tsx` | `useRoute(selectedPace)` 호출, 페이스 라벨 표시 (탭 기능 없음) |
| `src/screens/ProfileScreen.tsx` | 페이스 설정 항목 추가, PaceSelector 연결 |
| `src/screens/SavedRoutesScreen.tsx` | 유저 페이스 × 거리로 예상 시간 재계산 |

## 페이스 프리셋

| 레벨 | 페이스 | 초/km |
|------|--------|--------|
| 초보 | 8:00/km | 480 |
| 중수 | 6:00/km | 360 |
| 고수 | 4:30/km | 270 |

직접 입력 범위: 2:00/km(120초) ~ 15:00/km(900초)

## 검증

- `npx tsc --noEmit` 통과 (타입 오류 없음)
- 직접 입력 파싱: MM:SS 형식 검증, 범위 초과 시 오류 메시지 표시
- ProfileScreen에서 페이스 변경 → MapScreen 예상 시간 실시간 반영 확인
- SavedRoutes 목록에서 페이스 변경 후 예상 시간 변경 확인
