// 위도/경도 한 점. UI 표시·지도 좌표·GeoBounds 모서리 등 sequence가 필요 없는 모든 곳에서 사용합니다.
export interface Coordinate {
  latitude: number;
  longitude: number;
}

// 저장/전송용 경로 포인트. docs/data-model.md RoutePoint와 1:1 대응 — sequence는 0부터 연속이어야 합니다.
export interface RoutePoint extends Coordinate {
  sequence: number;
}

export interface GeoBounds {
  southWest: Coordinate;
  northEast: Coordinate;
}

export type CourseVisibility = 'PUBLIC' | 'PRIVATE';

// docs/data-model.md Course와 1:1 대응 (서버가 채우는 필드 포함, 모바일에서 생성하지 않음)
export interface Course {
  id: string;
  authorId: string;
  title: string;
  description: string | null;
  path: RoutePoint[];
  waypoints: RoutePoint[];
  distanceMeters: number;
  estimatedDurationSeconds: number;
  bounds: GeoBounds;
  startAddress: string | null;
  visibility: CourseVisibility;
  tags: string[];
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
  updatedAt: string;
}

// 화면에 표시할 현재 경로 통계. 시간은 항상 seconds 단위로 보관합니다 (docs/geo-conventions.md).
export interface RouteStats {
  distanceMeters: number;
  estimatedDurationSeconds: number;
  pointCount: number;
}

// docs/api-contract.md POST /api/courses 요청 본문과 1:1 대응.
export interface CreateCourseRequestBody {
  title: string;
  description: string | null;
  path: RoutePoint[];
  waypoints: RoutePoint[];
  distanceMeters: number;
  estimatedDurationSeconds: number;
  bounds: GeoBounds;
  visibility: CourseVisibility;
  tags: string[];
}

// docs/data-model.md User와 1:1 대응. providerUserId는 API 응답에 절대 노출되지 않는 내부
// 저장값이라 모바일 타입에는 포함하지 않는다.
export interface User {
  id: string;
  email: string | null;
  provider: 'KAKAO';
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
  runningPaceSecPerKm: number;
  createdAt: string;
  updatedAt: string;
}

// UI 전용 — 페이스 옵션 선택에서만 사용. docs/api-contract.md 제한값: 120-900초/km.
export type RunningPace = 240 | 270 | 300 | 330 | 360 | 390 | 420 | 480;

export interface PaceOption {
  paceSecPerKm: RunningPace;
  label: string;
}

// 대표 페이스 프리셋
export type PacePreset = '초보' | '중수' | '고수';

// docs/api-contract.md POST /auth/kakao 응답과 동일한 모양 (DevAuthController도 같은 모양으로 응답).
export interface AuthResponse {
  accessToken: string;
  user: User;
  isNewUser: boolean;
}

// docs/api-contract.md GET /me, PATCH /me 응답 형식.
export interface MeResponse {
  user: User;
}

// docs/api-contract.md PATCH /courses/{courseId} 요청 본문 (메타데이터만, 모든 필드 선택).
export interface UpdateCourseRequest {
  title?: string;
  description?: string | null;
  visibility?: CourseVisibility;
  tags?: string[];
}

// docs/api-contract.md PATCH /me 요청 본문 (모든 필드 선택).
export interface UpdateMeRequest {
  nickname?: string;
  profileImageUrl?: string | null;
  bio?: string | null;
  runningPaceSecPerKm?: number;
}

// docs/api-contract.md 공통 에러 응답 형식.
export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    details?: Array<{ field: string; message: string }>;
  };
}

// docs/api-contract.md GET /courses, GET /courses/mine 목록 응답과 1:1 대응. path는 빠진다
// (목록에서는 안 내려주고, 상세 화면 진입 시 GET /courses/{courseId}로 따로 조회한다).
export interface CourseSummary {
  id: string;
  authorId: string;
  title: string;
  description: string | null;
  distanceMeters: number;
  estimatedDurationSeconds: number;
  bounds: GeoBounds;
  startAddress: string | null;
  visibility: CourseVisibility;
  tags: string[];
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
  updatedAt: string;
}
