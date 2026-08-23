import { ReportReason, ReportTargetType } from '../types';
import { parseApiErrorMessage } from '../utils/apiError';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? '';

export async function postReport(
  targetType: ReportTargetType,
  targetId: string,
  reason: ReportReason,
  reasonDetail: string | null,
  accessToken: string
): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL이 설정되지 않았습니다.');
  }

  const response = await fetch(`${API_BASE_URL}/api/reports/${targetType}/${targetId}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ reason, reasonDetail }),
  });

  if (response.status !== 201 && response.status !== 200) {
    throw new Error(await parseApiErrorMessage(response));
  }
}
