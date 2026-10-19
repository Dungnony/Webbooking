/**
 * Mirrors UserService.validatePasswordStrength on the backend: 8-72 characters, no spaces, at least one
 * uppercase letter, one lowercase letter, one digit and one other character.
 */
export const STRONG_PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d\s])\S{8,72}$/;
