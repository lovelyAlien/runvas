import { AuthResponse } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

// 백엔드 DevAuthController(POST /auth/dev-login) 호출 — docs/api-contract.md 계약이 아니라
// 카카오 SDK 연동 전까지 실제 JWT를 받기 위한 임시 경로. AuthContext.mockLogin이 이 함수를
// 호출한다. 카카오 로그인이 준비되면 이 파일과 mockLogin 호출부를 함께 제거한다.
export async function devLogin(nickname?: string): Promise<AuthResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/dev-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as AuthResponse;
}
