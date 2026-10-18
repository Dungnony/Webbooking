package com.Nhom2.booking.exception;

/** Mapped to HTTP 429 so clients can tell a temporary lockout apart from wrong credentials. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
