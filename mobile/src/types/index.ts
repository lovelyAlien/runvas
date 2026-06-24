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
  distanceMeters: number;
  estimatedDurationSeconds: number;
  bounds: GeoBounds;
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
  distanceMeters: number;
  estimatedDurationSeconds: number;
  bounds: GeoBounds;
  visibility: CourseVisibility;
  tags: string[];
}

// docs/api-contract.md 공통 에러 응답 형식.
export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    details?: Array<{ field: string; message: string }>;
  };
}
