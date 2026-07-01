export function isJwtExpired(token: string): boolean {
  const [, payload] = token.split('.');
  if (!payload) return true;

  try {
    const claims = JSON.parse(decodeBase64Url(payload)) as { exp?: number };
    if (!claims.exp) return true;

    return claims.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

export function shouldRestoreStoredSession(token: string | null, user: string | null): boolean {
  return Boolean(token && user && !isJwtExpired(token));
}

export function isLogoutStatusAccepted(status: number): boolean {
  return status === 204 || status === 401;
}

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');

  if (typeof globalThis.atob !== 'function') {
    throw new Error('Base64 decoder is unavailable');
  }

  return globalThis.atob(padded);
}
