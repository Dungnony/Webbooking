import { isAxiosError } from 'axios';

/**
 * Extracts a human-readable message from anything thrown by an API call.
 * The backend replies either with a plain-text body or with `{ message }`.
 */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError(error)) {
    const data: unknown = error.response?.data;
    if (typeof data === 'string' && data.trim() !== '') return data;
    if (data && typeof data === 'object' && 'message' in data) {
      const { message } = data as { message?: unknown };
      if (typeof message === 'string' && message.trim() !== '') return message;
    }
    return fallback;
  }
  if (error instanceof Error && error.message.trim() !== '') return error.message;
  return fallback;
}

/** HTTP status of a failed API call, or undefined when the server was unreachable. */
export function getErrorStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}
