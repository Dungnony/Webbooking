package com.Nhom2.booking.exception;

/** Mapped to HTTP 401: the caller could not be authenticated (wrong credentials, bad Google token). */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String code) {
        super(code);
    }

    public UnauthorizedException(String code, Throwable cause) {
        super(code, cause);
    }
}
