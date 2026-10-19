package com.Nhom2.booking.exception;

/** Mapped to HTTP 503: a dependency (file storage, mail) failed, so retrying later may succeed. */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String code, Throwable cause) {
        super(code, cause);
    }
}
