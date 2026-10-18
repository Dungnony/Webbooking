interface JwtPayload {
  sub?: string;
  role?: string;
}

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
  return role;
}
