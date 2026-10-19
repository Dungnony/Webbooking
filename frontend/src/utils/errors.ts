import { isAxiosError } from 'axios';

interface ErrorBody {
  message?: unknown;
  code?: unknown;
}

/**
 * The backend answers errors with `{ message, code }`. Requests made with `format: 'text'` receive that JSON
 * unparsed, so a string body is parsed when it is a JSON object and otherwise treated as a plain-text message.
 */
function readErrorBody(error: unknown): ErrorBody | string | undefined {
  if (!isAxiosError(error)) return undefined;
  const data: unknown = error.response?.data;
  if (typeof data === 'string') {
    const text = data.trim();
    if (text.startsWith('{')) {
      try {
        return JSON.parse(text) as ErrorBody;
      } catch {
        // Not JSON after all: fall through and use it as plain text.
      }
    }
    return text;
  }
  return data && typeof data === 'object' ? (data as ErrorBody) : undefined;
}

/** Extracts a human-readable message from anything thrown by an API call. */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError(error)) {
    const body = readErrorBody(error);
    if (typeof body === 'string') return body !== '' ? body : fallback;
    const message = body?.message;
    return typeof message === 'string' && message.trim() !== '' ? message : fallback;
  }
  if (error instanceof Error && error.message.trim() !== '') return error.message;
  return fallback;
}

/** Stable error code from a `{ message, code }` body (e.g. `error.email.exists`), if the backend sent one. */
export function getErrorCode(error: unknown): string | undefined {
  const body = readErrorBody(error);
  if (body === undefined || typeof body === 'string') return undefined;
  return typeof body.code === 'string' ? body.code : undefined;
}

/** HTTP status of a failed API call, or undefined when the server was unreachable. */
export function getErrorStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}
