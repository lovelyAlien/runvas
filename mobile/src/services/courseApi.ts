// 코스 저장(POST /api/courses) — runvas/backend의 CourseController와 연동됨
// (MapScreen.tsx의 handleConfirmSave에서 accessToken과 함께 호출).
import {
  Course,
  CourseSummary,
  CreateCourseRequestBody,
  UpdateCourseRequest,
  GeoBounds,
  RoutePoint,
  CourseVisibility,
} from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

interface BuildCreateCourseRequestParams {
  title: string;
  description?: string | null;
  path: RoutePoint[];
  waypoints: RoutePoint[];
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
    waypoints: params.waypoints,
    distanceMeters: params.distanceMeters,
    estimatedDurationSeconds: params.estimatedDurationSeconds,
    bounds: params.bounds,
    visibility: params.visibility ?? 'PRIVATE',
    tags: params.tags ?? [],
  };
}

export async function postCourse(body: CreateCourseRequestBody, accessToken: string): Promise<Course> {
  if (!API_BASE_URL) {
    throw new Error(
      'EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다. backend가 준비되면 .env에 값을 채워주세요.'
    );
  }

  const response = await fetch(`${API_BASE_URL}/api/courses`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { course } = (await response.json()) as { course: Course };
  return course;
}

export async function getMyCourses(accessToken: string): Promise<CourseSummary[]> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/mine`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { courses } = (await response.json()) as { courses: CourseSummary[] };
  return courses;
}

export async function getCourse(courseId: string, accessToken?: string): Promise<Course> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { course } = (await response.json()) as { course: Course };
  return course;
}

export async function deleteCourse(courseId: string, accessToken: string): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }
}

export async function patchCourse(
  courseId: string,
  body: UpdateCourseRequest,
  accessToken: string
): Promise<Course> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/courses/${courseId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  const { course } = (await response.json()) as { course: Course };
  return course;
}
