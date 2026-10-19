import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { getErrorCode, getErrorMessage, getErrorStatus } from './errors';

const FALLBACK = 'fallback';

const httpError = (status: number, data: unknown) =>
  new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data,
  } as AxiosResponse);

describe('getErrorMessage', () => {
  it('uses the translated message of a JSON error body', () => {
    const error = httpError(400, { message: 'Email đã được dùng', code: 'error.email.exists' });

    expect(getErrorMessage(error, FALLBACK)).toBe('Email đã được dùng');
  });

  it('parses the JSON body that text-format requests receive as a string', () => {
    const error = httpError(401, '{"message":"Wrong username or password.","code":"error.login.invalidCredentials"}');

    expect(getErrorMessage(error, FALLBACK)).toBe('Wrong username or password.');
  });

  it('keeps a plain-text body as the message', () => {
    expect(getErrorMessage(httpError(400, 'Something went wrong'), FALLBACK)).toBe('Something went wrong');
  });

  it('falls back when the body has no usable message', () => {
    expect(getErrorMessage(httpError(500, ''), FALLBACK)).toBe(FALLBACK);
    expect(getErrorMessage(httpError(500, { message: '   ' }), FALLBACK)).toBe(FALLBACK);
    expect(getErrorMessage(httpError(500, '{not json'), FALLBACK)).toBe('{not json');
  });

  it('falls back when the server could not be reached', () => {
    const networkError = new AxiosError('Network Error', 'ERR_NETWORK');

    expect(getErrorMessage(networkError, FALLBACK)).toBe(FALLBACK);
    expect(getErrorStatus(networkError)).toBeUndefined();
  });

  it('uses the message of a non-HTTP error', () => {
    expect(getErrorMessage(new Error('boom'), FALLBACK)).toBe('boom');
    expect(getErrorMessage('not an error', FALLBACK)).toBe(FALLBACK);
  });
});

describe('getErrorCode', () => {
  it('reads the code from object and string JSON bodies', () => {
    expect(getErrorCode(httpError(400, { message: 'x', code: 'error.otp.invalid' }))).toBe('error.otp.invalid');
    expect(getErrorCode(httpError(400, '{"message":"x","code":"error.otp.invalid"}'))).toBe('error.otp.invalid');
  });

  it('is undefined when there is no code', () => {
    expect(getErrorCode(httpError(400, 'plain text'))).toBeUndefined();
    expect(getErrorCode(httpError(400, { message: 'x' }))).toBeUndefined();
    expect(getErrorCode(new Error('boom'))).toBeUndefined();
  });
});

describe('getErrorStatus', () => {
  it('returns the HTTP status of a failed call', () => {
    expect(getErrorStatus(httpError(429, {}))).toBe(429);
  });
});
