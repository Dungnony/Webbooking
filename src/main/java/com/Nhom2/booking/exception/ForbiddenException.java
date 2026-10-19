package com.Nhom2.booking.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String code, Object... args) {
        super(code, args);
    }
}
