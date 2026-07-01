import { AuthResponse } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';
import { isLogoutStatusAccepted } from '../utils/authSession';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function postAuthKakao(
  authorizationCode: string,
  redirectUri: string,
): Promise<AuthResponse> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/kakao`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider: 'KAKAO',
      authorizationCode,
      redirectUri,
    }),
  });

  if (!response.ok) {
    throw new Error(await parseApiErrorMessage(response));
  }

  return (await response.json()) as AuthResponse;
}

export async function postAuthLogout(accessToken: string): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (!isLogoutStatusAccepted(response.status)) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
