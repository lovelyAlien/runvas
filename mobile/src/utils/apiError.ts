import { ApiErrorBody } from '../types';

// 응답이 docs/api-contract.md 형식({"error":{"code","message"}})이 아닐 수도 있다 (예: Spring
// Security가 필터 단계에서 막아 기본 에러 바디를 내려주는 경우). optional chaining으로 방어해서
// 항상 사람이 읽을 수 있는 메시지를 만든다.
export async function parseApiErrorMessage(response: Response): Promise<string> {
  const body = await response.json().catch(() => null);
  const errorBody = body as Partial<ApiErrorBody> | null;
  const code = errorBody?.error?.code;
  const message = errorBody?.error?.message;

  if (code && message) {
    return `${code}: ${message}`;
  }
  return `UNKNOWN_ERROR: ${response.statusText || `HTTP ${response.status}`}`;
}
