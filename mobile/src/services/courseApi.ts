// 코스 저장(POST /api/courses) 연동 준비 — 오늘은 백엔드(runvas/backend)가 README만 있는
// 상태라 이 모듈은 어디서도 호출하지 않습니다. 백엔드가 준비되면 다음 체크리스트만 따르면 됩니다.
//
// 1. App.tsx에 "저장" 버튼을 추가하고, useRoute().toRoutePoints() / getBounds() / stats를
//    buildCreateCourseRequest()에 넘겨 본문을 만든 뒤 postCourse()를 호출하세요.
// 2. 인증: 아직 로그인 기능이 없습니다. 로그인 구현 후 발급받은 accessToken을
//    `Authorization: Bearer <accessToken>` 헤더로 추가해야 합니다 (현재는 미적용).
// 3. 에러 처리: 응답이 실패하면 ApiErrorBody.error.code를 docs/api-contract.md의
//    VALIDATION_ERROR / UNAUTHORIZED 등 에러 코드 표와 매핑해 사용자 메시지를 보여주세요.
import {
  Course,
  CreateCourseRequestBody,
  GeoBounds,
  ApiErrorBody,
  RoutePoint,
  CourseVisibility,
} from '../types';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

interface BuildCreateCourseRequestParams {
  title: string;
  description?: string | null;
  path: RoutePoint[];
  distanceMeters: number;
  estimatedDurationSeconds: number;
  bounds: GeoBounds;
  visibility?: CourseVisibility;
  tags?: string[];
}

// 네트워크 호출 없는 순수 변환 함수 — 오늘도 바로 사용/테스트 가능합니다.
export function buildCreateCourseRequest(
  params: BuildCreateCourseRequestParams
): CreateCourseRequestBody {
  return {
    title: params.title,
    description: params.description ?? null,
    path: params.path,
    distanceMeters: params.distanceMeters,
    estimatedDurationSeconds: params.estimatedDurationSeconds,
    bounds: params.bounds,
    visibility: params.visibility ?? 'PRIVATE',
    tags: params.tags ?? [],
  };
}

export async function postCourse(body: CreateCourseRequestBody): Promise<Course> {
  if (!API_BASE_URL) {
    throw new Error(
      'EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다. backend가 준비되면 .env에 값을 채워주세요.'
    );
  }

  const response = await fetch(`${API_BASE_URL}/api/courses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const errorBody: ApiErrorBody = await response.json().catch(() => ({
      error: { code: 'UNKNOWN_ERROR', message: response.statusText },
    }));
    throw new Error(`${errorBody.error.code}: ${errorBody.error.message}`);
  }

  const { course } = (await response.json()) as { course: Course };
  return course;
}
