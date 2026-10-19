import { LANGUAGE_STORAGE_KEY } from '../i18n';

interface JwtPayload {
  sub?: string;
  role?: string;
}

/** Fired on login and logout so long-lived providers (wishlist) can switch between guest and account data. */
export const AUTH_CHANGED_EVENT = 'auth-changed';

function decodeJwtPayload(token: string): JwtPayload {
  const base64Url = token.split('.')[1];
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  const json = decodeURIComponent(
    window.atob(base64)
      .split('')
      .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
      .join(''),
  );
  return JSON.parse(json);
}

/**
 * Stores the session after a successful login and returns the user's role.
 * The token is not verified here; the backend verifies it on every request.
 */
export function saveSession(token: string, fallbackUsername: string): string {
  const payload = decodeJwtPayload(token);
  const role = payload.role || 'USER';
  localStorage.setItem('token', token);
  localStorage.setItem('username', payload.sub || fallbackUsername);
  localStorage.setItem('role', role);
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
  return role;
}

export function clearSession(): void {
  // Logging out should not reset the visitor's language choice.
  const language = localStorage.getItem(LANGUAGE_STORAGE_KEY);
  localStorage.clear();
  if (language) localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}
